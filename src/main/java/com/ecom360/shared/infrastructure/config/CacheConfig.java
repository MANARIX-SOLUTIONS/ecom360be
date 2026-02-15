package com.ecom360.shared.infrastructure.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache configuration with named caches and sensible TTLs.
 * For horizontal scaling, swap Caffeine for Redis (spring-boot-starter-data-redis).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager mgr = new CaffeineCacheManager();
        mgr.setCaffeine(Caffeine.newBuilder()
                .maximumSize(1_000)
                .expireAfterWrite(10, TimeUnit.MINUTES)
                .recordStats());
        // Pre-register named caches so they are discoverable via actuator
        mgr.setCacheNames(java.util.List.of(
                "plans",
                "featureFlags",
                "platformConfig",
                "categories",
                "stores"
        ));
        return mgr;
    }
}
