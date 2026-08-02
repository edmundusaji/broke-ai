package org.edmund.brokeai.serviceImpl;

import org.edmund.brokeai.service.serviceimpl.RateLimitingServiceImpl;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitingServiceImplTests {

    @Test
    void tryConsume_BlocksEleventhRequestWithinMinute_Test() {
        RateLimitingServiceImpl rateLimitingService = new RateLimitingServiceImpl();

        for (int i = 0; i < 10; i++) {
            assertTrue(rateLimitingService.tryConsume(1L));
        }

        assertFalse(rateLimitingService.tryConsume(1L));
    }

    @Test
    void tryConsumeAuth_BlocksSixthRequestWithinMinute_PerIp() {
        RateLimitingServiceImpl rateLimitingService = new RateLimitingServiceImpl();

        for (int attempt = 0; attempt < 5; attempt++) {
            assertTrue(rateLimitingService.tryConsumeAuth("203.0.113.10"));
        }

        assertFalse(rateLimitingService.tryConsumeAuth("203.0.113.10"));
        assertTrue(rateLimitingService.tryConsumeAuth("203.0.113.11"));
    }
}
