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

@Entity
@Table(name = "user_preferences")
@Data
public class UserPreference {
    @Id
    private Long userId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "currency_code", nullable = false, length = 3)
    private String currencyCode = "IDR";

    @Column(name = "language_code", nullable = false, length = 20)
    private String languageCode = "en";

    @Column(name = "region_code", nullable = false, length = 2)
    private String regionCode = "ID";

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone = "Asia/Jakarta";

    @Column(name = "theme_mode", nullable = false, length = 10)
    private String themeMode = "light";

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private Long revision = 1L;
}
