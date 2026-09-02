package com.fluxcore.approval.service;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
public class RedisLockService {
    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> unlockScript;

    public RedisLockService(StringRedisTemplate redisTemplate, DefaultRedisScript<Long> unlockScript) {
        this.redisTemplate = redisTemplate;
        this.unlockScript = unlockScript;
    }

    public String tryLock(String key, Duration leaseTime) {
        String token = UUID.randomUUID().toString();
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(key, token, leaseTime);
        return Boolean.TRUE.equals(locked) ? token : null;
    }

    public void unlock(String key, String token) {
        if (token == null) return;
        redisTemplate.execute(unlockScript, Collections.singletonList(key), token);
    }
}
