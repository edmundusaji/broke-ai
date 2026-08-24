package org.edmund.brokeai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.UUID;

public record NotificationIngestionRequest(
    @NotBlank @Size(max = 4000) String text,
    UUID captureId,
    String captureMode,
    @Size(max = 255) String sourcePackage,
    Instant notificationPostedAt
) {
    public boolean isAutomatic() {
        return "AUTOMATIC".equals(captureMode);
    }

    public boolean isLegacyManual() {
        return captureId == null && captureMode == null
            && sourcePackage == null && notificationPostedAt == null;
    }
}

