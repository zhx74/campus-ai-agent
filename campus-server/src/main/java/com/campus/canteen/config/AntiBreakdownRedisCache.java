package com.campus.canteen.config;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.cache.Cache;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.data.redis.cache.RedisCache;

import java.lang.reflect.Method;
import java.time.Duration;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

@Slf4j
public class AntiBreakdownRedisCache implements Cache {

    private final RedisCache delegate;
    private final RedissonClient redissonClient;

    // ---- 击穿：SETNX 互斥锁 ----
    private static final long LOCK_TTL_SECONDS = 5;
    private static final int SPIN_MAX_RETRIES = 20;
    private static final long SPIN_INTERVAL_MS = 100;

    // ---- 穿透：null 值缓存 TTL ----
    private static final long NULL_TTL_MINUTES = 2;

    // ---- 雪崩：TTL 随机偏移比例（±10%） ----
    private static final double TTL_JITTER_RATIO = 0.1;

    public AntiBreakdownRedisCache(RedisCache delegate, RedissonClient redissonClient) {
        this.delegate = delegate;
        this.redissonClient = redissonClient;
    }

    private String lockKey(Object key) {
        return "cache:lock:" + getName() + ":" + key;
    }

    // ==================== get：击穿互斥 + 穿透识别 ====================

    @Override
    public ValueWrapper get(Object key) {
        ValueWrapper value = delegate.get(key);
        if (value != null) {
            return unwrapNull(value);
        }
        return tryAcquireAndWait(key);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Class<T> type) {
        ValueWrapper value = delegate.get(key);
        if (value != null) {
            return unwrapValue(value.get(), type);
        }
        value = tryAcquireAndWait(key);
        if (value != null) {
            return unwrapValue(value.get(), type);
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T get(Object key, Callable<T> valueLoader) {
        ValueWrapper value = delegate.get(key);
        if (value != null) {
            return unwrapNullValue(value);
        }

        RBucket<String> bucket = redissonClient.getBucket(lockKey(key));
        if (trySetLock(bucket)) {
            try {
                value = delegate.get(key);
                if (value != null) {
                    return unwrapNullValue(value);
                }
                T result = valueLoader.call();
                putInternal(key, result);
                return result;
            } catch (Exception e) {
                throw new ValueRetrievalException(key, valueLoader, e);
            } finally {
                deleteLock(bucket);
            }
        }

        value = spinWait(key);
        if (value != null) {
            return unwrapNullValue(value);
        }

        try {
            return valueLoader.call();
        } catch (Exception ex) {
            throw new ValueRetrievalException(key, valueLoader, ex);
        }
    }

    // ==================== put：穿透存 null 标记 + 雪崩随机 TTL ====================

    @Override
    public void put(Object key, Object value) {
        putInternal(key, value);
        // 释放 get(key) 路径设置的击穿锁
        deleteLock(redissonClient.getBucket(lockKey(key)));
    }

    private void putInternal(Object key, Object value) {
        // 穿透：null → 占位对象，后续请求识别为缓存命中
        Object storeValue = (value != null) ? value : CacheNullValue.INSTANCE;
        delegate.put(key, storeValue);

        // 雪崩：对实际 key 随机偏移 TTL，避免集中过期
        String redisKey = resolveRedisKey(key);
        Duration ttl = computeTtl(value);
        if (ttl != null) {
            try {
                redissonClient.getBucket(redisKey).expire(ttl);
            } catch (Exception e) {
                log.debug("TTL randomization failed for key={}", redisKey, e);
            }
        }
    }

    // ==================== 委托 ====================

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public Object getNativeCache() {
        return delegate.getNativeCache();
    }

    @Override
    public void evict(Object key) {
        delegate.evict(key);
        deleteLock(redissonClient.getBucket(lockKey(key)));
    }

    @Override
    public void clear() {
        delegate.clear();
    }

    // ==================== 内部 ====================

    /** 穿透：命中 NullValue 占位标记 → 返回 wrapped null（缓存命中） */
    private ValueWrapper unwrapNull(ValueWrapper value) {
        if (value.get() instanceof CacheNullValue) {
            return new SimpleValueWrapper(null);
        }
        return value;
    }

    @SuppressWarnings("unchecked")
    private <T> T unwrapNullValue(ValueWrapper value) {
        Object v = value.get();
        if (v instanceof CacheNullValue) {
            return null;
        }
        return (T) v;
    }

    @SuppressWarnings("unchecked")
    private <T> T unwrapValue(Object cached, Class<T> type) {
        if (cached instanceof CacheNullValue) {
            return null;
        }
        if (type != null && !type.isInstance(cached)) {
            throw new IllegalStateException(
                    "Cached value is not of required type [" + type.getName() + "]: " + cached);
        }
        return (T) cached;
    }

    /** 雪崩：base TTL ± 10% 随机偏移 */
    private Duration computeTtl(Object value) {
        Duration base = delegate.getCacheConfiguration().getTtl();
        if (base == null || base.isZero()) {
            return null;
        }
        // 穿透标记使用更短的 TTL
        if (value == null) {
            return Duration.ofMinutes(NULL_TTL_MINUTES);
        }
        long baseSec = base.getSeconds();
        long jitter = (long) (baseSec * TTL_JITTER_RATIO);
        long randomized = baseSec + ThreadLocalRandom.current().nextLong(-jitter, jitter + 1);
        return Duration.ofSeconds(Math.max(1, randomized));
    }

    /** 通过反射调用 protected RedisCache.createCacheKey 获取实际 Redis key */
    private String resolveRedisKey(Object key) {
        try {
            Method method = RedisCache.class.getDeclaredMethod("createCacheKey", Object.class);
            method.setAccessible(true);
            return (String) method.invoke(delegate, key);
        } catch (Exception e) {
            log.debug("Failed to resolve Redis key via reflection, using fallback", e);
            return delegate.getName() + "::" + key;
        }
    }

    // ---- 击穿：SETNX 抢锁 + 自旋 ----

    private ValueWrapper tryAcquireAndWait(Object key) {
        RBucket<String> bucket = redissonClient.getBucket(lockKey(key));
        if (trySetLock(bucket)) {
            ValueWrapper value = delegate.get(key);
            if (value != null) {
                deleteLock(bucket);
                return unwrapNull(value);
            }
            return null; // 放行给 Spring，后续 put() 释放锁
        }
        return spinWait(key);
    }

    private boolean trySetLock(RBucket<String> bucket) {
        try {
            return Boolean.TRUE.equals(
                    bucket.setIfAbsent("1", Duration.ofSeconds(LOCK_TTL_SECONDS)));
        } catch (Exception e) {
            log.warn("Redis SETNX failed, degraded to direct query", e);
            return false;
        }
    }

    private void deleteLock(RBucket<String> bucket) {
        try {
            bucket.delete();
        } catch (Exception e) {
            log.debug("Lock delete failed (may have expired)", e);
        }
    }

    private ValueWrapper spinWait(Object key) {
        for (int i = 0; i < SPIN_MAX_RETRIES; i++) {
            try {
                Thread.sleep(SPIN_INTERVAL_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
            ValueWrapper value = delegate.get(key);
            if (value != null) {
                return unwrapNull(value);
            }
        }
        log.warn("Cache breakdown: spin-wait exhausted for cache={} key={}", getName(), key);
        return null;
    }
}
