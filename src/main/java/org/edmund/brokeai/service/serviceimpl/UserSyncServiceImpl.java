package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.service.UserSyncService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class UserSyncServiceImpl implements UserSyncService {
    private final JdbcTemplate jdbcTemplate;

    @Override
    public void markChanged(Long userId) {
        Instant now = Instant.now();
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
}
