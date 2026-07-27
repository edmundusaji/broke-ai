package org.edmund.brokeai.service;

public interface RateLimitingService {
    boolean tryConsume(Long userId);
}
