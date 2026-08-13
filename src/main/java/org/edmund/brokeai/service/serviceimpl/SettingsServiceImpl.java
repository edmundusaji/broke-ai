package org.edmund.brokeai.service.serviceimpl;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.NotificationPreference;
import org.edmund.brokeai.entity.PrivacyPreference;
import org.edmund.brokeai.entity.UserDevice;
import org.edmund.brokeai.entity.UserPreference;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.NotificationPreferenceRepository;
import org.edmund.brokeai.repository.PrivacyPreferenceRepository;
import org.edmund.brokeai.repository.UserDeviceRepository;
import org.edmund.brokeai.repository.UserPreferenceRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.SensitiveValueCipher;
import org.edmund.brokeai.service.SettingsService;
import org.edmund.brokeai.service.SecurityAuditService;
import org.edmund.brokeai.service.UserSyncService;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class SettingsServiceImpl implements SettingsService {

    private final CurrentUserService currentUserService;
    private final UserPreferenceRepository userPreferenceRepository;
    private final NotificationPreferenceRepository notificationPreferenceRepository;
    private final PrivacyPreferenceRepository privacyPreferenceRepository;
    private final UserDeviceRepository userDeviceRepository;
    private final SensitiveValueCipher sensitiveValueCipher;
    private final JdbcTemplate jdbcTemplate;
    private final SecurityAuditService auditService;
    private final UserSyncService userSyncService;
    private final EntityManager entityManager;

    @Override
    @Transactional
    public ProfileSettingsApi.Preferences getPreferences() {
        return map(getOrCreatePreferences(currentUserService.getCurrentUser()));
    }

    @Override
    @Transactional
    public ProfileSettingsApi.Preferences updatePreferences(
        ProfileSettingsApi.PreferencesUpdateRequest request,
        String ifMatch
    ) {
        if (request == null || allNull(
            request.currencyCode(), request.languageCode(), request.regionCode(), request.timeZone(), request.themeMode()
        )) {
            throw ServiceSupport.validation(null, "At least one preference is required.");
        }
        UserPreference preference = getOrCreatePreferences(currentUserService.getCurrentUser());
        ServiceSupport.requireRevision(preference.getRevision(), ifMatch);

        if (request.currencyCode() != null) {
            ServiceSupport.validateCurrency(request.currencyCode());
            preference.setCurrencyCode(request.currencyCode());
        }
        if (request.languageCode() != null) {
            ServiceSupport.validateLanguage(request.languageCode());
            preference.setLanguageCode(request.languageCode());
        }
        if (request.regionCode() != null) {
            ServiceSupport.validateRegion(request.regionCode());
            preference.setRegionCode(request.regionCode());
        }
        if (request.timeZone() != null) {
            ServiceSupport.validateTimeZone(request.timeZone());
            preference.setTimeZone(request.timeZone());
        }
        if (request.themeMode() != null) {
            preference.setThemeMode(request.themeMode());
        }
        preference.setUpdatedAt(Instant.now());
        UserPreference saved = userPreferenceRepository.saveAndFlush(preference);
        userSyncService.markChanged(saved.getUserId());
        return map(saved);
    }

    @Override
    @Transactional
    public ProfileSettingsApi.NotificationPreferences getNotificationPreferences() {
        return map(getOrCreateNotifications(currentUserService.getCurrentUser()));
    }

    @Override
    @Transactional
    public ProfileSettingsApi.NotificationPreferences updateNotificationPreferences(
        ProfileSettingsApi.NotificationPreferencesUpdateRequest request,
        String ifMatch
    ) {
        if (request == null || allNull(
            request.spendingReminderEnabled(), request.reminderTime(), request.weeklySummaryEnabled(),
            request.monthlyReportEnabled(), request.securityAlertsEnabled(), request.productUpdatesEnabled()
        )) {
            throw ServiceSupport.validation(null, "At least one notification preference is required.");
        }
        NotificationPreference preference = getOrCreateNotifications(currentUserService.getCurrentUser());
        ServiceSupport.requireRevision(preference.getRevision(), ifMatch);
        if (request.spendingReminderEnabled() != null) preference.setSpendingReminders(request.spendingReminderEnabled());
        if (request.reminderTime() != null) preference.setReminderTime(request.reminderTime());
        if (request.weeklySummaryEnabled() != null) preference.setWeeklySummary(request.weeklySummaryEnabled());
        if (request.monthlyReportEnabled() != null) preference.setMonthlyReport(request.monthlyReportEnabled());
        if (request.securityAlertsEnabled() != null) preference.setSecurityAlerts(request.securityAlertsEnabled());
        if (request.productUpdatesEnabled() != null) preference.setProductUpdates(request.productUpdatesEnabled());
        preference.setUpdatedAt(Instant.now());
        NotificationPreference saved = notificationPreferenceRepository.saveAndFlush(preference);
        userSyncService.markChanged(saved.getUserId());
        return map(saved);
    }

    @Override
    @Transactional
    public ProfileSettingsApi.PrivacyPreferences getPrivacyPreferences() {
        return map(getOrCreatePrivacy(currentUserService.getCurrentUser()));
    }

    @Override
    @Transactional
    public ProfileSettingsApi.PrivacyPreferences updatePrivacyPreferences(
        ProfileSettingsApi.PrivacyPreferencesUpdateRequest request,
        String ifMatch
    ) {
        PrivacyPreference preference = getOrCreatePrivacy(currentUserService.getCurrentUser());
        ServiceSupport.requireRevision(preference.getRevision(), ifMatch);
        if (request.personalizedInsights() != null) preference.setPersonalizedInsights(request.personalizedInsights());
        if (request.anonymousAnalytics() != null) preference.setAnonymousAnalytics(request.anonymousAnalytics());
        preference.setPolicyVersion(request.policyVersion());
        preference.setSourceDeviceId(request.sourceDeviceId());
        preference.setSourcePlatform(request.sourcePlatform());
        preference.setConsentedAt(Instant.now());
        preference.setUpdatedAt(Instant.now());
        PrivacyPreference saved = privacyPreferenceRepository.saveAndFlush(preference);
        jdbcTemplate.update(
            "INSERT INTO privacy_consent_history " +
                "(user_id, personalized_insights, anonymous_analytics, policy_version, consented_at, " +
                "source_device_id, source_platform) VALUES (?, ?, ?, ?, ?, ?, ?)",
            saved.getUserId(),
            saved.getPersonalizedInsights(),
            saved.getAnonymousAnalytics(),
            saved.getPolicyVersion(),
            Timestamp.from(saved.getConsentedAt()),
            saved.getSourceDeviceId(),
            saved.getSourcePlatform()
        );
        auditService.record(saved.getUser(), "PRIVACY_PREFERENCE_CHANGED", null,
            Map.of("policyVersion", saved.getPolicyVersion()));
        userSyncService.markChanged(saved.getUserId());
        return map(saved);
    }

    @Override
    @Transactional
    public ProfileSettingsApi.SyncStatus getSyncStatus() {
        Long userId = currentUserService.getCurrentUser().getId();
        var statuses = jdbcTemplate.query(
            "SELECT status, last_synced_at, server_revision FROM user_sync_state WHERE user_id = ?",
            (resultSet, rowNumber) -> new ProfileSettingsApi.SyncStatus(
                resultSet.getString("status"),
                resultSet.getTimestamp("last_synced_at") == null
                    ? null
                    : resultSet.getTimestamp("last_synced_at").toInstant(),
                resultSet.getLong("server_revision")
            ),
            userId
        );
        if (!statuses.isEmpty()) return statuses.getFirst();
        Instant now = Instant.now();
        jdbcTemplate.update(
            "INSERT INTO user_sync_state (user_id, status, last_synced_at, server_revision) VALUES (?, 'synced', ?, 1)",
            userId, Timestamp.from(now)
        );
        return new ProfileSettingsApi.SyncStatus("synced", now, 1);
    }

    @Override
    @Transactional
    public ProfileSettingsApi.Device registerDevice(ProfileSettingsApi.DeviceRegistrationRequest request) {
        AppUser user = currentUserService.getCurrentUser();
        String tokenHash = ServiceSupport.sha256(request.pushToken());
        UserDevice device = userDeviceRepository.findByPushTokenHash(tokenHash)
            .filter(existing -> existing.getUser().getId().equals(user.getId()))
            .orElseGet(UserDevice::new);
        if (request.deviceId() != null) {
            device = userDeviceRepository.findByIdAndUserId(request.deviceId(), user.getId()).orElse(device);
        }
        device.setUser(user);
        device.setPlatform(request.platform());
        device.setDeviceName(request.deviceName());
        device.setPushTokenHash(tokenHash);
        device.setPushTokenEncrypted(sensitiveValueCipher.encrypt(request.pushToken()));
        device.setAppVersion(request.appVersion());
        device.setLastSeenAt(Instant.now());
        device.setUpdatedAt(Instant.now());
        UserDevice saved = userDeviceRepository.save(device);
        userSyncService.markChanged(user.getId());
        return map(saved);
    }

    @Override
    @Transactional
    public void unregisterDevice(UUID deviceId) {
        Long userId = currentUserService.getCurrentUser().getId();
        UserDevice device = userDeviceRepository.findByIdAndUserId(deviceId, userId)
            .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "Device not found."));
        userDeviceRepository.delete(device);
        userSyncService.markChanged(userId);
    }

    private UserPreference getOrCreatePreferences(AppUser user) {
        return userPreferenceRepository.findById(user.getId()).orElseGet(() -> {
            UserPreference value = new UserPreference();
            value.setUserId(user.getId());
            value.setUser(user);
            entityManager.persist(value);
            return value;
        });
    }

    private NotificationPreference getOrCreateNotifications(AppUser user) {
        return notificationPreferenceRepository.findById(user.getId()).orElseGet(() -> {
            NotificationPreference value = new NotificationPreference();
            value.setUserId(user.getId());
            value.setUser(user);
            entityManager.persist(value);
            return value;
        });
    }

    private PrivacyPreference getOrCreatePrivacy(AppUser user) {
        return privacyPreferenceRepository.findById(user.getId()).orElseGet(() -> {
            PrivacyPreference value = new PrivacyPreference();
            value.setUserId(user.getId());
            value.setUser(user);
            value.setPolicyVersion("2026-08");
            value.setConsentedAt(Instant.now());
            entityManager.persist(value);
            return value;
        });
    }

    private boolean allNull(Object... values) {
        for (Object value : values) if (value != null) return false;
        return true;
    }

    private ProfileSettingsApi.Preferences map(UserPreference value) {
        return new ProfileSettingsApi.Preferences(
            value.getCurrencyCode(), value.getLanguageCode(), value.getRegionCode(), value.getTimeZone(),
            value.getThemeMode(), value.getRevision(), value.getUpdatedAt()
        );
    }

    private ProfileSettingsApi.NotificationPreferences map(NotificationPreference value) {
        return new ProfileSettingsApi.NotificationPreferences(
            value.getSpendingReminders(), value.getReminderTime(), value.getWeeklySummary(), value.getMonthlyReport(),
            value.getSecurityAlerts(), value.getProductUpdates(), value.getRevision(), value.getUpdatedAt()
        );
    }

    private ProfileSettingsApi.PrivacyPreferences map(PrivacyPreference value) {
        return new ProfileSettingsApi.PrivacyPreferences(
            value.getPersonalizedInsights(), value.getAnonymousAnalytics(), value.getPolicyVersion(),
            value.getConsentedAt(), value.getSourceDeviceId(), value.getSourcePlatform(), value.getRevision(),
            value.getUpdatedAt()
        );
    }

    private ProfileSettingsApi.Device map(UserDevice value) {
        return new ProfileSettingsApi.Device(
            value.getId(), value.getPlatform(), value.getDeviceName(), value.getAppVersion(),
            value.getLastSeenAt(), value.getCreatedAt()
        );
    }
}
