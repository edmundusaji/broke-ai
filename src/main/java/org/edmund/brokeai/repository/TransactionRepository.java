package org.edmund.brokeai.repository;

import org.edmund.brokeai.dto.CategorySummaryDTO;
import org.edmund.brokeai.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    // Pengeluaran berdasarkan kategori dalam rentang waktu tertentu
    @Query("SELECT new org.edmund.brokeai.dto.CategorySummaryDTO(COALESCE(t.kategori, 'Lain-lain'), SUM(t.jumlah)) " +
        "FROM Transaction t " +
        "WHERE t.tanggal >= :startDate AND t.tanggal <= :endDate " +
        "GROUP BY COALESCE(t.kategori, 'Lain-lain') " +
        "ORDER BY SUM(t.jumlah) DESC")
    List<CategorySummaryDTO>  getExpenseSummaryByDateRange(@Param("startDate")LocalDateTime startDate, @Param("endDate") LocalDateTime endDate);

    List<Transaction> findByKategori(String kategori);
}
