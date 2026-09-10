package ru.korteng.finance_manager.security;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Service
@RequiredArgsConstructor
public class TokenBlacklistService {

    private final RedisTemplate<String, String> redisTemplate;

    private static final String PREFIX = "blacklist:token:";

    public void blacklist(String token, long remainingMs) {
        if (remainingMs <= 0) {
            return; // токен и так уже истёк, нет смысла хранить
        }
        redisTemplate.opsForValue().set(PREFIX + token, "revoked", Duration.ofMillis(remainingMs));
    }

    public boolean isBlacklisted(String token) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(PREFIX + token));
    }
}