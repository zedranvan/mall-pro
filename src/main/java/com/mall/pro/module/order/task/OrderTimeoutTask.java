package com.mall.pro.module.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.mapper.TicketOrderMapper;
import com.mall.pro.module.order.queue.OrderDelayQueue;
import com.mall.pro.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final OrderDelayQueue orderDelayQueue;
    private final OrderService orderService;

    /**
     * 每 5 秒极速巡检一次时间轮 (零数据库压力)
     */
    @Scheduled(fixedDelay = 5000)
    public void scanAndCancelExpiredOrders() {
        // 1. 从延迟队列拉取已到期的订单号
        Set<Long> expiredOrderIds = orderDelayQueue.pollExpiredOrders(50);
        if (expiredOrderIds.isEmpty()) {
            return;
        }

        log.info("[延迟队列触发] 发现 {} 笔到期未支付订单，启动关单自愈...", expiredOrderIds.size());
        for (Long orderId : expiredOrderIds) {
            try {
                // 2. 推进关单
                orderService.cancelOrder(orderId, "超时未支付系统自动取消");
            } catch (Exception e) {
                log.error("[自动关单异常] orderId={}", orderId, e);
            }
        }
    }
}
