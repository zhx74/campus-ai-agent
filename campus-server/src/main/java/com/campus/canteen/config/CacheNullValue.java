package com.campus.canteen.config;

import java.io.Serializable;

/**
 * 缓存穿透的 null 值占位标记。
 * 存入 Redis 时由 GenericJackson2JsonRedisSerializer 序列化，读取时根据类型识别。
 */
public final class CacheNullValue implements Serializable {

    public static final CacheNullValue INSTANCE = new CacheNullValue();

    /** Jackson 反序列化需要 public 无参构造器 */
    public CacheNullValue() {}

    @Override
    public boolean equals(Object o) {
        return o instanceof CacheNullValue;
    }

    @Override
    public int hashCode() {
        return 0;
    }
}
