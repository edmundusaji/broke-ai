package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.AiExpenseResponse;
import org.edmund.brokeai.dto.CategorySummaryDTO;
import org.edmund.brokeai.dto.ExpenseSummaryResponse;
import org.edmund.brokeai.dto.ExpenseRequest;
import org.edmund.brokeai.dto.NotificationIngestionRequest;
import org.edmund.brokeai.dto.NotificationIngestionResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.entity.UserDevice;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.NotificationIdempotencyRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserDeviceRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.exception.GuestAiTrialLimitException;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.ExpenseService;
import org.edmund.brokeai.service.UserSyncService;
import org.edmund.brokeai.service.GeminiService;
import org.edmund.brokeai.service.RateLimitingService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ExpenseServiceImpl implements ExpenseService {

    private static final int MAX_NOTIFICATION_LENGTH = 4000;
    private static final Duration MAX_CAPTURE_AGE = Duration.ofDays(7);
    private static final Duration MAX_FUTURE_SKEW = Duration.ofMinutes(5);
    private static final Set<String> SUPPORTED_SOURCE_PACKAGES = Set.of(
        "com.gojek.app",
        "com.gojek.gopay",
        "ovo.id",
        "id.dana",
        "com.bca",
        "com.bca.mybca.omni.android",
        "src.com.bni",
        "id.bni.wondr",
        "id.co.bri.brimo"
    );

    private final GeminiService geminiService;
    private final TransactionRepository transactionRepository;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final UserSyncService userSyncService;
    private final UserDeviceRepository userDeviceRepository;
    private final NotificationIdempotencyRepository notificationIdempotencyRepository;
    private final RateLimitingService rateLimitingService;

    @Value("${app.notification-capture.enabled:false}")
    private boolean notificationCaptureEnabled;

    @Value("${app.notification-capture.confidence-threshold:0.80}")
    private double notificationConfidenceThreshold;

    @Override
    public Transaction saveReceipt(MultipartFile file) {
        AppUser currentUser = currentUserService.getCurrentUser();
        return executeAiOperation(currentUser, () -> {
            AiExpenseResponse aiResponse = geminiService.receiptProcess(file);

            if (aiResponse == null || aiResponse.getAmount() == null) {
                throw new RuntimeException("Failed to process receipt");
            }

            Transaction transaction = mapToEntity(aiResponse, "RECEIPT", currentUser);
            Transaction saved = transactionRepository.save(transaction);
            userSyncService.markChanged(currentUser.getId());
            return saved;
        });
    }

    @Override
    public Transaction saveNotification(String notification) {
        AppUser currentUser = currentUserService.getCurrentUser();
        return executeAiOperation(currentUser, () -> {
            AiExpenseResponse aiResponse = geminiService.processNotification(notification);
            NotificationDecision decision = decideNotification(aiResponse);
            if (decision == NotificationDecision.IGNORED) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTIFICATION_IGNORED",
                    "The submitted notification is not an expense.");
            }
            if (decision == NotificationDecision.NEEDS_REVIEW) {
                throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "NOTIFICATION_NEEDS_REVIEW",
                    "The submitted notification could not be classified with enough confidence.");
            }
            Transaction transaction = mapToEntity(aiResponse, "NOTIFICATION", currentUser);
            transaction.setCaptureMode("PASTED");
            transaction.setSourcePayloadHash(ServiceSupport.sha256(normalizeNotification(notification)));
            Transaction saved = transactionRepository.save(transaction);
            userSyncService.markChanged(currentUser.getId());
            return saved;
        });
    }

    @Override
    @Transactional
    public NotificationIngestionResponse saveAutomaticNotification(
        NotificationIngestionRequest request,
        UUID deviceId
    ) {
        requireNotificationCaptureEnabled();
        validateAutomaticRequest(request, deviceId);

        AppUser user = currentUserService.getCurrentUser();
        UserDevice device = userDeviceRepository.findByIdAndUserId(deviceId, user.getId())
            .filter(value -> "android".equals(value.getPlatform()))
            .filter(value -> value.getNotificationCaptureTokenHash() != null)
            .filter(value -> value.getNotificationCaptureRevokedAt() == null)
            .orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "CAPTURE_CREDENTIAL_INVALID",
                "Open Broke.AI to reconnect notification capture."
            ));

        Instant now = Instant.now();
        device.setNotificationCaptureLastUsedAt(now);
        device.setLastSeenAt(now);
        device.setUpdatedAt(now);

        String normalizedText = normalizeNotification(request.text());
        String payloadHash = ServiceSupport.sha256(normalizedText);
        String requestHash = ServiceSupport.sha256(
            request.sourcePackage() + "|" + request.notificationPostedAt() + "|" + payloadHash
        );

        Optional<Transaction> existing = transactionRepository
            .findByUserIdAndCaptureIdAndDeletedAtIsNull(user.getId(), request.captureId());
        if (existing.isPresent()) {
            return new NotificationIngestionResponse(
                NotificationIngestionResponse.Status.DUPLICATE, existing.get(), request.captureId()
            );
        }

        boolean reserved = notificationIdempotencyRepository.reserve(
            user.getId(), request.captureId(), requestHash, now.plus(MAX_CAPTURE_AGE)
        );
        if (!reserved) {
            notificationIdempotencyRepository.findRequestHash(user.getId(), request.captureId())
                .filter(previousHash -> !previousHash.equals(requestHash))
                .ifPresent(ignored -> {
                    throw new ApiException(HttpStatus.CONFLICT, "CAPTURE_ID_REUSED",
                        "The captureId was already used for a different payload.", "captureId");
                });
            Transaction duplicate = transactionRepository
                .findByUserIdAndCaptureIdAndDeletedAtIsNull(user.getId(), request.captureId())
                .orElse(null);
            return new NotificationIngestionResponse(
                NotificationIngestionResponse.Status.DUPLICATE, duplicate, request.captureId()
            );
        }

        if (!rateLimitingService.tryConsumeAutomatic(user.getId())) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "CAPTURE_RATE_LIMITED",
                "Automatic notification capture is temporarily rate limited.");
        }

        AiExpenseResponse aiResponse = geminiService.processNotification(normalizedText);
        NotificationDecision decision = decideNotification(aiResponse);
        if (decision != NotificationDecision.SAVED) {
            NotificationIngestionResponse.Status status = decision == NotificationDecision.IGNORED
                ? NotificationIngestionResponse.Status.IGNORED
                : NotificationIngestionResponse.Status.NEEDS_REVIEW;
            notificationIdempotencyRepository.complete(
                user.getId(), request.captureId(), HttpStatus.OK.value(), terminalResponseJson(status, request.captureId(), null)
            );
            userDeviceRepository.save(device);
            return new NotificationIngestionResponse(status, null, request.captureId());
        }

        Transaction transaction = mapToEntity(aiResponse, "NOTIFICATION", user);
        transaction.setCaptureId(request.captureId());
        transaction.setCaptureMode("AUTOMATIC");
        transaction.setCaptureDevice(device);
        transaction.setSourcePackage(request.sourcePackage());
        transaction.setSourceNotificationPostedAt(request.notificationPostedAt());
        transaction.setSourcePayloadHash(payloadHash);
        Transaction saved = transactionRepository.saveAndFlush(transaction);
        userDeviceRepository.save(device);
        notificationIdempotencyRepository.complete(
            user.getId(), request.captureId(), HttpStatus.OK.value(),
            terminalResponseJson(NotificationIngestionResponse.Status.SAVED, request.captureId(), saved.getId())
        );
        userSyncService.markChanged(user.getId());
        return new NotificationIngestionResponse(
            NotificationIngestionResponse.Status.SAVED, saved, request.captureId()
        );
    }

    @Override
    @Transactional
    public Transaction createManualExpense(ExpenseRequest request) {
        validateExpenseRequest(request);

        Transaction transaction = new Transaction();
        applyExpenseDetails(transaction, request);
        transaction.setInputType("MANUAL");
        transaction.setValidationStatus("CONFIRMED");
        transaction.setUser(currentUserService.getCurrentUser());
        Transaction saved = transactionRepository.save(transaction);
        userSyncService.markChanged(saved.getUser().getId());
        return saved;
    }

    @Override
    @Transactional
    public Transaction updateExpense(Long id, ExpenseRequest request) {
        validateExpenseRequest(request);

        AppUser currentUser = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, currentUser)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        applyExpenseDetails(transaction, request);
        transaction.setUpdatedAt(java.time.Instant.now());
        Transaction saved = transactionRepository.save(transaction);
        userSyncService.markChanged(currentUser.getId());
        return saved;
    }

    @Override
    @Transactional
    public void deleteExpense(Long id) {
        AppUser currentUser = currentUserService.getCurrentUser();
        Transaction transaction = transactionRepository.findByIdAndUser(id, currentUser)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
        java.time.Instant now = java.time.Instant.now();
        transaction.setDeletedAt(now);
        transaction.setUpdatedAt(now);
        transactionRepository.save(transaction);
        userSyncService.markChanged(currentUser.getId());
    }

    @Override
    public ExpenseSummaryResponse getExpenseSummary(int month, int year) {
        AppUser currentUser = currentUserService.getCurrentUser();
        DateRange dateRange = buildMonthDateRange(month, year);

        List<CategorySummaryDTO> breakdown = transactionRepository.getExpenseSummaryByUserAndDateRange(
            currentUser,
            dateRange.startDate(),
            dateRange.endDate()
        );

        Double total = breakdown.stream()
            .mapToDouble(CategorySummaryDTO::totalAmount)
            .sum();

        return new ExpenseSummaryResponse(total, breakdown);
    }

    @Override
    public List<Transaction> getExpenseHistory(int month, int year) {
        AppUser currentUser = currentUserService.getCurrentUser();
        DateRange dateRange = buildMonthDateRange(month, year);

        return transactionRepository.findByUserAndDateBetweenOrderByDateDesc(
            currentUser,
            dateRange.startDate(),
            dateRange.endDate()
        );
    }

    @Override
    public List<Transaction> getRecentExpenses() {
        AppUser currentUser = currentUserService.getCurrentUser();
        return transactionRepository.findTop5ByUserOrderByDateDesc(currentUser);
    }

    private Transaction mapToEntity(AiExpenseResponse aiResponse, String inputType, AppUser user) {
        Transaction transaction = new Transaction();

        transaction.setAmount(aiResponse.getAmount());
        transaction.setCategory(aiResponse.getCategory());
        transaction.setPaymentMethod(aiResponse.getPaymentMethod());
        transaction.setDescription(aiResponse.getDescription());
        transaction.setInputType(inputType);
        transaction.setValidationStatus("PENDING");
        transaction.setUser(user);
        transaction.setDate(parseDateAndTime(aiResponse.getDate(), aiResponse.getTime()));

        return transaction;
    }

    private void applyExpenseDetails(Transaction transaction, ExpenseRequest request) {
        LocalTime transactionTime = transaction.getDate() == null
            ? LocalTime.now()
            : transaction.getDate().toLocalTime();
        transaction.setDate(LocalDateTime.of(request.date(), transactionTime));
        transaction.setAmount(request.amount());
        transaction.setCategory(request.category().trim());
        transaction.setPaymentMethod(request.paymentMethod().trim());
        transaction.setDescription(request.description().trim());
    }

    private void validateExpenseRequest(ExpenseRequest request) {
        if (request == null || request.date() == null || request.amount() == null
            || request.amount() <= 0 || isBlank(request.category())
            || isBlank(request.paymentMethod()) || isBlank(request.description())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Date, positive amount, category, payment method, and description are required"
            );
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private NotificationDecision decideNotification(AiExpenseResponse response) {
        if (response == null || response.getIsExpense() == null) return NotificationDecision.NEEDS_REVIEW;
        if (!response.getIsExpense()) return NotificationDecision.IGNORED;
        Double confidence = response.getConfidence();
        if (confidence == null || !Double.isFinite(confidence) || confidence < 0 || confidence > 1
            || confidence < notificationConfidenceThreshold) {
            return NotificationDecision.NEEDS_REVIEW;
        }
        if (response.getAmount() == null || !Double.isFinite(response.getAmount()) || response.getAmount() <= 0
            || isBlank(response.getCategory()) || isBlank(response.getPaymentMethod())
            || isBlank(response.getDescription()) || parseDateAndTimeStrict(response.getDate(), response.getTime()).isEmpty()) {
            return NotificationDecision.NEEDS_REVIEW;
        }
        if (response.getCategory().length() > 255 || response.getPaymentMethod().length() > 255
            || response.getDescription().length() > 255) {
            return NotificationDecision.NEEDS_REVIEW;
        }
        return NotificationDecision.SAVED;
    }

    private void validateAutomaticRequest(NotificationIngestionRequest request, UUID deviceId) {
        if (request == null || !request.isAutomatic() || request.captureId() == null || deviceId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTURE_PAYLOAD_INVALID",
                "captureId, captureMode AUTOMATIC, and an authenticated capture device are required.");
        }
        String normalized = normalizeNotification(request.text());
        if (normalized.isBlank() || normalized.length() > MAX_NOTIFICATION_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTURE_PAYLOAD_INVALID",
                "Notification text must contain between 1 and 4000 characters.", "text");
        }
        if (!SUPPORTED_SOURCE_PACKAGES.contains(request.sourcePackage())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTURE_SOURCE_UNSUPPORTED",
                "The source package is not supported.", "sourcePackage");
        }
        Instant postedAt = request.notificationPostedAt();
        Instant now = Instant.now();
        if (postedAt == null || postedAt.isBefore(now.minus(MAX_CAPTURE_AGE))
            || postedAt.isAfter(now.plus(MAX_FUTURE_SKEW))) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTURE_TIMESTAMP_INVALID",
                "notificationPostedAt is outside the accepted time window.", "notificationPostedAt");
        }
    }

    private void requireNotificationCaptureEnabled() {
        if (!notificationCaptureEnabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTURE_FEATURE_DISABLED",
                "Automatic notification capture is not enabled on this server.");
        }
    }

    private String normalizeNotification(String value) {
        if (value == null) return "";
        return value.trim().replaceAll("\\s+", " ");
    }

    private Optional<LocalDateTime> parseDateAndTimeStrict(String date, String time) {
        if (isBlank(date)) return Optional.empty();
        try {
            LocalDate localDate = LocalDate.parse(date.trim(), DateTimeFormatter.ISO_LOCAL_DATE);
            LocalTime localTime = isBlank(time) || "null".equalsIgnoreCase(time.trim())
                ? LocalTime.now()
                : LocalTime.parse(time.trim(), DateTimeFormatter.ISO_LOCAL_TIME);
            return Optional.of(LocalDateTime.of(localDate, localTime));
        } catch (DateTimeParseException exception) {
            return Optional.empty();
        }
    }

    private String terminalResponseJson(
        NotificationIngestionResponse.Status status,
        UUID captureId,
        Long transactionId
    ) {
        return "{\"status\":\"" + status + "\",\"captureId\":\"" + captureId + "\",\"transactionId\":"
            + (transactionId == null ? "null" : transactionId) + "}";
    }

    private enum NotificationDecision {
        SAVED,
        IGNORED,
        NEEDS_REVIEW
    }

    private Transaction executeAiOperation(AppUser user, Supplier<Transaction> operation) {
        if (!Boolean.TRUE.equals(user.getIsGuest())) {
            return operation.get();
        }

        if (userRepository.consumeGuestAiTrial(user.getId()) == 0) {
            throw new GuestAiTrialLimitException();
        }

        try {
            Transaction result = operation.get();
            int currentTrials = user.getAiTrialCount() == null ? 1 : user.getAiTrialCount();
            user.setAiTrialCount(Math.max(0, currentTrials - 1));
            return result;
        } catch (RuntimeException | Error exception) {
            userRepository.restoreGuestAiTrial(user.getId());
            throw exception;
        }
    }

    private DateRange buildMonthDateRange(int month, int year) {
        YearMonth yearMonth = YearMonth.of(year, month);
        LocalDateTime startDate = yearMonth.atDay(1).atStartOfDay();
        LocalDateTime endDate = yearMonth.atEndOfMonth().atTime(23, 59, 59);
        return new DateRange(startDate, endDate);
    }

    private LocalDateTime parseDateAndTime(String dateFromAI, String timeFromAI) {
        if (dateFromAI == null || dateFromAI.isBlank()) {
            return LocalDateTime.now();
        }

        LocalDate localDate;
        try {
            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            localDate = LocalDate.parse(dateFromAI.trim(), dateFormatter);
        } catch (DateTimeParseException e) {
            return LocalDateTime.now();
        }

        LocalTime localTime;
        if (timeFromAI == null || timeFromAI.isBlank() || timeFromAI.equalsIgnoreCase("null")) {
            localTime = LocalTime.now();
        } else {
            try {
                localTime = LocalTime.parse(timeFromAI.trim());
            } catch (DateTimeParseException e) {
                localTime = LocalTime.now();
            }
        }

        return LocalDateTime.of(localDate, localTime);
    }

    private record DateRange(LocalDateTime startDate, LocalDateTime endDate) {
    }
}
