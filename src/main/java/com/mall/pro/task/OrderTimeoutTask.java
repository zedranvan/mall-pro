package com.mall.pro.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.OrderStatus;
import com.mall.pro.entity.TicketOrder;
import com.mall.pro.mapper.TicketOrderMapper;
import com.mall.pro.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
public class OrderTimeoutTask {

    @Autowired
    private TicketOrderMapper ticketOrderMapper;

    @Autowired
    private OrderService orderService;

    /**
     * 每 10 秒自动巡逻一次
     * 找出创建时间超过 15 分钟且依然处于「待支付 (0)」的订单，自动关单并回滚库存！
     */
    @Scheduled(fixedDelay = 10000)
    public void scanAndCancelExpiredOrders() {
        // 算出 15 分钟前的时间线
        LocalDateTime expireTime = LocalDateTime.now().minusMinutes(15);

        // 查询条件：status = 0 AND create_time < 15分钟前
        LambdaQueryWrapper<TicketOrder> query = new LambdaQueryWrapper<TicketOrder>()
                .eq(TicketOrder::getStatus, OrderStatus.CREATED.getCode())
                .lt(TicketOrder::getCreateTime, expireTime)
                .last("LIMIT 50"); // 批量处理，单次最多扫50笔，保护数据库

        List<TicketOrder> expiredOrders = ticketOrderMapper.selectList(query);
        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("[定时巡逻] 发现 {} 笔超时未支付订单，启动自动关单与库存回滚...",
                expiredOrders.size());
        for (TicketOrder order : expiredOrders) {
            try {
                orderService.cancelOrder(order.getId(), "超时未支付系统自动关单");
            } catch (Exception e) {
                log.error("[自动关单异常] orderId={}", order.getId(), e);
            }
        }
    }

}