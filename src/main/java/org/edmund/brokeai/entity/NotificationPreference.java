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
import java.time.LocalTime;

@Entity
@Table(name = "notification_preferences")
@Data
public class NotificationPreference {
    @Id
    private Long userId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id")
    private AppUser user;

    @Column(name = "spending_reminders", nullable = false)
    private Boolean spendingReminders = true;

    @Column(name = "reminder_time")
    private LocalTime reminderTime;

    @Column(name = "weekly_summary", nullable = false)
    private Boolean weeklySummary = true;

    @Column(name = "monthly_report", nullable = false)
    private Boolean monthlyReport = true;

    @Column(name = "security_alerts", nullable = false)
    private Boolean securityAlerts = true;

    @Column(name = "product_updates", nullable = false)
    private Boolean productUpdates = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private Long revision = 1L;
}
