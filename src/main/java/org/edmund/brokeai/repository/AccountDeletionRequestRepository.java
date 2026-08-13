package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.AccountDeletionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AccountDeletionRequestRepository extends JpaRepository<AccountDeletionRequest, UUID> {
    Optional<AccountDeletionRequest> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    Optional<AccountDeletionRequest> findFirstByUserIdAndStatusOrderByRequestedAtDesc(Long userId, String status);
}
