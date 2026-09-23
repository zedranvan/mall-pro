package com.mall.pro.service;

import com.mall.pro.common.OrderStatus;
import com.mall.pro.entity.OrderInfo;
import com.mall.pro.mapper.OrderInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ConcurrentModificationException;

@Slf4j
@Service
public class OrderStateMachineService {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private OutboxService outboxService;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 订单状态机原子跃迁：
     * 1. 严格按合法矩阵校验；
     * 2. 基于 version 乐观锁原子更新；
     * 3. 产生对应的 Transactional Outbox 事件持久化！
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean transition(Long orderId, OrderStatus targetStatus) {
        OrderInfo order = orderInfoMapper.selectById(orderId);
        if (order == null) {
            log.error("[状态机] 订单不存在: orderId={}", orderId);
            throw new IllegalArgumentException("订单不存在: " + orderId);
        }

        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());

        // 1. 业务逻辑矩阵校验：严禁非法跳状态
        if (!currentStatus.canTransitionTo(targetStatus)) {
            String err = String.format("[非法状态跃迁拦截] 订单ID: %d, 当前状态: %s, 目标状态: %s",
                    orderId, currentStatus.getDescription(), targetStatus.getDescription());
            log.warn(err);
            throw new IllegalStateException(err);
        }

        // 2. 数据库级原子乐观锁跃迁
        int rows = orderInfoMapper.transitionStatusWithOptimisticLock(
                orderId,
                currentStatus.getCode(),
                targetStatus.getCode(),
                order.getVersion()
        );

        if (rows == 0) {
            String err = String.format("[并发冲突/乱序拦截] 订单ID: %d 的版本号已被其他线程修改, 跃迁中断!", orderId);
            log.error(err);
            throw new ConcurrentModificationException(err);
        }

        // 更新本地对象用于 Outbox 发送
        order.setStatus(targetStatus.getCode());
        order.setVersion(order.getVersion() + 1);

        // 3. 写入发件箱，原子通知下游子系统
        String eventType = "ORDER_" + targetStatus.name();
        outboxService.saveEvent("ORDER", orderId, eventType, "order-event-topic", order);

        // 4. 特殊业务补偿逻辑（如取消订单自动返还 Redis 库存）
        if (targetStatus == OrderStatus.CANCELLED) {
            stringRedisTemplate.opsForValue().increment("seckill:stock:" + order.getGoodsId());
            log.info("[状态机回滚] 订单已取消，Redis 秒杀库存已自动原子返还 1 件");
        }

        log.info("[状态机成功跃迁] 订单ID: {}, 状态: {} -> {}, 新版本号: {}",
                orderId, currentStatus.name(), targetStatus.name(), order.getVersion());
        return true;
    }
}
