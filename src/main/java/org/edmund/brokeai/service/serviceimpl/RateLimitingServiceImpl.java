package org.edmund.brokeai.service.serviceimpl;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import org.edmund.brokeai.service.RateLimitingService;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class RateLimitingServiceImpl implements RateLimitingService {

    private final ConcurrentMap<Long, Bucket> buckets = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Bucket> authBuckets = new ConcurrentHashMap<>();

    @Override
    public boolean tryConsume(Long userId) {
        return buckets.computeIfAbsent(userId, ignored -> newAiBucket()).tryConsume(1);
    }

    @Override
    public boolean tryConsumeAuth(String clientIp) {
        return authBuckets.computeIfAbsent(clientIp, ignored -> newAuthBucket()).tryConsume(1);
    }

    private Bucket newAiBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(10)
            .refillGreedy(10, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }

    private Bucket newAuthBucket() {
        Bandwidth limit = Bandwidth.builder()
            .capacity(5)
            .refillGreedy(5, Duration.ofMinutes(1))
            .build();
        return Bucket.builder()
            .addLimit(limit)
            .build();
    }
}
