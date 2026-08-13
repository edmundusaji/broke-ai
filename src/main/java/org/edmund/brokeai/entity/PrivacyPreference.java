package org.edmund.brokeai.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.MapsId;
import jakarta.persistence.OneToOne;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "privacy_preferences")
@Data
public class PrivacyPreference {
    @Id
    private Long userId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "personalized_insights", nullable = false)
    private Boolean personalizedInsights = true;

    @Column(name = "anonymous_analytics", nullable = false)
    private Boolean anonymousAnalytics = true;

    @Column(name = "policy_version", nullable = false, length = 30)
    private String policyVersion;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "source_device_id")
    private UUID sourceDeviceId;

    @Column(name = "source_platform", length = 20)
    private String sourcePlatform;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private Long revision = 1L;
}
