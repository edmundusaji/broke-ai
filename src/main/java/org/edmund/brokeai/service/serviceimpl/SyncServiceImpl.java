package org.edmund.brokeai.service.serviceimpl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.edmund.brokeai.dto.SyncApi;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.SyncMutationReceiptRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserChangeLogRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.RateLimitingService;
import org.edmund.brokeai.service.SyncService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@Service
@Slf4j
public class SyncServiceImpl implements SyncService {
    private static final int MAX_PULL_LIMIT = 500;
    private static final Duration MUTATION_RETENTION = Duration.ofDays(180);

    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final UserChangeLogRepository changeLogRepository;
    private final SyncMutationReceiptRepository mutationReceiptRepository;
    private final ObjectMapper objectMapper;
    private final RateLimitingService rateLimitingService;
    private final TransactionTemplate operationTransaction;

    public SyncServiceImpl(
        CurrentUserService currentUserService,
        UserRepository userRepository,
        TransactionRepository transactionRepository,
        UserChangeLogRepository changeLogRepository,
        SyncMutationReceiptRepository mutationReceiptRepository,
        ObjectMapper objectMapper,
        RateLimitingService rateLimitingService,
        PlatformTransactionManager transactionManager
    ) {
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.changeLogRepository = changeLogRepository;
        this.mutationReceiptRepository = mutationReceiptRepository;
        this.objectMapper = objectMapper;
        this.rateLimitingService = rateLimitingService;
        this.operationTransaction = new TransactionTemplate(transactionManager);
        this.operationTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public SyncApi.PushResponse push(SyncApi.PushRequest request) {
        Long userId = currentUserService.getCurrentUser().getId();
        requireRateLimit(userId, request.operations().size());
        List<SyncApi.OperationResult> results = new ArrayList<>(request.operations().size());

        for (SyncApi.Mutation mutation : request.operations()) {
            try {
                SyncApi.OperationResult result = operationTransaction.execute(
                    status -> processMutation(userId, mutation)
                );
                if (result == null) throw new IllegalStateException("Mutation transaction returned no result");
                results.add(result);
            } catch (ApiException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                log.warn("Sync mutation failed and can be retried ({})", exception.getClass().getSimpleName());
                results.add(new SyncApi.OperationResult(
                    mutation.operationId(),
                    SyncApi.OperationStatus.RETRYABLE_FAILURE,
                    "MUTATION_TEMPORARILY_UNAVAILABLE",
                    "The mutation could not be applied yet. Retry later.",
                    null
                ));
            }
        }

        return new SyncApi.PushResponse(results, changeLogRepository.currentRevision(userId));
    }

    @Override
    public SyncApi.PullResponse pull(String cursor, int limit) {
        if (limit < 1 || limit > MAX_PULL_LIMIT) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_ERROR",
                "limit must be between 1 and " + MAX_PULL_LIMIT + ".",
                "limit"
            );
        }

        Long userId = currentUserService.getCurrentUser().getId();
        requireRateLimit(userId, 1);
        long afterSequence = decodeCursor(cursor);
        List<UserChangeLogRepository.ChangeRow> rows = changeLogRepository.findAfter(
            userId,
            afterSequence,
            limit + 1
        );
        boolean hasMore = rows.size() > limit;
        List<UserChangeLogRepository.ChangeRow> pageRows = hasMore
            ? rows.subList(0, limit)
            : rows;

