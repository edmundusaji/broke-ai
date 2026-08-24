package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.NotificationCaptureDeviceRequest;
import org.edmund.brokeai.dto.NotificationCaptureDeviceResponse;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.UserDevice;
import org.edmund.brokeai.repository.UserDeviceRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.service.UserSyncService;
import org.edmund.brokeai.service.serviceimpl.NotificationCaptureDeviceServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationCaptureDeviceServiceImplTest {
    @InjectMocks
    private NotificationCaptureDeviceServiceImpl service;

    @Mock
    private CurrentUserService currentUserService;

    @Mock
    private UserDeviceRepository userDeviceRepository;

    @Mock
    private UserSyncService userSyncService;

    private AppUser user;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "enabled", true);
        user = new AppUser();
        user.setId(42L);
        user.setIsGuest(false);
        user.setStatus("active");
        when(currentUserService.getCurrentUser()).thenReturn(user);
    }

    @Test
    void provision_ReturnsPlaintextOnceAndPersistsOnlyHash() {
        UUID deviceId = UUID.randomUUID();
        when(userDeviceRepository.saveAndFlush(any(UserDevice.class))).thenAnswer(invocation -> {
            UserDevice device = invocation.getArgument(0);
            device.setId(deviceId);
            return device;
        });

        NotificationCaptureDeviceResponse response = service.provision(
            new NotificationCaptureDeviceRequest(null, "android", "Pixel 9", "2.0.0")
        );

        assertEquals(deviceId, response.deviceId());
        assertTrue(response.captureCredential().startsWith("bcap_"));
        assertNotNull(response.enabledAt());
        verify(userSyncService).markChanged(user.getId());
    }

    @Test
    void provision_PersistsHashDifferentFromReturnedCredential() {
        UUID deviceId = UUID.randomUUID();
        org.mockito.ArgumentCaptor<UserDevice> captor = org.mockito.ArgumentCaptor.forClass(UserDevice.class);
        when(userDeviceRepository.saveAndFlush(any(UserDevice.class))).thenAnswer(invocation -> {
            UserDevice device = invocation.getArgument(0);
            device.setId(deviceId);
            return device;
        });

        NotificationCaptureDeviceResponse response = service.provision(
            new NotificationCaptureDeviceRequest(null, "android", "Pixel", "2.0")
        );

        verify(userDeviceRepository).saveAndFlush(captor.capture());
        assertNotNull(captor.getValue().getNotificationCaptureTokenHash());
        assertNotEquals(response.captureCredential(), captor.getValue().getNotificationCaptureTokenHash());
        assertFalse(captor.getValue().getNotificationCaptureTokenHash().contains("bcap_"));
    }

    @Test
    void revoke_ClearsHashAndMarksRevoked() {
        UUID deviceId = UUID.randomUUID();
        UserDevice device = new UserDevice();
        device.setId(deviceId);
        device.setUser(user);
        device.setPlatform("android");
        device.setNotificationCaptureTokenHash("hash");
        when(userDeviceRepository.findByIdAndUserId(deviceId, user.getId())).thenReturn(Optional.of(device));

        service.revoke(deviceId);

        assertNull(device.getNotificationCaptureTokenHash());
        assertNotNull(device.getNotificationCaptureRevokedAt());
        verify(userDeviceRepository).save(device);
    }
}
