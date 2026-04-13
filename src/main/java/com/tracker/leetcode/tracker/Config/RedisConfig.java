package com.tracker.leetcode.tracker.Config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

// STRICTLY Jackson 3 Imports (Removed the problematic annotation import)
import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;
import tools.jackson.datatype.jsr310.JavaTimeModule;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        // JACKSON 3 API: You must use the Builder pattern to construct the immutable mapper
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();

        return JsonMapper.builder()
                .addModule(new JavaTimeModule()) // Handles Dates and Instants safely
                // NEW: Jackson 3 allows us to drop JsonTypeInfo completely for standard polymorphic typing!
                .activateDefaultTyping(ptv, DefaultTyping.NON_FINAL)
                .build();
    }

    /**
     * Custom Adapter: Bridges Spring Data Redis with Jackson 3 (tools.jackson)
     * This entirely bypasses Spring's hardcoded Jackson 2 requirement.
     */
    public static class Jackson3RedisSerializer implements RedisSerializer<Object> {
        private final ObjectMapper mapper;

        public Jackson3RedisSerializer(ObjectMapper mapper) {
            this.mapper = mapper;
        }

        @Override
        public byte[] serialize(Object source) throws SerializationException {
            if (source == null) return new byte[0];
            try {
                return mapper.writeValueAsBytes(source);
            } catch (Exception e) {
                throw new SerializationException("Could not serialize object to JSON", e);
            }
        }

        @Override
        public Object deserialize(byte[] source) throws SerializationException {
            if (source == null || source.length == 0) return null;
            try {
                return mapper.readValue(source, Object.class);
            } catch (Exception e) {
                throw new SerializationException("Could not deserialize object from JSON", e);
            }
        }
    }

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {

        // Default cache configuration (10 minutes)
        RedisCacheConfiguration defaultCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson3RedisSerializer(redisObjectMapper) // <-- Using our custom Jackson 3 Serializer!
                ));

        // Define TTL configurations for specific caches
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();

        // Student data caches
        cacheConfigurations.put("student-progress", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("student-stats", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("student-recent", defaultCacheConfig.entryTtl(Duration.ofMinutes(30)));
        cacheConfigurations.put("student-profile", defaultCacheConfig.entryTtl(Duration.ofHours(1)));

        // Classroom data caches
        cacheConfigurations.put("classroom-dashboard", defaultCacheConfig.entryTtl(Duration.ofHours(1)));
        cacheConfigurations.put("classroom-analytics", defaultCacheConfig.entryTtl(Duration.ofHours(1)));

        // Mentor & Path caches
        cacheConfigurations.put("mentor", defaultCacheConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("mentors-all", defaultCacheConfig.entryTtl(Duration.ofHours(2)));
        cacheConfigurations.put("learning-paths-by-mentor", defaultCacheConfig.entryTtl(Duration.ofHours(3)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultCacheConfig)
                .withInitialCacheConfigurations(cacheConfigurations)
                .build();
    }
}