package com.mall.pro.module.order.service;

import com.mall.pro.common.BusinessException;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.mapper.TicketOrderMapper;
import com.mall.pro.module.order.queue.OrderDelayQueue;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final long DEFAULT_TIMEOUT_MILLIS = 30 * 1000L;
    private final TicketOrderMapper ticketOrderMapper;
    private final TicketCategoryService ticketCategoryService;
    private final OrderDelayQueue orderDelayQueue;

    public TicketOrder getOrderById(Long id){
        TicketOrder order = ticketOrderMapper.selectById(id);
        if(order == null){
            throw new BusinessException(404,"订单不存在：id="+id);
        }

        return order;
    }
    @Transactional(rollbackFor = Exception.class)
    public TicketOrder  createOrder(Long userId,Long ticketCategoryId,Integer count){
        log.info("[开始下单] userId={},ticketCategoryId={},count={}",userId,ticketCategoryId,count);

        TicketCategory category = ticketCategoryService.getById(ticketCategoryId);

        boolean deductSuccess = ticketCategoryService.deductStock(ticketCategoryId,count);
        if(!deductSuccess){
            log.warn("[下单阻断] 库存不足或已售罄: categoryId={}", ticketCategoryId);
            throw new BusinessException(400, "手慢了，该票档已经售罄！");
        }
        BigDecimal totalPrice = category.getPrice().multiply(new BigDecimal(count));
        TicketOrder order = TicketOrder.builder()
                .userId(userId)
                .programId(category.getProgramId())
                .ticketCategoryId(category.getId())
                .ticketName(category.getName())
                .orderPrice(totalPrice)
                .status(OrderStatus.CREATED.getCode())
                .version(1)
                .createTime(LocalDateTime.now())
                .build();

        ticketOrderMapper.insert(order);

        // 委托给延迟队列管理器
        orderDelayQueue.push(order.getId(), DEFAULT_TIMEOUT_MILLIS);
        log.info("[下单成功] 订单已创建: orderId={}", order.getId());
        return order;
    }
    /**
     * 支付：校验状态机 -> CAS 跃迁 PAID -> 移出延迟关单队列
     */
    @Transactional(rollbackFor = Exception.class)
    public TicketOrder payOrder(Long orderId){
        TicketOrder order = getOrderById(orderId);

        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());
        if (!currentStatus.canTransitionTo(OrderStatus.PAID)) {
            throw new BusinessException(400, "订单状态有误，无法支付! 当前状态: " + currentStatus.getDescription());
        }

        int rows = ticketOrderMapper.updateStatusCas(orderId, OrderStatus.CREATED.getCode(), OrderStatus.PAID.getCode());
        if (rows == 0) {
            log.warn("[支付冲突阻断] 订单版本已被其他任务修改: orderId={}", orderId);
            throw new BusinessException(409, "订单状态已发生变更，请刷新重试！");
        }
        order.setStatus(OrderStatus.PAID.getCode());
        order.setPayTime(LocalDateTime.now());

        // 付款成功，移出延迟队列
        orderDelayQueue.remove(orderId);
        log.info("[支付成功] orderId={}, payTime={}", orderId, order.getPayTime());
        return order;
    }
    @Transactional(rollbackFor = Exception.class)
    public boolean cancelOrder(Long orderId, String reason) {
        TicketOrder order = ticketOrderMapper.selectById(orderId);
        if (order == null) {
            log.warn("[关单拒绝] 订单不存在: orderId={}", orderId);
            return false;
        }

        OrderStatus currentStatus = OrderStatus.fromCode(order.getStatus());
        if (!currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
            log.warn("[关单拒绝] 订单已处于终态，无法取消: orderId={}, status={}", orderId, order.getStatus());
            return false;
        }

        int rows = ticketOrderMapper.updateStatusCas(orderId, OrderStatus.CREATED.getCode(), OrderStatus.CANCELLED.
                getCode());
        if (rows == 0) {
            log.warn("[关单并发冲突] 订单状态已被抢先修改(如已支付): orderId={}", orderId);
            return false;
        }

        // 触发库存自愈回滚
        boolean restoreSuccess = ticketCategoryService.restoreStock(order.getTicketCategoryId(), 1);
        if (!restoreSuccess) {
            log.error("[库存自愈失败] 回滚库存未生效: orderId={}", orderId);
            throw new BusinessException(500, "库存回滚失败，关单事务终止");
        }

        log.info("[关单成功-库存自愈] reason={}, orderId={}, categoryId={}", reason, orderId, order.getTicketCategoryId());
        return true;
    }

}
