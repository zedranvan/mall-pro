package com.mall.pro.module.ticket.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.mapper.TicketCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketCategoryService {


    private final TicketCategoryMapper ticketCategoryMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final String CACHE_KEY_PREFIX = "ticket_category:";
    private final com.github.benmanes.caffeine.cache.Cache<String, Object> caffeineCache;

    public TicketCategory getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;

        // 1. 优先查 L1 本地缓存 (纳秒级耗时，零网络 IO)
        TicketCategory localCategory = (TicketCategory) caffeineCache.getIfPresent(cacheKey);
        if (localCategory != null) {
            log.info("[L1 Caffeine命中] 进程内存直出票档: id={}", id);
            return localCategory;
        }

        // 2. L1 未命中，查 L2 Redis 分布式缓存 (毫秒级耗时)
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedJson)) {
            try {
                TicketCategory category = objectMapper.readValue(cachedJson, TicketCategory.class);
                // 核心：将 Redis 的数据回填至 L1 Caffeine，下次直接走内存
                caffeineCache.put(cacheKey, category);
                log.info("[L2 Redis命中] 成功回填L1本地缓存并返回: id={}", id);
                return category;
            } catch (JsonProcessingException e) {
                log.error("[Redis反序列化失败]", e);
            }
        }

        // 3. L1/L2 均未命中，穿透查询 PostgreSQL 数据库 (底线保护)
        log.info("[L1/L2均未命中] 穿透查询数据库: id={}", id);
        TicketCategory category = ticketCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "票档不存在: id=" + id);
        }

        // 4. 双级回填
        try {
            String json = objectMapper.writeValueAsString(category);
            stringRedisTemplate.opsForValue().set(cacheKey, json, 1, TimeUnit.HOURS);
        } catch (JsonProcessingException e) {
            log.error("[Redis序列化失败]", e);
        }
        caffeineCache.put(cacheKey, category);
        log.info("[双级回填完成] 票档写入Redis与Caffeine: id={}", id);

        return category;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long id, Integer count) {
        int rows = ticketCategoryMapper.deductStock(id, count);
        if (rows > 0) {
            String cacheKey = CACHE_KEY_PREFIX + id;
            caffeineCache.invalidate(cacheKey);
            stringRedisTemplate.delete(cacheKey);
            log.info("[Redis缓存淘汰] 票档库存已变更，删除旧缓存: id={}", id);
            return true;
        }
        return false;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean restoreStock(Long id, Integer count) {
        int rows = ticketCategoryMapper.restoreStock(id, count);
        if (rows > 0) {
            String cacheKey = CACHE_KEY_PREFIX + id;
            caffeineCache.invalidate(cacheKey);
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        stringRedisTemplate.delete(cacheKey);
                        caffeineCache.invalidate(cacheKey);
                        log.info("[Redis联动] 事务确认提交，安全淘汰缓存: key={}", cacheKey);
                    }
                });
            } else {
                stringRedisTemplate.delete(cacheKey);
            }
            return true;
        }
        return false;
    }
}
