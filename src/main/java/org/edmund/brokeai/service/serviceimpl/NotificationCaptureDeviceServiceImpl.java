package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.NotificationCaptureDeviceRequest;
import org.edmund.brokeai.dto.NotificationCaptureDeviceResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.UserDevice;
import org.edmund.brokeai.exception.ApiException;
import org.edmund.brokeai.repository.UserDeviceRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.NotificationCaptureDeviceService;
import org.edmund.brokeai.service.UserSyncService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class NotificationCaptureDeviceServiceImpl implements NotificationCaptureDeviceService {
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final CurrentUserService currentUserService;
    private final UserDeviceRepository userDeviceRepository;
    private final UserSyncService userSyncService;

    @Value("${app.notification-capture.enabled:false}")
    private boolean enabled;

    @Override
    @Transactional
    public NotificationCaptureDeviceResponse provision(NotificationCaptureDeviceRequest request) {
        requireFeatureEnabled();
        if (request == null || !"android".equals(request.platform())) {
            throw new ApiException(
                HttpStatus.BAD_REQUEST,
                "CAPTURE_PLATFORM_UNSUPPORTED",
                "Automatic notification capture is available only for Android devices.",
                "platform"
            );
        }

        AppUser user = currentUserService.getCurrentUser();
        if (Boolean.TRUE.equals(user.getIsGuest()) || !"active".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "CAPTURE_NOT_ELIGIBLE",
                "Automatic notification capture requires an active registered account.");
        }

        UserDevice device = request.deviceId() == null
            ? new UserDevice()
            : userDeviceRepository.findByIdAndUserId(request.deviceId(), user.getId())
                .orElseThrow(() -> new ApiException(
                    HttpStatus.NOT_FOUND, "CAPTURE_DEVICE_NOT_FOUND", "The capture device was not found."
                ));

        if (device.getId() != null && !"android".equals(device.getPlatform())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "CAPTURE_PLATFORM_UNSUPPORTED",
                "Only Android devices can be provisioned for notification capture.", "deviceId");
        }

        String plaintextCredential = newCredential();
        Instant now = Instant.now();
        device.setUser(user);
        device.setPlatform("android");
        device.setDeviceName(trimToNull(request.deviceName()));
        device.setAppVersion(trimToNull(request.appVersion()));
        device.setNotificationCaptureTokenHash(ServiceSupport.sha256(plaintextCredential));
        device.setNotificationCaptureEnabledAt(now);
        device.setNotificationCaptureRevokedAt(null);
        device.setNotificationCaptureLastUsedAt(null);
        device.setLastSeenAt(now);
        device.setUpdatedAt(now);
        UserDevice saved = userDeviceRepository.saveAndFlush(device);
        userSyncService.markChanged(user.getId());

        return new NotificationCaptureDeviceResponse(saved.getId(), plaintextCredential, now);
    }

    @Override
    @Transactional
    public void revoke(UUID deviceId) {
        AppUser user = currentUserService.getCurrentUser();
        UserDevice device = userDeviceRepository.findByIdAndUserId(deviceId, user.getId())
            .orElseThrow(() -> new ApiException(
                HttpStatus.NOT_FOUND, "CAPTURE_DEVICE_NOT_FOUND", "The capture device was not found."
            ));
        Instant now = Instant.now();
        device.setNotificationCaptureTokenHash(null);
        device.setNotificationCaptureRevokedAt(now);
        device.setUpdatedAt(now);
        userDeviceRepository.save(device);
        userSyncService.markChanged(user.getId());
    }

    private void requireFeatureEnabled() {
        if (!enabled) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "CAPTURE_FEATURE_DISABLED",
                "Automatic notification capture is not enabled on this server.");
        }
    }

    private String newCredential() {
        byte[] random = new byte[32];
        SECURE_RANDOM.nextBytes(random);
        return "bcap_" + Base64.getUrlEncoder().withoutPadding().encodeToString(random);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim();
    }
}

