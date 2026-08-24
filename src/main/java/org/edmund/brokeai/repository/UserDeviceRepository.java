package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;

import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    Optional<UserDevice> findByIdAndUserId(UUID id, Long userId);

    Optional<UserDevice> findByPushTokenHash(String pushTokenHash);

    @EntityGraph(attributePaths = "user")
    Optional<UserDevice> findByNotificationCaptureTokenHashAndNotificationCaptureRevokedAtIsNull(
        String notificationCaptureTokenHash
    );

    @Modifying
    @Query("UPDATE UserDevice d SET d.notificationCaptureTokenHash = null, " +
        "d.notificationCaptureRevokedAt = :now, d.updatedAt = :now " +
        "WHERE d.user.id = :userId AND d.notificationCaptureTokenHash IS NOT NULL")
    int revokeNotificationCaptureForUser(@Param("userId") Long userId, @Param("now") Instant now);
}
