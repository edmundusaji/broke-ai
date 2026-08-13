package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.DataExportJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;
import java.time.Instant;
import java.util.List;

public interface DataExportJobRepository extends JpaRepository<DataExportJob, UUID> {
    Optional<DataExportJob> findByIdAndUserId(UUID id, Long userId);

    Optional<DataExportJob> findByUserIdAndIdempotencyKey(Long userId, String idempotencyKey);

    List<DataExportJob> findByStatusAndExpiresAtBefore(String status, Instant now);
}
