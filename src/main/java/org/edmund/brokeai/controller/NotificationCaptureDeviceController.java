package org.edmund.brokeai.controller;

import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.dto.NotificationCaptureDeviceRequest;
import org.edmund.brokeai.dto.NotificationCaptureDeviceResponse;
import org.edmund.brokeai.service.NotificationCaptureDeviceService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/me/notification-capture-devices")
@SecurityRequirement(name = "Bearer Authentication")
@RequiredArgsConstructor
public class NotificationCaptureDeviceController {
    private final NotificationCaptureDeviceService notificationCaptureDeviceService;

    @PostMapping
    public NotificationCaptureDeviceResponse provision(
        @Valid @RequestBody NotificationCaptureDeviceRequest request
    ) {
        return notificationCaptureDeviceService.provision(request);
    }

    @DeleteMapping("/{deviceId}")
    public ResponseEntity<Void> revoke(@PathVariable UUID deviceId) {
        notificationCaptureDeviceService.revoke(deviceId);
        return ResponseEntity.noContent().build();
    }
}

