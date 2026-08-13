package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.UserDevice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserDeviceRepository extends JpaRepository<UserDevice, UUID> {
    Optional<UserDevice> findByIdAndUserId(UUID id, Long userId);

    Optional<UserDevice> findByPushTokenHash(String pushTokenHash);
}
