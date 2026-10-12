package com.mall.pro.common;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {

    @Bean
    public Cache<String, Object> caffeineCache() {
        return Caffeine.newBuilder()
                .initialCapacity(100)                     // 初始哈希表容量
                .maximumSize(10_000)                     // 内存上限：最多缓存 10000 个对象，超量时按 W-TinyLFU 自动淘汰
                .expireAfterWrite(10, TimeUnit.SECONDS)  // 写入后 10 秒自动过期，保证弱一致性
                .recordStats()                           // 开启命中率监控与指标统计
                .build();
    }
}
