package org.edmund.brokeai.repository;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.exception.ApiException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class SyncMutationReceiptRepository {
    private final JdbcTemplate jdbcTemplate;

    public Reservation reserve(
        Long userId,
        UUID operationId,
        String mutationType,
        String requestHash,
        Instant expiresAt
    ) {
        boolean inserted = jdbcTemplate.update(
            """
            INSERT INTO sync_mutation_receipts (
                id, user_id, operation_id, mutation_type, request_hash, created_at, expires_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, operation_id) DO NOTHING
            """,
            UUID.randomUUID(),
            userId,
            operationId,
            mutationType,
            requestHash,
            Timestamp.from(Instant.now()),
            Timestamp.from(expiresAt)
        ) == 1;

        if (inserted) return new Reservation(true, null);

        ExistingReceipt existing = find(userId, operationId).orElseThrow(() -> new ApiException(
            HttpStatus.CONFLICT,
            "MUTATION_RESERVATION_LOST",
            "The mutation could not be safely reserved. Retry with a new operationId."
        ));
        if (!existing.mutationType().equals(mutationType) || !existing.requestHash().equals(requestHash)) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "IDEMPOTENCY_KEY_REUSED",
                "The operationId was already used for a different mutation.",
                "operationId"
            );
        }
        if (existing.responseBody() == null) {
            throw new ApiException(
                HttpStatus.CONFLICT,
                "MUTATION_RESULT_UNAVAILABLE",
                "The previous mutation result is unavailable. Retry later."
            );
        }
        return new Reservation(false, existing.responseBody());
    }

    public void complete(Long userId, UUID operationId, String responseBody) {
        int updated = jdbcTemplate.update(
            """
            UPDATE sync_mutation_receipts
               SET response_body = CAST(? AS jsonb)
             WHERE user_id = ? AND operation_id = ?
            """,
            responseBody,
            userId,
            operationId
        );
        if (updated != 1) {
            throw new IllegalStateException("Reserved sync mutation receipt was not found");
        }
    }

    private Optional<ExistingReceipt> find(Long userId, UUID operationId) {
        return jdbcTemplate.query(
            """
            SELECT mutation_type, request_hash, response_body
              FROM sync_mutation_receipts
             WHERE user_id = ? AND operation_id = ?
            """,
            (resultSet, rowNumber) -> new ExistingReceipt(
                resultSet.getString("mutation_type"),
                resultSet.getString("request_hash"),
                resultSet.getString("response_body")
            ),
            userId,
            operationId
        ).stream().findFirst();
    }

    public record Reservation(boolean acquired, String responseBody) {
    }

    private record ExistingReceipt(String mutationType, String requestHash, String responseBody) {
    }
}
