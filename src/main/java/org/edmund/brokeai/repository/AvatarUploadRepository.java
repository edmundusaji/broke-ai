package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.AvatarUpload;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;
import java.util.Optional;

public interface AvatarUploadRepository extends JpaRepository<AvatarUpload, UUID> {
    Optional<AvatarUpload> findByObjectKey(String objectKey);
}
