package com.mall.pro.module.order.queue;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDelayQueue {

    private final StringRedisTemplate stringRedisTemplate;
    private static final String DELAY_QUEUE_KEY = "order_del_queue";

    /**
     * 1. 订单超时入队
     * @param orderId 订单号
     * @param timeoutMillis 超时相对时长 (如 30 秒)
     */

    public void push(Long orderId,long timeoutMillis) {
        long expireAt = System.currentTimeMillis() + timeoutMillis;
        stringRedisTemplate.opsForZSet().add(DELAY_QUEUE_KEY, String.valueOf(orderId),expireAt);
        log.info("[延迟队列-入队] orderId={}, expireAt={}", orderId, expireAt);
    }

    /**
     * 2. 订单已支付/已取消，安全移出队列
     */

    public void remove(Long orderId) {
        stringRedisTemplate.opsForZSet().remove(DELAY_QUEUE_KEY, String.valueOf(orderId));
        log.info("[延迟队列-移出] 订单已处理完毕，移出队列: orderId={}", orderId);
    }

    /**
     * 3. 轮询并安全弹出已到期的订单 ID 集合 (原子防并发重复弹)
     */

    public Set<Long> pollExpiredOrders(int batchSize) {
        long now  = System.currentTimeMillis();
        Set<String> expiredStrs = stringRedisTemplate.opsForZSet().rangeByScore(DELAY_QUEUE_KEY,0,now,0,batchSize);
        if(expiredStrs==null||expiredStrs.isEmpty()){
            return Collections.emptySet();
        }
        return expiredStrs.stream()
                .filter(idStr ->{
                    Long removed = stringRedisTemplate.opsForZSet().remove(DELAY_QUEUE_KEY,String.valueOf(idStr));
                    return removed != null && removed>0;
                })
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }
}
