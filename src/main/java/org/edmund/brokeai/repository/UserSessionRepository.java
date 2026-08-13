package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.UserSession;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface UserSessionRepository extends JpaRepository<UserSession, UUID> {
    Page<UserSession> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);

    Optional<UserSession> findByIdAndUserId(UUID id, Long userId);

    Optional<UserSession> findByRefreshTokenHash(String refreshTokenHash);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now WHERE s.user.id = :userId AND s.revokedAt IS NULL")
    int revokeAll(@Param("userId") Long userId, @Param("now") Instant now);

    @Modifying
    @Query("UPDATE UserSession s SET s.revokedAt = :now " +
        "WHERE s.user.id = :userId AND s.revokedAt IS NULL AND (:currentId IS NULL OR s.id <> :currentId)")
    int revokeOthers(
        @Param("userId") Long userId,
        @Param("currentId") UUID currentId,
        @Param("now") Instant now
    );
}
