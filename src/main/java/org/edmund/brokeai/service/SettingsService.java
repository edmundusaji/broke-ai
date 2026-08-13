package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.ProfileSettingsApi;

import java.util.UUID;

public interface SettingsService {
    ProfileSettingsApi.Preferences getPreferences();

    ProfileSettingsApi.Preferences updatePreferences(
        ProfileSettingsApi.PreferencesUpdateRequest request,
        String ifMatch
    );

    ProfileSettingsApi.NotificationPreferences getNotificationPreferences();

    ProfileSettingsApi.NotificationPreferences updateNotificationPreferences(
        ProfileSettingsApi.NotificationPreferencesUpdateRequest request,
        String ifMatch
    );

    ProfileSettingsApi.PrivacyPreferences getPrivacyPreferences();

    ProfileSettingsApi.PrivacyPreferences updatePrivacyPreferences(
        ProfileSettingsApi.PrivacyPreferencesUpdateRequest request,
        String ifMatch
    );

    ProfileSettingsApi.SyncStatus getSyncStatus();

    ProfileSettingsApi.Device registerDevice(ProfileSettingsApi.DeviceRegistrationRequest request);

    void unregisterDevice(UUID deviceId);
}
