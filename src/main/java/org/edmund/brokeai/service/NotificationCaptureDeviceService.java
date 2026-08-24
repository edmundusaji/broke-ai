package org.edmund.brokeai.service;

import org.edmund.brokeai.dto.NotificationCaptureDeviceRequest;
import org.edmund.brokeai.dto.NotificationCaptureDeviceResponse;

import java.util.UUID;

public interface NotificationCaptureDeviceService {
    NotificationCaptureDeviceResponse provision(NotificationCaptureDeviceRequest request);

    void revoke(UUID deviceId);
}

