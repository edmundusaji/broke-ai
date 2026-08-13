package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.entity.AccountDeletionRequest;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.DataExportJob;
import org.edmund.brokeai.entity.TransactionClearRequest;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.AccountDeletionRequestRepository;
import org.edmund.brokeai.repository.DataExportJobRepository;
import org.edmund.brokeai.repository.TransactionClearRequestRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.SignedDownloadService;
import org.edmund.brokeai.service.DataControlService;
import org.edmund.brokeai.service.PrivateObjectStorage;
import org.edmund.brokeai.service.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DataControlServiceImpl implements DataControlService {
    private final CurrentUserService currentUserService;
    private final DataExportJobRepository dataExportJobRepository;
    private final TransactionClearRequestRepository transactionClearRequestRepository;
    private final AccountDeletionRequestRepository accountDeletionRequestRepository;
    private final TransactionRepository transactionRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final JdbcTemplate jdbcTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final PrivateObjectStorage objectStorage;
    private final SignedDownloadService signedDownloadService;

    @Override
    @Transactional
    public ProfileSettingsApi.DataExport requestExport(String idempotencyKey) {
        AppUser user = currentUserService.getCurrentUser();
        String key = requireIdempotencyKey(idempotencyKey);
        DataExportJob existing = dataExportJobRepository.findByUserIdAndIdempotencyKey(user.getId(), key).orElse(null);
        if (existing != null) return map(existing);

        DataExportJob job = new DataExportJob();
        job.setUser(user);
        job.setIdempotencyKey(key);
        DataExportJob saved = dataExportJobRepository.save(job);
        auditService.record(user, "EXPORT_REQUESTED", null, Map.of("jobId", saved.getId().toString()));
        eventPublisher.publishEvent(new DataExportRequestedEvent(saved.getId()));
        return map(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileSettingsApi.DataExport getExport(UUID jobId) {
        Long userId = currentUserService.getCurrentUser().getId();
        return map(dataExportJobRepository.findByIdAndUserId(jobId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Export job not found.")));
    }

    @Override
    @Transactional
    public byte[] downloadExport(UUID jobId, String token) {
        DataExportJob job = dataExportJobRepository.findById(jobId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Export job not found."));
        AppUser user = job.getUser();
        signedDownloadService.validate(token, jobId, user.getId());
        if (!"ready".equals(job.getStatus()) || job.getObjectKey() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "EXPORT_NOT_READY", "The export is not ready for download.");
        }
        if (job.getExpiresAt() == null || job.getExpiresAt().isBefore(Instant.now())) {
            throw new ApiException(HttpStatus.GONE, "RESOURCE_NOT_FOUND", "The export has expired.");
        }
        auditService.record(user, "EXPORT_DOWNLOADED", null, Map.of("jobId", jobId.toString()));
        return objectStorage.get(job.getObjectKey());
    }

    @Override
    @Transactional
    public ProfileSettingsApi.ClearTransactions clearTransactions(
        String idempotencyKey,
        ProfileSettingsApi.ClearTransactionsRequest request
    ) {
        AppUser user = currentUserService.getCurrentUser();
        String key = requireIdempotencyKey(idempotencyKey);
        TransactionClearRequest previous = transactionClearRequestRepository
            .findByUserIdAndIdempotencyKey(user.getId(), key).orElse(null);
        if (previous != null) {
            return new ProfileSettingsApi.ClearTransactions(previous.getDeletedCount(), previous.getRecoverableUntil());
        }
        requireConfirmation(request.confirmation(), "CLEAR");
        reauthenticate(user, request.currentPassword());

        Instant now = Instant.now();
        int count = transactionRepository.softDeleteAllByUserId(user.getId(), now);
        int syncRows = jdbcTemplate.update(
            "UPDATE user_sync_state SET status = 'synced', last_synced_at = ?, " +
                "server_revision = server_revision + 1 WHERE user_id = ?",
            Timestamp.from(now), user.getId()
        );
        if (syncRows == 0) {
            jdbcTemplate.update(
                "INSERT INTO user_sync_state (user_id, status, last_synced_at, server_revision) " +
                    "VALUES (?, 'synced', ?, 1)",
                user.getId(), Timestamp.from(now)
            );
        }
        TransactionClearRequest clear = new TransactionClearRequest();
        clear.setUser(user);
        clear.setIdempotencyKey(key);
        clear.setDeletedCount((long) count);
        clear.setRecoverableUntil(now.plus(Duration.ofDays(7)));
        transactionClearRequestRepository.save(clear);
        auditService.record(user, "TRANSACTION_HISTORY_CLEARED", null,
            Map.of("deletedCount", count, "recoverableUntil", clear.getRecoverableUntil().toString()));
        return new ProfileSettingsApi.ClearTransactions(clear.getDeletedCount(), clear.getRecoverableUntil());
    }

    @Override
    @Transactional
    public ProfileSettingsApi.AccountDeletion requestDeletion(
        String idempotencyKey,
        ProfileSettingsApi.AccountDeletionRequest request
    ) {
        AppUser user = currentUserService.getCurrentUser();
        String key = requireIdempotencyKey(idempotencyKey);
        AccountDeletionRequest previous = accountDeletionRequestRepository
            .findByUserIdAndIdempotencyKey(user.getId(), key).orElse(null);
        if (previous != null) return map(previous);
        if (accountDeletionRequestRepository.findFirstByUserIdAndStatusOrderByRequestedAtDesc(user.getId(), "pending").isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT, "CONFLICTING_UPDATE", "An account deletion is already pending.");
        }
        requireConfirmation(request.confirmation(), "DELETE");
        reauthenticate(user, request.currentPassword());

        Instant now = Instant.now();
        AccountDeletionRequest deletion = new AccountDeletionRequest();
        deletion.setUser(user);
        deletion.setIdempotencyKey(key);
        deletion.setScheduledFor(now.plus(Duration.ofDays(7)));
        AccountDeletionRequest saved = accountDeletionRequestRepository.save(deletion);
        user.setStatus("pending_deletion");
        user.setUpdatedAt(now);
        userRepository.save(user);
        userSessionRepository.revokeAll(user.getId(), now);
        auditService.record(user, "ACCOUNT_DELETION_REQUESTED", null,
            Map.of("requestId", saved.getId().toString(), "scheduledFor", saved.getScheduledFor().toString()));
        return map(saved);
    }

    @Override
    @Transactional
    public void cancelDeletion() {
        AppUser user = currentUserService.getCurrentUser();
        AccountDeletionRequest deletion = accountDeletionRequestRepository
            .findFirstByUserIdAndStatusOrderByRequestedAtDesc(user.getId(), "pending")
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND",
                "No pending account deletion request was found."));
        deletion.setStatus("cancelled");
        deletion.setCancelledAt(Instant.now());
        accountDeletionRequestRepository.save(deletion);
        user.setStatus("active");
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
        auditService.record(user, "ACCOUNT_DELETION_CANCELLED", null,
            Map.of("requestId", deletion.getId().toString()));
    }

    private void reauthenticate(AppUser user, String password) {
        if (user.getPassword() == null || !passwordEncoder.matches(password, user.getPassword())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "REAUTHENTICATION_REQUIRED",
                "Recent password authentication is required.", "currentPassword");
        }
    }

    private String requireIdempotencyKey(String key) {
        if (key == null || key.isBlank() || key.length() > 255) {
            throw ServiceSupport.validation("Idempotency-Key", "A valid Idempotency-Key header is required.");
        }
        return key.trim();
    }

    private void requireConfirmation(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw ServiceSupport.validation("confirmation", "Confirmation must equal " + expected + ".");
        }
    }

    private ProfileSettingsApi.DataExport map(DataExportJob value) {
        String downloadUrl = "ready".equals(value.getStatus())
            ? "/api/v1/me/data-exports/" + value.getId() + "/download?token=" +
                signedDownloadService.create(value.getId(), value.getUser().getId(), value.getExpiresAt())
            : null;
        return new ProfileSettingsApi.DataExport(
            value.getId(), value.getStatus(), value.getExportFormat(), value.getRequestedAt(), value.getCompletedAt(),
            downloadUrl, value.getExpiresAt(), value.getFailureReason()
        );
    }

    private ProfileSettingsApi.AccountDeletion map(AccountDeletionRequest value) {
        return new ProfileSettingsApi.AccountDeletion(
            value.getId(), value.getStatus(), value.getRequestedAt(), value.getScheduledFor()
        );
    }
}
