package com.campus.canteen.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.io.IOException;
import java.time.Duration;

@Configuration
@EnableCaching
@Slf4j
public class RedisConfiguration {

    @Bean
    public RedisTemplate redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        log.info("开始创建redis模板对象...");

        RedisTemplate redisTemplate = new RedisTemplate();
        redisTemplate.setConnectionFactory(redisConnectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());

        return redisTemplate;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory,
                                     RedissonClient redissonClient) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(30))
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(cacheSerializer())
                );
        RedisCacheManager nativeManager = RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
        return new AntiBreakdownCacheManager(nativeManager, redissonClient);
    }

    /**
     * 缓存专用序列化器：开启 DefaultTyping，
     * 让 JSON 中每个对象都携带 @class 类型信息，
     * 解决 List<Dish> 等泛型集合反序列化时元素类型丢失的问题。
     */
    private GenericJackson2JsonRedisSerializer cacheSerializer() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());       // 支持 LocalDateTime 等 Java 8 时间类型
        mapper.activateDefaultTyping(                       // 写入 @class 类型标记
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.NON_FINAL
        );
        return new GenericJackson2JsonRedisSerializer(mapper);
    }

    @Bean
    public RedissonClient redissonClient() throws IOException {
        log.info("开始创建RedissonClient...");
        Config config = Config.fromYAML(
            new ClassPathResource("redisson-config.yaml").getInputStream()
        );
        return Redisson.create(config);
    }
}
