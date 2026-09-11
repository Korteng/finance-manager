package ru.korteng.finance_manager.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBlacklistServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private TokenBlacklistService tokenBlacklistService;

    @Test
    void blacklist_PositiveRemainingMs_SetsKeyWithPrefixAndCorrectTtl() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        tokenBlacklistService.blacklist("tok123", 5000L);

        verify(valueOperations).set("blacklist:token:tok123", "revoked", Duration.ofMillis(5000L));
    }

    @Test
    void blacklist_ZeroRemainingMs_DoesNotTouchRedis() {
        tokenBlacklistService.blacklist("tok123", 0L);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void blacklist_NegativeRemainingMs_DoesNotTouchRedis() {
        tokenBlacklistService.blacklist("tok123", -100L);

        verifyNoInteractions(redisTemplate);
    }

    @Test
    void isBlacklisted_KeyExists_ReturnsTrue() {
        when(redisTemplate.hasKey("blacklist:token:tok123")).thenReturn(true);

        assertTrue(tokenBlacklistService.isBlacklisted("tok123"));
    }

    @Test
    void isBlacklisted_KeyDoesNotExist_ReturnsFalse() {
        when(redisTemplate.hasKey("blacklist:token:tok123")).thenReturn(false);

        assertFalse(tokenBlacklistService.isBlacklisted("tok123"));
    }

    @Test
    void isBlacklisted_RedisReturnsNull_ReturnsFalseNotNpe() {
        when(redisTemplate.hasKey("blacklist:token:tok123")).thenReturn(null);

        assertFalse(tokenBlacklistService.isBlacklisted("tok123"));
    }
}