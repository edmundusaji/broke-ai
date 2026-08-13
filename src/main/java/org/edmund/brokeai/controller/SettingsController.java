package org.edmund.brokeai.controller;

import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.ApiEnvelope;
import org.edmund.brokeai.dto.ProfileSettingsApi;
import org.edmund.brokeai.service.SettingsService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class SettingsController {
    private final SettingsService settingsService;

    @GetMapping("/preferences")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.Preferences>> getPreferences() {
        ProfileSettingsApi.Preferences preferences = settingsService.getPreferences();
        return withRevision(ApiEnvelope.of(preferences), preferences.revision());
    }

    @PatchMapping("/preferences")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.Preferences>> updatePreferences(
        @Valid @RequestBody ProfileSettingsApi.PreferencesUpdateRequest request,
        @RequestHeader("If-Match") String ifMatch
    ) {
        ProfileSettingsApi.Preferences preferences = settingsService.updatePreferences(request, ifMatch);
        return withRevision(ApiEnvelope.of(preferences), preferences.revision());
    }

    @GetMapping("/notification-preferences")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.NotificationPreferences>> getNotificationPreferences() {
        ProfileSettingsApi.NotificationPreferences preferences = settingsService.getNotificationPreferences();
        return withRevision(ApiEnvelope.of(preferences), preferences.revision());
    }

    @PatchMapping("/notification-preferences")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.NotificationPreferences>> updateNotificationPreferences(
        @Valid @RequestBody ProfileSettingsApi.NotificationPreferencesUpdateRequest request,
        @RequestHeader("If-Match") String ifMatch
    ) {
        ProfileSettingsApi.NotificationPreferences preferences =
            settingsService.updateNotificationPreferences(request, ifMatch);
        return withRevision(ApiEnvelope.of(preferences), preferences.revision());
    }

    @GetMapping("/privacy-preferences")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.PrivacyPreferences>> getPrivacyPreferences() {
        ProfileSettingsApi.PrivacyPreferences preferences = settingsService.getPrivacyPreferences();
        return withRevision(ApiEnvelope.of(preferences), preferences.revision());
    }

    @PatchMapping("/privacy-preferences")
    public ResponseEntity<ApiEnvelope<ProfileSettingsApi.PrivacyPreferences>> updatePrivacyPreferences(
        @Valid @RequestBody ProfileSettingsApi.PrivacyPreferencesUpdateRequest request,
        @RequestHeader("If-Match") String ifMatch
    ) {
        ProfileSettingsApi.PrivacyPreferences preferences = settingsService.updatePrivacyPreferences(request, ifMatch);
        return withRevision(ApiEnvelope.of(preferences), preferences.revision());
    }

    @GetMapping("/sync-status")
    public ApiEnvelope<ProfileSettingsApi.SyncStatus> getSyncStatus() {
        return ApiEnvelope.of(settingsService.getSyncStatus());
    }

    @PostMapping("/devices")
    public ApiEnvelope<ProfileSettingsApi.Device> registerDevice(
        @Valid @RequestBody ProfileSettingsApi.DeviceRegistrationRequest request
    ) {
        return ApiEnvelope.of(settingsService.registerDevice(request));
    }

    @DeleteMapping("/devices/{deviceId}")
    public ApiEnvelope<ApiEnvelope.ActionResult> unregisterDevice(@PathVariable UUID deviceId) {
        settingsService.unregisterDevice(deviceId);
        return ApiEnvelope.success();
    }

    private <T> ResponseEntity<T> withRevision(T body, long revision) {
        return ResponseEntity.ok().header("ETag", "\"" + revision + "\"").body(body);
    }
}
