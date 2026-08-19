package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.DataExportJob;
import org.edmund.brokeai.repository.DataExportJobRepository;
import org.edmund.brokeai.service.PrivateObjectStorage;
import jakarta.persistence.EntityManager;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GuestDataPurgeService {
    private static final List<String> USER_OWNED_TABLES = List.of(
        "receipt",
        "transaction_clear_requests",
        "account_deletion_requests",
        "idempotency_records",
        "support_tickets",
        "privacy_consent_history",
        "privacy_preferences",
        "notification_preferences",
        "user_preferences",
        "user_sync_state",
        "email_change_requests",
        "avatar_uploads",
        "outbound_email_jobs",
        "data_export_jobs",
        "user_sessions",
        "user_devices"
    );

    private final DataExportJobRepository dataExportJobRepository;
    private final PrivateObjectStorage objectStorage;
    private final JdbcTemplate jdbcTemplate;
    private final EntityManager entityManager;

    public void purgeOwnedData(Long userId) {
        for (DataExportJob job : dataExportJobRepository.findByUserId(userId)) {
            if (job.getObjectKey() != null) {
                objectStorage.delete(job.getObjectKey());
            }
        }
        if (tableExists("security_audit_log")) {
            jdbcTemplate.update("UPDATE security_audit_log SET session_id = NULL WHERE user_id = ?", userId);
        }
        for (String table : USER_OWNED_TABLES) {
            if (tableExists(table)) {
                jdbcTemplate.update("DELETE FROM " + table + " WHERE user_id = ?", userId);
            }
        }
    }

    public void hardDeleteGuest(AppUser guest) {
        Long userId = guest.getId();
        entityManager.flush();
        purgeOwnedData(userId);
        if (tableExists("security_audit_log")) {
            jdbcTemplate.update("UPDATE security_audit_log SET user_id = NULL WHERE user_id = ?", userId);
        }
        entityManager.clear();
        jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM information_schema.tables WHERE LOWER(table_name) = ?",
            Integer.class,
            tableName.toLowerCase()
        );
        return count != null && count > 0;
    }
}
