package com.campus.canteen.config;

import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.cache.RedisCacheManager;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class AntiBreakdownCacheManager implements CacheManager {

    private final RedisCacheManager delegate;
    private final RedissonClient redissonClient;
    private final ConcurrentMap<String, Cache> cacheMap = new ConcurrentHashMap<>();

    public AntiBreakdownCacheManager(RedisCacheManager delegate, RedissonClient redissonClient) {
        this.delegate = delegate;
        this.redissonClient = redissonClient;
    }

    @Override
    public Cache getCache(String name) {
        return cacheMap.computeIfAbsent(name, n -> {
            Cache cache = delegate.getCache(n);
            if (cache instanceof RedisCache redisCache) {
                return new AntiBreakdownRedisCache(redisCache, redissonClient);
            }
            return cache;
        });
    }

    @Override
    public Collection<String> getCacheNames() {
        return delegate.getCacheNames();
    }
}
