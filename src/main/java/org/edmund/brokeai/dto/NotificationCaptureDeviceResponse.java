package org.edmund.brokeai.dto;

import java.time.Instant;
import java.util.UUID;

public record NotificationCaptureDeviceResponse(
    UUID deviceId,
    String captureCredential,
    Instant enabledAt
) {
}

