package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.EmailChangeRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface EmailChangeRequestRepository extends JpaRepository<EmailChangeRequest, UUID> {
    Optional<EmailChangeRequest> findFirstByUserIdAndVerifiedAtIsNullAndCancelledAtIsNullOrderByRequestedAtDesc(Long userId);

    Optional<EmailChangeRequest> findByVerificationTokenHashAndVerifiedAtIsNullAndCancelledAtIsNull(String tokenHash);
}
