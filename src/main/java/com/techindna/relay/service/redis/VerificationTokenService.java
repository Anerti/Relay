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
    private static final String VALUE_SEPARATOR = ":";

    private final StringRedisTemplate redisTemplate;

    public String createForUser(UUID userId, VerificationFlow flow) {
        String token = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(KEY_PREFIX + token, flow.name() + VALUE_SEPARATOR + userId, TOKEN_TTL);
        return token;
    }

    public void delete(String token) {
        redisTemplate.delete(KEY_PREFIX + token);
    }

    public UUID consume(String token, VerificationFlow flow) {
        String value = redisTemplate.opsForValue().getAndDelete(KEY_PREFIX + token);
        String prefix = flow.name() + VALUE_SEPARATOR;
        return value != null && value.startsWith(prefix) ? UUID.fromString(value.substring(prefix.length())) : null;
    }
}
