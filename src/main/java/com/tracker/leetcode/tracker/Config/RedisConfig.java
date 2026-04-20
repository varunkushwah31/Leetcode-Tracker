package com.tracker.leetcode.tracker.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import com.fasterxml.jackson.databind.jsontype.PolymorphicTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.SerializationException;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

    @Bean
    public ObjectMapper redisObjectMapper() {
        // Jackson 2 API: Standard ObjectMapper with JSR310 support for Java 8+ date/time
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule()); // Handles Dates and Instants safely

        // Enable default typing for polymorphic type handling
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        mapper.activateDefaultTyping(ptv, com.fasterxml.jackson.databind.ObjectMapper.DefaultTyping.NON_FINAL);

        return mapper;
    }

    /**
     * Custom Adapter: Bridges Spring Data Redis with Jackson 2
     */
    public static class Jackson2RedisSerializer implements RedisSerializer<Object> {
        private final ObjectMapper mapper;

        public Jackson2RedisSerializer(ObjectMapper mapper) {
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
                        new Jackson2RedisSerializer(redisObjectMapper) // <-- Using our custom Jackson 2 Serializer!
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

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper redisObjectMapper) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // 1. Serialize Keys as plain Strings
        StringRedisSerializer stringSerializer = new StringRedisSerializer();
        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        // 2. Serialize Values using your custom Jackson 2 Serializer
        Jackson2RedisSerializer valueSerializer = new Jackson2RedisSerializer(redisObjectMapper);
        template.setValueSerializer(valueSerializer);
        template.setHashValueSerializer(valueSerializer);

        template.afterPropertiesSet();
        return template;
    }
}
