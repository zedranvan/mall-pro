package com.mall.pro.module.order.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.mapper.TicketOrderMapper;
import com.mall.pro.module.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderTimeoutTask {

    private final TicketOrderMapper ticketOrderMapper;
    private final  OrderService orderService;

    /**
     * 每 10 秒自动巡逻一次
     * 找出创建时间超过 15 分钟且依然处于「待支付 (0)」的订单，自动关单并回滚库存！
     */
    @Scheduled(fixedDelay = 10000)
    public void scanAndCancelExpiredOrders() {
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(15);

        LambdaQueryWrapper<TicketOrder> query = new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, OrderStatus.CREATED.getCode())
                .lt(TicketOrder::getCreateTime, expireTime)
                .last("LIMIT 50");

        List<TicketOrder> expiredOrders = ticketOrderMapper.selectList(query);
        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("[定时巡逻] 发现 {} 笔超时未支付订单，启动自动关单与库存回滚...", expiredOrders.size());
        for (TicketOrder order : expiredOrders) {
            try {
                orderService.cancelOrder(order.getId(), "超时未支付系统自动关单");
            } catch (Exception e) {
                log.error("[自动关单异常] orderId={}", order.getId(), e);
            }
        }
    }
}
