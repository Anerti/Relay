package com.techindna.relay.service.redis;

import com.techindna.relay.service.enums.VerificationFlow;
import java.time.Duration;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VerificationTokenService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    private static final String KEY_PREFIX = "auth:verification:";

    private final StringRedisTemplate redisTemplate;

    public String createForUser(UUID userId, VerificationFlow flow) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, flow.name() + ":" + userId, TOKEN_TTL);
        return token;
    }

    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }
}
