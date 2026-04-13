package com.lifetex.sign.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.Map;

@Configuration
@EnableCaching
public class RedisConfig {

        @Bean
        public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
                RedisTemplate<String, Object> template = new RedisTemplate<>();
                template.setConnectionFactory(connectionFactory);

                // String serializer
                StringRedisSerializer stringSerializer = new StringRedisSerializer();
                template.setKeySerializer(stringSerializer);
                template.setHashKeySerializer(stringSerializer);

                // JSON serializer
                GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer();
                template.setValueSerializer(jsonSerializer);
                template.setHashValueSerializer(jsonSerializer);

                template.afterPropertiesSet();
                return template;
        }

        @Bean
        public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
                RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                                .entryTtl(Duration.ofMinutes(10))
                                .serializeKeysWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new StringRedisSerializer()))
                                .serializeValuesWith(
                                                RedisSerializationContext.SerializationPair.fromSerializer(
                                                                new GenericJackson2JsonRedisSerializer()));

                return RedisCacheManager.builder(connectionFactory)
                                .cacheDefaults(config)
                                .build();
        }

        @Bean
        public CommandLineRunner initRedisStreams(RedisTemplate<String, Object> redis) {
                return args -> {

                        // Email Stream
                        try {
                                redis.opsForStream()
                                                .createGroup("email_stream", "email_group");
                        } catch (Exception ignored) {
                        }

                        // Retry Stream
                        try {
                                redis.opsForStream()
                                                .createGroup("email_retry_stream", "retry_group");
                        } catch (Exception ignored) {
                        }

                        // Dead-letter
                        redis.opsForStream()
                                        .add("email_dead_letter", Map.of("init", "true"));
                };
        }

}
