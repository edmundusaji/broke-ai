package org.edmund.brokeai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class NotificationIdempotencyRepository {
    private static final String OPERATION = "AUTOMATIC_NOTIFICATION_CAPTURE";

    private final JdbcTemplate jdbcTemplate;

    public boolean reserve(Long userId, UUID captureId, String requestHash, Instant expiresAt) {
        return jdbcTemplate.update("""
            INSERT INTO idempotency_records
                (id, user_id, operation, idempotency_key, request_hash, created_at, expires_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, operation, idempotency_key) DO NOTHING
            """,
            UUID.randomUUID(), userId, OPERATION, captureId.toString(), requestHash,
            Timestamp.from(Instant.now()), Timestamp.from(expiresAt)
        ) == 1;
    }

    public void complete(Long userId, UUID captureId, int responseStatus, String responseBody) {
        jdbcTemplate.update("""
            UPDATE idempotency_records
               SET response_status = ?, response_body = CAST(? AS jsonb)
             WHERE user_id = ? AND operation = ? AND idempotency_key = ?
            """, responseStatus, responseBody, userId, OPERATION, captureId.toString());
    }

    public Optional<String> findRequestHash(Long userId, UUID captureId) {
        return jdbcTemplate.query(
            """
            SELECT request_hash
              FROM idempotency_records
             WHERE user_id = ? AND operation = ? AND idempotency_key = ?
            """,
            (resultSet, rowNumber) -> resultSet.getString(1),
            userId, OPERATION, captureId.toString()
        ).stream().findFirst();
    }
}
