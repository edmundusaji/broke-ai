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
}
