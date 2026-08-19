package org.edmund.brokeai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "support_tickets")
@Data
public class SupportTicket {
    @Id
    @GeneratedValue
    @UuidGenerator
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Column(nullable = false, length = 20)
    private String type;

    @Column(nullable = false, length = 160)
    private String subject;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false, length = 30)
    private String status = "open";

    @Column(name = "app_version", nullable = false, length = 30)
    private String appVersion;

    @Column(nullable = false, length = 20)
    private String platform;

    @Column(name = "os_version", nullable = false, length = 60)
    private String osVersion;

    @Column(name = "device_model", nullable = false, length = 100)
    private String deviceModel;

    @Column(nullable = false, length = 35)
    private String locale;

    @Column(name = "current_route")
    private String currentRoute;

    @Column(name = "attachment_object_key")
    private String attachmentObjectKey;

    @Column(name = "contact_email_encrypted", columnDefinition = "text")
    private String contactEmailEncrypted;

    @Column(name = "contact_consent_at")
    private Instant contactConsentAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "diagnostic_metadata", nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> diagnosticMetadata = Map.of();

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Version
    @Column(nullable = false)
    private Long revision = 1L;
}
