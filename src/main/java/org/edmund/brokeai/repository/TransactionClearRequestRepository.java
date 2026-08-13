package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.TransactionClearRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface TransactionClearRequestRepository extends JpaRepository<TransactionClearRequest, UUID> {
    Optional<TransactionClearRequest> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);
}
