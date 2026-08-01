package com.adavis.mdm.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class CacheConfig extends CachingConfigurerSupport {

    @Bean
    public RedisCacheManager cacheManager(
            RedisConnectionFactory redisConnectionFactory,
            @Value("${mdm.cache.ttl-minutes:15}") long ttlMinutes) {
        ObjectMapper objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
        objectMapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        objectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY);

        RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(ttlMinutes))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer(objectMapper)));

        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(defaults)
                .build();
    }

        @Override
        @Bean
        public CacheErrorHandler errorHandler() {
                return new CacheErrorHandler() {
                        @Override
                        public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                                log.warn("Ignoring cache GET error for cache='{}', key='{}': {}", cache != null ? cache.getName() : "unknown", key, exception.getMessage());
                        }

                        @Override
                        public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                                log.warn("Ignoring cache PUT error for cache='{}', key='{}': {}", cache != null ? cache.getName() : "unknown", key, exception.getMessage());
                        }

                        @Override
                        public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                                log.warn("Ignoring cache EVICT error for cache='{}', key='{}': {}", cache != null ? cache.getName() : "unknown", key, exception.getMessage());
                        }

                        @Override
                        public void handleCacheClearError(RuntimeException exception, Cache cache) {
                                log.warn("Ignoring cache CLEAR error for cache='{}': {}", cache != null ? cache.getName() : "unknown", exception.getMessage());
                        }
                };
        }
}