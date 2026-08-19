package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.edmund.brokeai.service.serviceimpl.AbandonedGuestCleanupService;
import org.edmund.brokeai.service.serviceimpl.GuestDataPurgeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AbandonedGuestCleanupServiceTest {
    @Mock private UserRepository userRepository;
    @Mock private UserSessionRepository userSessionRepository;
    @Mock private GuestDataPurgeService purgeService;

    @Test
    void removesOnlyInactiveGuestsWhoseSessionsHaveAllExpired() {
        AppUser abandoned = guest(1L);
        AppUser liveSession = guest(2L);
        AppUser recentActivity = guest(3L);
        when(userRepository.findByIsGuestTrueAndStatusAndGuestRetentionHoldFalseAndUpdatedAtBefore(
            eq("active"), any(Instant.class)
        )).thenReturn(List.of(abandoned, liveSession, recentActivity));
        doReturn(true).when(userSessionRepository)
            .existsByUserIdAndExpiresAtAfter(eq(2L), any(Instant.class));
        doReturn(true).when(userSessionRepository)
            .existsByUserIdAndLastActiveAtAfter(eq(3L), any(Instant.class));

        AbandonedGuestCleanupService service = new AbandonedGuestCleanupService(
            userRepository, userSessionRepository, purgeService
        );
        ReflectionTestUtils.setField(service, "retentionDays", 90L);
        service.removeAbandonedGuests();

        verify(purgeService).hardDeleteGuest(abandoned);
        verify(purgeService, never()).hardDeleteGuest(liveSession);
        verify(purgeService, never()).hardDeleteGuest(recentActivity);
    }

    private AppUser guest(Long id) {
        AppUser user = new AppUser();
        user.setId(id);
        user.setIsGuest(true);
        return user;
    }
}
