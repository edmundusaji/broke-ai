package org.edmund.brokeai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record NotificationCaptureDeviceRequest(
    UUID deviceId,
    @NotBlank String platform,
    @Size(max = 100) String deviceName,
    @Size(max = 30) String appVersion
) {
}

