package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class TransactionRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Test
    void findTop5ByUserOrderByTanggalDesc_ReturnsOnlyLatestFiveForRequestedUser() {
        AppUser user = saveUser("recent_user");
        AppUser anotherUser = saveUser("another_recent_user");

        for (int day = 1; day <= 7; day++) {
            saveTransaction(user, "Merchant " + day, LocalDateTime.of(2026, 4, day, 10, 0));
        }
        saveTransaction(anotherUser, "Other User Merchant", LocalDateTime.of(2026, 5, 1, 10, 0));
        transactionRepository.flush();

        List<Transaction> result = transactionRepository.findTop5ByUserOrderByTanggalDesc(user);

        assertEquals(5, result.size());
        assertEquals(List.of("Merchant 7", "Merchant 6", "Merchant 5", "Merchant 4", "Merchant 3"),
            result.stream().map(Transaction::getMerchant).toList());
    }

    private AppUser saveUser(String username) {
        AppUser user = new AppUser();
        user.setNamaLengkap(username);
        user.setUsername(username);
        user.setIsGuest(false);
        user.setAiTrialCount(0);
        return userRepository.saveAndFlush(user);
    }

    private void saveTransaction(AppUser user, String merchant, LocalDateTime date) {
        Transaction transaction = new Transaction();
        transaction.setUser(user);
        transaction.setMerchant(merchant);
        transaction.setJumlah(1000.0);
        transaction.setTanggal(date);
        transactionRepository.save(transaction);
    }
}
