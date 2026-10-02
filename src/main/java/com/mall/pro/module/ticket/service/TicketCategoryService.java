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

    public TicketCategory getById(Long id) {
        String cacheKey = CACHE_KEY_PREFIX + id;
        String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
        if (StringUtils.hasText(cachedJson)) {
            try {
                log.info("[Redis缓存命中] 成功从内存取得票档: id={}", id);
                return objectMapper.readValue(cachedJson, TicketCategory.class);
            } catch (JsonProcessingException e) {
                log.error("[Redis反序列化失败]", e);
            }
        }

        log.info("[Redis缓存未命中] 穿透查询数据库: id={}", id);
        TicketCategory category = ticketCategoryMapper.selectById(id);
        if (category == null) {
            throw new BusinessException(404, "票档不存在: id=" + id);
        }

        try {
            String json = objectMapper.writeValueAsString(category);
            stringRedisTemplate.opsForValue().set(cacheKey, json, 1, TimeUnit.HOURS);
            log.info("[Redis回填成功] 票档写入缓存成功: id={}", id);
        } catch (JsonProcessingException e) {
            log.error("[Redis序列化失败]", e);
        }
        return category;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deductStock(Long id, Integer count) {
        int rows = ticketCategoryMapper.deductStock(id, count);
        if (rows > 0) {
            String cacheKey = CACHE_KEY_PREFIX + id;
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
            if (TransactionSynchronizationManager.isActualTransactionActive()) {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        stringRedisTemplate.delete(cacheKey);
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
