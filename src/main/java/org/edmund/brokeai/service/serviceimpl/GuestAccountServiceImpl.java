package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.TransactionClearRequest;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.TransactionClearRequestRepository;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.GuestAccountService;
import org.edmund.brokeai.service.SecurityAuditService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class GuestAccountServiceImpl implements GuestAccountService {
    private final CurrentUserService currentUserService;
    private final TransactionRepository transactionRepository;
    private final TransactionClearRequestRepository transactionClearRequestRepository;
    private final UserSessionRepository userSessionRepository;
    private final UserRepository userRepository;
    private final SecurityAuditService auditService;
    private final GuestDataPurgeService purgeService;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public ProfileSettingsApi.ClearTransactions clearTransactions(
        String idempotencyKey,
        ProfileSettingsApi.GuestConfirmationRequest request
    ) {
        AppUser guest = requireLockedGuest();
        String key = requireUuidIdempotencyKey(idempotencyKey);
        requireConfirmation(request.confirmation(), "CLEAR");
        TransactionClearRequest previous = transactionClearRequestRepository
            .findByUserIdAndIdempotencyKey(guest.getId(), key)
            .orElse(null);
        if (previous != null) {
            return new ProfileSettingsApi.ClearTransactions(previous.getDeletedCount(), previous.getRecoverableUntil());
        }
        Instant now = Instant.now();
        int count = transactionRepository.softDeleteAllByUserId(guest.getId(), now);
        updateSyncRevision(guest.getId(), now);

        TransactionClearRequest clear = new TransactionClearRequest();
        clear.setUser(guest);
        clear.setIdempotencyKey(key);
        clear.setDeletedCount((long) count);
        clear.setRecoverableUntil(now.plus(Duration.ofDays(7)));
        transactionClearRequestRepository.save(clear);
        auditService.record(guest, "GUEST_TRANSACTION_HISTORY_CLEARED", null,
            Map.of("deletedCount", count, "recoverableUntil", clear.getRecoverableUntil().toString()));
        return new ProfileSettingsApi.ClearTransactions(clear.getDeletedCount(), clear.getRecoverableUntil());
    }

    @Override
    @Transactional
    public ProfileSettingsApi.GuestDeletion deleteGuest(
        String idempotencyKey,
        ProfileSettingsApi.GuestConfirmationRequest request
    ) {
        AppUser guest = requireLockedGuest();
        requireUuidIdempotencyKey(idempotencyKey);
        requireConfirmation(request.confirmation(), "DELETE");

        Instant now = Instant.now();
        userSessionRepository.revokeAll(guest.getId(), now);
        auditService.record(guest, "GUEST_IDENTITY_DELETED", null, Map.of("deletedAt", now.toString()));
        purgeService.purgeOwnedData(guest.getId());

        guest.setFullName("Deleted Guest");
        guest.setUsername("deleted_" + UUID.randomUUID().toString().replace("-", "").substring(0, 22));
        guest.setEmail(null);
        guest.setPassword(null);
        guest.setPhone(null);
        guest.setPendingEmail(null);
        guest.setAvatarObjectKey(null);
        guest.setAiTrialCount(0);
        guest.setStatus("deleted");
        guest.setDeletedAt(now);
        guest.setUpdatedAt(now);
        userRepository.save(guest);
        return new ProfileSettingsApi.GuestDeletion(true);
    }

    @Override
    @Transactional(readOnly = true)
    public ProfileSettingsApi.GuestDataSummary getDataSummary() {
        AppUser guest = requireGuest();
        return new ProfileSettingsApi.GuestDataSummary(
            transactionRepository.countByUserIdAndDeletedAtIsNull(guest.getId()),
            transactionRepository.findFirstTransactionAt(guest.getId()).map(this::toInstant).orElse(null),
            transactionRepository.findLastTransactionAt(guest.getId()).map(this::toInstant).orElse(null),
            Math.max(0, guest.getAiTrialCount() == null ? 0 : guest.getAiTrialCount())
        );
    }

    private AppUser requireGuest() {
        AppUser user = currentUserService.getCurrentUser();
        if (!Boolean.TRUE.equals(user.getIsGuest())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "GUEST_ONLY", "This operation is available only in guest mode.");
        }
        return user;
    }

    private AppUser requireLockedGuest() {
        AppUser authenticated = requireGuest();
        return userRepository.findByIdForUpdate(authenticated.getId())
            .filter(user -> Boolean.TRUE.equals(user.getIsGuest()))
            .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Guest account not found."));
    }

    private String requireUuidIdempotencyKey(String value) {
        try {
            return UUID.fromString(value == null ? "" : value.trim()).toString();
        } catch (IllegalArgumentException exception) {
            throw ServiceSupport.validation("Idempotency-Key", "Idempotency-Key must be a UUID.");
        }
    }

    private void requireConfirmation(String actual, String expected) {
        if (!expected.equals(actual)) {
            throw ServiceSupport.validation("confirmation", "Confirmation must equal " + expected + ".");
        }
    }

    private void updateSyncRevision(Long userId, Instant now) {
        int updated = jdbcTemplate.update(
            "UPDATE user_sync_state SET status = 'synced', last_synced_at = ?, " +
                "server_revision = server_revision + 1 WHERE user_id = ?",
            Timestamp.from(now), userId
        );
        if (updated == 0) {
            jdbcTemplate.update(
                "INSERT INTO user_sync_state (user_id, status, last_synced_at, server_revision) " +
                    "VALUES (?, 'synced', ?, 1)",
                userId, Timestamp.from(now)
            );
        }
    }

    private Instant toInstant(LocalDateTime value) {
        return value.atZone(ZoneId.systemDefault()).toInstant();
    }
}
