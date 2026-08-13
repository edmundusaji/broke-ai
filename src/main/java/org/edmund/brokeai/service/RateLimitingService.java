package org.edmund.brokeai.service;

public interface RateLimitingService {
    boolean tryConsume(Long userId);
    boolean tryConsumeAuth(String clientIp);

    boolean tryConsumeSensitive(String clientIp, String operation);
}
