package org.edmund.brokeai.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Data;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "receipt")
@Data
public class Transaction {

    @Id
    @GeneratedValue (strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "client_transaction_id", nullable = false, updatable = false)
    private UUID clientTransactionId = UUID.randomUUID();

    @Column(name = "transaction_date")
    private LocalDateTime date;

    private Double amount;

    private String category;

    @Column(name = "payment_method")
    private String paymentMethod;

    @Column(name = "description")
    private String description;

    @Column(name = "input_type")
    private String inputType; // RECEIPT, NOTIFICATION

    @Column(name = "capture_id")
    private UUID captureId;

    @Column(name = "capture_mode", length = 20)
    private String captureMode;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "capture_device_id")
    private UserDevice captureDevice;

    @Column(name = "source_package", length = 255)
    private String sourcePackage;

    @Column(name = "source_notification_posted_at")
    private Instant sourceNotificationPostedAt;

    @JsonIgnore
    @Column(name = "source_payload_hash")
    private String sourcePayloadHash;

    @Column(name = "validation_status")
    private String validationStatus; // PENDING, CONFIRMED

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private Long revision = 1L;

    @Column(name = "deleted_at")
    private Instant deletedAt;
}
