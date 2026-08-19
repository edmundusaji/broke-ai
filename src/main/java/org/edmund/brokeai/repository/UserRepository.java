package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Lock;
import jakarta.persistence.LockModeType;

import java.util.Optional;
import java.time.Instant;
import java.util.List;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByUsernameAndIdNot(String username, Long id);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCaseAndIdNot(String username, Long id);

    boolean existsByEmail(String email);

    boolean existsByEmailAndIdNot(String email, Long id);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AppUser u SET u.aiTrialCount = u.aiTrialCount - 1 " +
        "WHERE u.id = :userId AND u.isGuest = true AND u.aiTrialCount > 0")
    int consumeGuestAiTrial(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("UPDATE AppUser u SET u.aiTrialCount = u.aiTrialCount + 1 " +
        "WHERE u.id = :userId AND u.isGuest = true AND u.aiTrialCount < 2")
    int restoreGuestAiTrial(@Param("userId") Long userId);

    List<AppUser> findByIsGuestTrueAndStatusAndGuestRetentionHoldFalseAndUpdatedAtBefore(
        String status,
        Instant cutoff
    );

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE u.id = :id")
    Optional<AppUser> findByIdForUpdate(@Param("id") Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT u FROM AppUser u WHERE LOWER(u.username) = LOWER(:username)")
    Optional<AppUser> findByUsernameForUpdate(@Param("username") String username);
}
