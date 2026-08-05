package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.dto.RegisterRequest;
import org.edmund.brokeai.entity.AppUser;
import org.edmund.brokeai.entity.Transaction;
import org.edmund.brokeai.repository.TransactionRepository;
import org.edmund.brokeai.repository.UserRepository;
import org.edmund.brokeai.security.CurrentUserService;
import org.edmund.brokeai.security.JwtService;
import org.edmund.brokeai.service.serviceimpl.AuthServiceImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class GuestRegistrationIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_AuthenticatedGuest_UpdatesExistingRowAndPreservesTransactions() {
        AppUser guest = new AppUser();
        guest.setFullName("Guest User");
        guest.setUsername("guest_registration_test");
        guest.setIsGuest(true);
        guest.setAiTrialCount(1);
        guest = userRepository.saveAndFlush(guest);

        Transaction transaction = new Transaction();
        transaction.setPaymentMethod("Existing Payment Method");
        transaction.setDescription("Existing transaction");
        transaction.setAmount(25000.0);
        transaction.setDate(LocalDateTime.of(2026, 4, 1, 10, 0));
        transaction.setUser(guest);
        transaction = transactionRepository.saveAndFlush(transaction);

        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(guest, null, List.of())
        );
        PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        AuthServiceImpl authService = new AuthServiceImpl(
            userRepository,
            passwordEncoder,
            mock(JwtService.class),
            new CurrentUserService()
        );

        Long guestId = guest.getId();
        Long transactionId = transaction.getId();
        authService.register(new RegisterRequest("Edmundus Aji", "edmundus", "aji@mail.com", "password123"));
        userRepository.flush();

        AppUser registeredUser = userRepository.findById(guestId).orElseThrow();
        Transaction preservedTransaction = transactionRepository.findById(transactionId).orElseThrow();
        assertEquals(1, userRepository.count());
        assertEquals(guestId, registeredUser.getId());
        assertEquals("edmundus", registeredUser.getUsername());
        assertEquals("aji@mail.com", registeredUser.getEmail());
        assertEquals("encoded-password", registeredUser.getPassword());
        assertFalse(registeredUser.getIsGuest());
        assertEquals(0, registeredUser.getAiTrialCount());
        assertEquals(guestId, preservedTransaction.getUser().getId());
        assertSame(registeredUser, preservedTransaction.getUser());
    }
}
