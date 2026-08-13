package org.edmund.brokeai.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.time.LocalTime;
import java.util.Map;
import java.util.UUID;

public final class ProfileSettingsApi {

    private ProfileSettingsApi() {
    }

    public record Profile(
        Long id,
        String fullName,
        String username,
        String email,
        String pendingEmail,
        Instant emailVerifiedAt,
        String phone,
        String avatarUrl,
        String status,
        long revision,
        Instant createdAt,
        Instant updatedAt
    ) {
    }

    public record ProfileUpdateRequest(
        @Size(min = 1, max = 100) String fullName,
        @Size(min = 3, max = 30) String username,
        @Pattern(regexp = "^\\+[1-9][0-9]{7,14}$") String phone
    ) {
    }

    public record UsernameAvailability(String username, boolean available) {
    }

    public record AvatarUploadRequest(
        @NotBlank String contentType,
        @NotNull @Min(1) @Max(5_242_880) Long sizeBytes
    ) {
    }

    public record AvatarUpload(
        UUID uploadId,
        String uploadUrl,
        Instant expiresAt,
        Map<String, String> requiredHeaders
    ) {
    }

    public record AvatarContent(String contentType, byte[] content) {
    }

    public record EmailChangeRequest(
        @NotBlank @Email @Size(max = 254) String newEmail,
        @NotBlank @Size(max = 1024) String currentPassword
    ) {
    }

    public record EmailChange(
        String pendingEmail,
        Instant expiresAt
    ) {
    }

    public record EmailVerificationRequest(@NotBlank @Size(min = 32) String verificationToken) {
    }

    public record PasswordChangeRequest(
        @NotBlank @Size(max = 1024) String currentPassword,
        @NotBlank @Size(min = 15, max = 128) String newPassword,
        Boolean retainCurrentSession
    ) {
    }

    public record PasswordChange(Instant changedAt, int revokedSessionCount) {
    }

    public record Preferences(
        String currencyCode,
        String languageCode,
        String regionCode,
        String timeZone,
        String themeMode,
        long revision,
        Instant updatedAt
    ) {
    }

    public record PreferencesUpdateRequest(
        @Pattern(regexp = "^[A-Z]{3}$") String currencyCode,
        @Size(max = 20) String languageCode,
        @Pattern(regexp = "^[A-Z]{2}$") String regionCode,
        @Size(max = 64) String timeZone,
        @Pattern(regexp = "^(light|dark|system)$") String themeMode
    ) {
    }

    public record NotificationPreferences(
        boolean spendingReminderEnabled,
        LocalTime reminderTime,
        boolean weeklySummaryEnabled,
        boolean monthlyReportEnabled,
        boolean securityAlertsEnabled,
        boolean productUpdatesEnabled,
        long revision,
        Instant updatedAt
    ) {
    }

    public record NotificationPreferencesUpdateRequest(
        Boolean spendingReminderEnabled,
        LocalTime reminderTime,
        Boolean weeklySummaryEnabled,
        Boolean monthlyReportEnabled,
        Boolean securityAlertsEnabled,
        Boolean productUpdatesEnabled
    ) {
    }

    public record PrivacyPreferences(
        boolean personalizedInsights,
        boolean anonymousAnalytics,
        String policyVersion,
        Instant consentedAt,
        UUID sourceDeviceId,
        String sourcePlatform,
        long revision,
        Instant updatedAt
    ) {
    }

    public record PrivacyPreferencesUpdateRequest(
        Boolean personalizedInsights,
        Boolean anonymousAnalytics,
        @NotBlank @Size(max = 30) String policyVersion,
        UUID sourceDeviceId,
        @Size(max = 20) String sourcePlatform
    ) {
    }

    public record SyncStatus(String status, Instant lastSyncedAt, long serverRevision) {
    }

    public record DeviceRegistrationRequest(
        UUID deviceId,
        @NotBlank @Pattern(regexp = "^(android|ios)$") String platform,
        @Size(max = 100) String deviceName,
        @NotBlank @Size(max = 4096) String pushToken,
        @NotBlank @Size(max = 30) String appVersion
    ) {
    }

    public record Device(
        UUID id,
        String platform,
        String deviceName,
        String appVersion,
        Instant lastSeenAt,
        Instant createdAt
    ) {
    }

    public record Session(
        UUID id,
        boolean current,
        UUID deviceId,
        String deviceName,
        String userAgent,
        Instant lastActiveAt,
        Instant expiresAt,
        Instant revokedAt,
        Instant createdAt
    ) {
    }

    public record DataExport(
        UUID jobId,
        String status,
        String format,
        Instant requestedAt,
        Instant completedAt,
        String downloadUrl,
        Instant expiresAt,
        String failureReason
    ) {
    }

    public record ClearTransactionsRequest(
        @NotBlank String confirmation,
        @NotBlank @Size(max = 1024) String currentPassword
    ) {
    }

    public record ClearTransactions(long deletedCount, Instant recoverableUntil) {
    }

    public record AccountDeletionRequest(
        @NotBlank String confirmation,
        @NotBlank @Size(max = 1024) String currentPassword
    ) {
    }

    public record AccountDeletion(
        UUID requestId,
        String status,
        Instant requestedAt,
        Instant scheduledFor
    ) {
    }

    public record SupportTicketRequest(
        @NotBlank @Pattern(regexp = "^(support|bug)$") String type,
        @NotBlank @Size(max = 160) String subject,
        @NotBlank @Size(max = 10_000) String message,
        @NotBlank @Size(max = 30) String appVersion,
        @NotBlank @Size(max = 20) String platform,
        @NotBlank @Size(max = 60) String osVersion,
        @NotBlank @Size(max = 100) String deviceModel,
        @NotBlank @Size(max = 35) String locale,
        @Size(max = 255) String currentRoute,
        UUID diagnosticAttachmentId,
        Map<String, Object> diagnosticMetadata
    ) {
    }

    public record SupportTicket(
        UUID id,
        String type,
        String subject,
        String message,
        String status,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt
    ) {
    }

    public record FaqArticle(
        UUID id,
        String locale,
        String category,
        String title,
        String body,
        int displayOrder
    ) {
    }
}
