package com.fluxcore.approval.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.script.DefaultRedisScript;

@Configuration
public class RedisLockConfig {
    @Bean
    DefaultRedisScript<Long> unlockScript() {
        return new DefaultRedisScript<>("""
                if redis.call('get', KEYS[1]) == ARGV[1] then
                    return redis.call('del', KEYS[1])
                else
                    return 0
                end
                """, Long.class);
    }
}
