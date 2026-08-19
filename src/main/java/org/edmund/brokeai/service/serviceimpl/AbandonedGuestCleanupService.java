package org.edmund.brokeai.service.serviceimpl;

import lombok.RequiredArgsConstructor;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.repository.UserSessionRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AbandonedGuestCleanupService {
    private final UserRepository userRepository;
    private final UserSessionRepository userSessionRepository;
    private final GuestDataPurgeService purgeService;

    @Value("${app.guest-cleanup.retention-days:90}")
    private long retentionDays;

    @Scheduled(cron = "${app.guest-cleanup.cron:0 30 3 * * *}")
    @Transactional
    public void removeAbandonedGuests() {
        Instant now = Instant.now();
        Instant cutoff = now.minus(Duration.ofDays(retentionDays));
        List<AppUser> candidates = userRepository
            .findByIsGuestTrueAndStatusAndGuestRetentionHoldFalseAndUpdatedAtBefore("active", cutoff);
        for (AppUser guest : candidates) {
            if (userSessionRepository.existsByUserIdAndExpiresAtAfter(guest.getId(), now)) {
                continue;
            }
            if (userSessionRepository.existsByUserIdAndLastActiveAtAfter(guest.getId(), cutoff)) {
                continue;
            }
            purgeService.hardDeleteGuest(guest);
        }
    }
}