        List<SyncApi.PullChange> changes = pageRows.stream().map(this::mapChange).toList();
        long nextSequence = pageRows.isEmpty()
            ? afterSequence
            : pageRows.getLast().sequence();
        return new SyncApi.PullResponse(
            changes,
            encodeCursor(nextSequence),
            hasMore,
            changeLogRepository.currentRevision(userId)
        );
    }

    private SyncApi.OperationResult processMutation(Long userId, SyncApi.Mutation mutation) {
        String mutationType = "TRANSACTION_" + mutation.type().name();
        String requestHash = hashMutation(mutation);
        SyncMutationReceiptRepository.Reservation reservation = mutationReceiptRepository.reserve(
            userId,
            mutation.operationId(),
            mutationType,
            requestHash,
            Instant.now().plus(MUTATION_RETENTION)
        );
        if (!reservation.acquired()) {
            return readStoredResult(reservation.responseBody());
        }

        SyncApi.OperationResult result = switch (mutation.type()) {
            case CREATE -> applyCreate(userId, mutation);
            case UPDATE -> applyUpdate(userId, mutation);
            case DELETE -> applyDelete(userId, mutation);
        };
        mutationReceiptRepository.complete(userId, mutation.operationId(), writeJson(result));
        return result;
    }

    private void requireRateLimit(Long userId, int cost) {
        if (!rateLimitingService.tryConsumeSync(userId, cost)) {
            throw new ApiException(
                HttpStatus.TOO_MANY_REQUESTS,
                "SYNC_RATE_LIMITED",
                "Too many synchronization operations. Retry later.",
                null,
                60
            );
        }
    }

    private SyncApi.OperationResult applyCreate(Long userId, SyncApi.Mutation mutation) {
        String invalid = validateInput(mutation.transaction());
        if (mutation.baseRevision() != null) invalid = "baseRevision must be null for CREATE.";
        if (invalid != null) return rejected(mutation, invalid);

        var existing = transactionRepository.findByClientTransactionIdForUpdate(
            userId,
            mutation.clientTransactionId()
        );
        if (existing.isPresent()) {
            Transaction current = existing.get();
            if (current.getDeletedAt() != null) return conflict(mutation, current, "TRANSACTION_DELETED");
            return new SyncApi.OperationResult(
                mutation.operationId(),
                SyncApi.OperationStatus.DUPLICATE,
                null,
                null,
                SyncApi.TransactionView.from(current)
            );
        }

        AppUser user = userRepository.findById(userId)
            .orElseThrow(() -> new ApiException(
                HttpStatus.UNAUTHORIZED,
                "UNAUTHENTICATED",
                "Authentication is required."
            ));
        Transaction transaction = new Transaction();
        transaction.setClientTransactionId(mutation.clientTransactionId());
        transaction.setUser(user);
        transaction.setInputType("MANUAL");
        transaction.setValidationStatus("CONFIRMED");
        applyDetails(transaction, mutation.transaction());
        Transaction saved = transactionRepository.saveAndFlush(transaction);
        return applied(mutation, saved);
    }

    private SyncApi.OperationResult applyUpdate(Long userId, SyncApi.Mutation mutation) {
        String invalid = validateInput(mutation.transaction());
        if (mutation.baseRevision() == null) invalid = "baseRevision is required for UPDATE.";
        if (invalid != null) return rejected(mutation, invalid);

        Transaction transaction = transactionRepository.findByClientTransactionIdForUpdate(
            userId,
            mutation.clientTransactionId()
        ).orElse(null);
        if (transaction == null) return rejected(mutation, "Transaction not found.", "RESOURCE_NOT_FOUND");
        if (transaction.getDeletedAt() != null) return conflict(mutation, transaction, "TRANSACTION_DELETED");
        if (!mutation.baseRevision().equals(transaction.getRevision())) {
            return conflict(mutation, transaction, "TRANSACTION_REVISION_CONFLICT");
        }

        applyDetails(transaction, mutation.transaction());
        transaction.setUpdatedAt(Instant.now());
        Transaction saved = transactionRepository.saveAndFlush(transaction);
        return applied(mutation, saved);
    }

    private SyncApi.OperationResult applyDelete(Long userId, SyncApi.Mutation mutation) {
        if (mutation.baseRevision() == null) {
            return rejected(mutation, "baseRevision is required for DELETE.");
        }
        Transaction transaction = transactionRepository.findByClientTransactionIdForUpdate(
            userId,
            mutation.clientTransactionId()
        ).orElse(null);
        if (transaction == null) return rejected(mutation, "Transaction not found.", "RESOURCE_NOT_FOUND");
        if (transaction.getDeletedAt() != null) {
            if (mutation.baseRevision().equals(transaction.getRevision())) {
                return new SyncApi.OperationResult(
                    mutation.operationId(),
                    SyncApi.OperationStatus.DUPLICATE,
                    null,
                    null,
                    SyncApi.TransactionView.from(transaction)
                );
            }
            return conflict(mutation, transaction, "TRANSACTION_REVISION_CONFLICT");
        }
        if (!mutation.baseRevision().equals(transaction.getRevision())) {
            return conflict(mutation, transaction, "TRANSACTION_REVISION_CONFLICT");
        }

        Instant now = Instant.now();
        transaction.setDeletedAt(now);
        transaction.setUpdatedAt(now);
        Transaction saved = transactionRepository.saveAndFlush(transaction);
        return applied(mutation, saved);
    }

    private void applyDetails(Transaction transaction, SyncApi.TransactionInput input) {
        LocalTime time = transaction.getDate() == null
            ? LocalTime.now()
            : transaction.getDate().toLocalTime();
        transaction.setDate(LocalDateTime.of(input.date(), time));
        transaction.setAmount(input.amount());
        transaction.setCategory(input.category().trim());
        transaction.setPaymentMethod(input.paymentMethod().trim());
        transaction.setDescription(input.description().trim());
    }

    private String validateInput(SyncApi.TransactionInput input) {
        if (input == null) return "transaction is required.";
        if (input.date() == null) return "transaction.date is required.";
        if (input.amount() == null || !Double.isFinite(input.amount()) || input.amount() <= 0) {
            return "transaction.amount must be a positive finite number.";
        }
        if (isInvalidText(input.category())) return "transaction.category must contain 1 to 255 characters.";
        if (isInvalidText(input.paymentMethod())) {
            return "transaction.paymentMethod must contain 1 to 255 characters.";
        }
        if (isInvalidText(input.description())) {
            return "transaction.description must contain 1 to 255 characters.";
        }
        return null;
    }

    private boolean isInvalidText(String value) {
        return value == null || value.isBlank() || value.trim().length() > 255;
    }

    private SyncApi.OperationResult applied(SyncApi.Mutation mutation, Transaction transaction) {
        return new SyncApi.OperationResult(
            mutation.operationId(),
            SyncApi.OperationStatus.APPLIED,
            null,
            null,
            SyncApi.TransactionView.from(transaction)
        );
    }

    private SyncApi.OperationResult conflict(
        SyncApi.Mutation mutation,
        Transaction current,
        String code
    ) {
        return new SyncApi.OperationResult(
            mutation.operationId(),
            SyncApi.OperationStatus.CONFLICT,
            code,
            "The server transaction changed after the local edit was based on it.",
            SyncApi.TransactionView.from(current)
        );
    }

    private SyncApi.OperationResult rejected(SyncApi.Mutation mutation, String message) {
        return rejected(mutation, message, "VALIDATION_ERROR");
    }

    private SyncApi.OperationResult rejected(
        SyncApi.Mutation mutation,
        String message,
        String code
    ) {
        return new SyncApi.OperationResult(
            mutation.operationId(),
            SyncApi.OperationStatus.REJECTED,
            code,
            message,
            null
        );
    }

    private SyncApi.PullChange mapChange(UserChangeLogRepository.ChangeRow row) {
        SyncApi.TransactionView snapshot = readSnapshot(row.snapshot());
        boolean deleted = "DELETE".equals(row.changeType());
        return new SyncApi.PullChange(
            row.sequence(),
            deleted ? SyncApi.ChangeType.TRANSACTION_DELETE : SyncApi.ChangeType.TRANSACTION_UPSERT,
            row.clientEntityId(),
            row.entityRevision(),
            deleted ? null : snapshot,
            deleted ? (snapshot.deletedAt() == null ? row.createdAt() : snapshot.deletedAt()) : null
        );
    }

    private String hashMutation(SyncApi.Mutation mutation) {
        return ServiceSupport.sha256(writeJson(mutation));
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Sync payload could not be serialized", exception);
        }
    }

    private SyncApi.OperationResult readStoredResult(String json) {
        try {
            return objectMapper.readValue(json, SyncApi.OperationResult.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored sync result could not be read", exception);
        }
    }

    private SyncApi.TransactionView readSnapshot(String json) {
        try {
            return objectMapper.readValue(json, SyncApi.TransactionView.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Stored change snapshot could not be read", exception);
        }
    }

    private String encodeCursor(long sequence) {
        String value = "v1:" + sequence;
        return Base64.getUrlEncoder().withoutPadding()
            .encodeToString(value.getBytes(StandardCharsets.US_ASCII));
    }

    private long decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) return 0;
        try {
            String value = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.US_ASCII);
            if (!value.startsWith("v1:")) throw new IllegalArgumentException("Unsupported cursor version");
            long sequence = Long.parseLong(value.substring(3));
            if (sequence < 0) throw new IllegalArgumentException("Negative cursor");
            return sequence;
        } catch (IllegalArgumentException exception) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "INVALID_CURSOR",
                "The synchronization cursor is invalid.",
                "cursor"
            );
        }
    }
}
