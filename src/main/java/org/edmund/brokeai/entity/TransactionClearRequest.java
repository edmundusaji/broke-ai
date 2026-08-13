package org.edmund.brokeai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Data;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
    name = "transaction_clear_requests",
    uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "idempotency_key"})
)
@Data
public class TransactionClearRequest {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    @Column(name = "deleted_count", nullable = false)
    private Long deletedCount;

    @Column(name = "recoverable_until", nullable = false)
    private Instant recoverableUntil;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();
}
