package org.edmund.brokeai.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserChangeLogRepository {
    private final JdbcTemplate jdbcTemplate;

    public List<ChangeRow> findAfter(Long userId, long sequence, int limit) {
        return jdbcTemplate.query(
            """
            SELECT sequence,
                   client_entity_id,
                   change_type,
                   entity_revision,
                   snapshot,
                   created_at
              FROM user_change_log
             WHERE user_id = ? AND sequence > ?
             ORDER BY sequence
             LIMIT ?
            """,
            (resultSet, rowNumber) -> new ChangeRow(
                resultSet.getLong("sequence"),
                resultSet.getObject("client_entity_id", UUID.class),
                resultSet.getString("change_type"),
                resultSet.getLong("entity_revision"),
                resultSet.getString("snapshot"),
                resultSet.getTimestamp("created_at").toInstant()
            ),
            userId,
            sequence,
            limit
        );
    }

    public long currentRevision(Long userId) {
        Long value = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(sequence), 0) FROM user_change_log WHERE user_id = ?",
            Long.class,
            userId
        );
        return value == null ? 0 : value;
    }

    public record ChangeRow(
        long sequence,
        UUID clientEntityId,
        String changeType,
        long entityRevision,
        String snapshot,
        Instant createdAt
    ) {
    }
}
