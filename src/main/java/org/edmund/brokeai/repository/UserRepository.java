package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<AppUser, Long> {
    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

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
}
