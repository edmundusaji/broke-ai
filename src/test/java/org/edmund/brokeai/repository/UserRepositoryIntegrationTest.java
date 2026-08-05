package org.edmund.brokeai.repository;

import org.edmund.brokeai.entity.AppUser;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class UserRepositoryIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void guestAiTrialUpdates_AreAtomicAndBounded() {
        AppUser guest = new AppUser();
        guest.setFullName("Guest User");
        guest.setUsername("guest_repository_test");
        guest.setIsGuest(true);
        guest.setAiTrialCount(2);
        guest = userRepository.saveAndFlush(guest);

        assertEquals(1, userRepository.consumeGuestAiTrial(guest.getId()));
        assertEquals(1, userRepository.consumeGuestAiTrial(guest.getId()));
        assertEquals(0, userRepository.consumeGuestAiTrial(guest.getId()));
        assertEquals(0, userRepository.findById(guest.getId()).orElseThrow().getAiTrialCount());

        assertEquals(1, userRepository.restoreGuestAiTrial(guest.getId()));
        assertEquals(1, userRepository.findById(guest.getId()).orElseThrow().getAiTrialCount());
    }

    @Test
    void registeredUser_CannotConsumeGuestTrials() {
        AppUser user = new AppUser();
        user.setFullName("Registered User");
        user.setUsername("registered_repository_test");
        user.setEmail("registered@example.com");
        user.setPassword("encoded-password");
        user.setIsGuest(false);
        user.setAiTrialCount(0);
        user = userRepository.saveAndFlush(user);

        assertEquals(0, userRepository.consumeGuestAiTrial(user.getId()));
    }
}
