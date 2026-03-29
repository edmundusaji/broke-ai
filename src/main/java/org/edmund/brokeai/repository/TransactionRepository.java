package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByKategori(String kategori);
}
