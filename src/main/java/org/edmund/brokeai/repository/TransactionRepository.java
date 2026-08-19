package org.edmund.brokeai.repository;

import org.edmund.brokeai.dto.CategorySummaryDTO;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.time.Instant;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    @Query("SELECT new org.edmund.brokeai.dto.CategorySummaryDTO(COALESCE(t.category, 'Other'), SUM(t.amount)) " +
        "FROM Transaction t " +
        "WHERE t.user = :user AND t.deletedAt IS NULL AND t.date >= :startDate AND t.date <= :endDate " +
        "GROUP BY COALESCE(t.category, 'Other') " +
        "ORDER BY SUM(t.amount) DESC")
    List<CategorySummaryDTO> getExpenseSummaryByUserAndDateRange(
        @Param("user") AppUser user,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );

    List<Transaction> findByUserAndDeletedAtIsNullAndDateBetweenOrderByDateDesc(
        AppUser user,
        LocalDateTime startDate,
        LocalDateTime endDate
    );

    default List<Transaction> findByUserAndDateBetweenOrderByDateDesc(
        AppUser user,
        LocalDateTime startDate,
        LocalDateTime endDate
    ) {
        return findByUserAndDeletedAtIsNullAndDateBetweenOrderByDateDesc(user, startDate, endDate);
    }

    List<Transaction> findTop5ByUserAndDeletedAtIsNullOrderByDateDesc(AppUser user);

    default List<Transaction> findTop5ByUserOrderByDateDesc(AppUser user) {
        return findTop5ByUserAndDeletedAtIsNullOrderByDateDesc(user);
    }

    Optional<Transaction> findByIdAndUserAndDeletedAtIsNull(Long id, AppUser user);

    List<Transaction> findByUserIdAndDeletedAtIsNullOrderByDateDesc(Long userId);

    default Optional<Transaction> findByIdAndUser(Long id, AppUser user) {
        return findByIdAndUserAndDeletedAtIsNull(id, user);
    }

    @Modifying
    @Query("UPDATE Transaction t SET t.deletedAt = :deletedAt, t.updatedAt = :deletedAt, " +
        "t.revision = t.revision + 1 WHERE t.user.id = :userId AND t.deletedAt IS NULL")
    int softDeleteAllByUserId(@Param("userId") Long userId, @Param("deletedAt") Instant deletedAt);

    long countByUserIdAndDeletedAtIsNull(Long userId);

    @Query("SELECT MIN(t.date) FROM Transaction t WHERE t.user.id = :userId AND t.deletedAt IS NULL")
    Optional<LocalDateTime> findFirstTransactionAt(@Param("userId") Long userId);

    @Query("SELECT MAX(t.date) FROM Transaction t WHERE t.user.id = :userId AND t.deletedAt IS NULL")
    Optional<LocalDateTime> findLastTransactionAt(@Param("userId") Long userId);
}
