package com.zomato.backend.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis configuration — sets up:
 *
 * 1. {@link RedisTemplate}  — for manual Redis operations (Cart, location tracking)
 * 2. {@link RedisCacheManager} — for Spring Cache abstraction (@Cacheable / @CacheEvict)
 *
 * Serialization strategy:
 *   - Keys   → plain String  (human-readable in redis-cli)
 *   - Values → JSON via GenericJackson2JsonRedisSerializer
 *              (includes type info so deserialization works correctly)
 *
 * Cache names and TTLs (configurable via application.yml → app.cache.*):
 *   ┌──────────────────┬──────────────────────────────────────┐
 *   │ Cache name       │ TTL                                  │
 *   ├──────────────────┼──────────────────────────────────────┤
 *   │ restaurants      │ app.cache.restaurant-ttl-minutes     │
 *   │ menus            │ app.cache.menu-ttl-minutes           │
 *   │ reviews          │ app.cache.reviews-ttl-minutes        │
 *   └──────────────────┴──────────────────────────────────────┘
 */
@Configuration
@EnableCaching
@RequiredArgsConstructor
public class RedisConfig {

    private final AppProperties appProperties;

    // ── ObjectMapper ──────────────────────────────────────────────────────────

    /**
     * Dedicated ObjectMapper for Redis serialization.
     *
     * Why a separate ObjectMapper (not the app-wide one)?
     * We need activateDefaultTyping() for Redis — it embeds type info
     * in the JSON so GenericJackson2JsonRedisSerializer can reconstruct
     * the correct class on deserialization. This behaviour is NOT wanted
     * for the HTTP response ObjectMapper.
     */
    @Bean(name = "redisObjectMapper")
    public ObjectMapper redisObjectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        // Embed class type info so deserialization works for any cached object
        mapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return mapper;
    }

    // ── RedisTemplate ─────────────────────────────────────────────────────────

    /**
     * General-purpose RedisTemplate used for Cart and location tracking
     * (manual Redis operations — not via @Cacheable).
     *
     * Keys   → String serializer  (e.g. "cart:42")
     * Values → JSON serializer    (human-readable, debuggable)
     */
    @Bean
    public RedisTemplate<String, Object> redisTemplate(
            RedisConnectionFactory connectionFactory
    ) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());
        StringRedisSerializer stringSerializer = new StringRedisSerializer();

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);
        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);
        template.afterPropertiesSet();

        return template;
    }

    // ── RedisCacheManager ─────────────────────────────────────────────────────

    /**
     * Spring Cache abstraction backed by Redis.
     *
     * Each cache name gets its own TTL. The default configuration
     * applies to any cache not explicitly listed.
     */
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer(redisObjectMapper());

        // Base configuration shared by all caches
        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer())
                )
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jsonSerializer)
                )
                .disableCachingNullValues();   // never cache null — avoids stale 404s

        // Per-cache TTL overrides
        Map<String, RedisCacheConfiguration> cacheConfigs = new HashMap<>();

        cacheConfigs.put("restaurants",
                defaultConfig.entryTtl(
                        Duration.ofMinutes(appProperties.getCache().getRestaurantTtlMinutes())
                ));

        cacheConfigs.put("menus",
                defaultConfig.entryTtl(
                        Duration.ofMinutes(appProperties.getCache().getMenuTtlMinutes())
                ));

        cacheConfigs.put("reviews",
                defaultConfig.entryTtl(
                        Duration.ofMinutes(appProperties.getCache().getReviewsTtlMinutes())
                ));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig.entryTtl(Duration.ofMinutes(10)))
                .withInitialCacheConfigurations(cacheConfigs)
                .build();
    }
}
