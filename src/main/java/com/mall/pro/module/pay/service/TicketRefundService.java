package com.mall.pro.module.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.mapper.TicketOrderMapper;
import com.mall.pro.module.pay.entity.PayRecord;
import com.mall.pro.module.pay.enums.PayStatus;
import com.mall.pro.module.pay.mapper.PayRecordMapper;
import com.mall.pro.module.ticket.dto.RefundResultDTO;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.entity.TicketItem;
import com.mall.pro.module.ticket.mapper.TicketItemMapper;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketRefundService {
    private final TicketItemMapper ticketItemMapper;
    private final TicketCategoryService ticketCategoryService;
    private final TicketOrderMapper ticketOrderMapper;
    private final PayRecordMapper payRecordMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final com.mall.pro.module.waitlist.service.WaitlistService waitlistService;

    @Transactional(rollbackFor = Exception.class)
    public RefundResultDTO refundTicket(Long ticketItemId, Long userId) {
        // 步骤 1：前置安全检查（查验归属与物理核验状态）
        TicketItem item = ticketItemMapper.selectById(ticketItemId);
        if (item == null) {
            throw new BusinessException(404, "门票不存在: id=" + ticketItemId);
        }
        if (!Objects.equals(item.getUserId(), userId)) {
            throw new BusinessException(403, "非法操作：无权办理他人门票的退票！");
        }
        if (item.getStatus() == 1) {
            throw new BusinessException(400, "【退票拒绝】门票已在闸机核销入场，严禁退票！");
        }
        if (item.getStatus() == 2) {
            throw new BusinessException(400, "【退票拒绝】该门票已办理退票，请勿重复申请！");
        }

        // 步骤 2：阶梯费率引擎（按距离演出时长阶梯扣减）
        // 假定演出时间设定在售出后的第 3 天 (72小时后)
        LocalDateTime showTime = item.getCreateTime().plusDays(3);
        long hoursUntilShow = Duration.between(LocalDateTime.now(), showTime).toHours();

        BigDecimal feeRate;
        String ruleDesc;
        if (hoursUntilShow >= 48) {
            feeRate = new BigDecimal("0.20"); // 扣除 20% 手续费
            ruleDesc = "开演前48小时以上退票，扣除20%手续费";
        } else if (hoursUntilShow >= 24) {
            feeRate = new BigDecimal("0.50"); // 扣除 50% 手续费
            ruleDesc = "开演前24~48小时退票，扣除50%手续费";
        } else if (hoursUntilShow > 0) {
            feeRate = new BigDecimal("0.80"); // 扣除 80% 手续费
            ruleDesc = "开演前24小时内退票，扣除80%手续费";
        } else {
            throw new BusinessException(400, "【退票拒绝】演出已开演或已结束，不支持退票！");
        }

        TicketCategory category = ticketCategoryService.getById(item.getTicketCategoryId());
        BigDecimal originalPrice = category.getPrice();
        BigDecimal handlingFee = originalPrice.multiply(feeRate).setScale(2, RoundingMode.HALF_UP);
        BigDecimal actualRefund = originalPrice.subtract(handlingFee);

        // 步骤 3：CAS 原子作废门票（底层防并发抢先核销）
        int rows = ticketItemMapper.refundTicketCas(ticketItemId, LocalDateTime.now());
        if (rows == 0) {
            log.warn("[退票并发冲突] 门票可能已被抢先核销: ticketItemId={}", ticketItemId);
            throw new BusinessException(409, "退票处理冲突，门票状态已变更！");
        }

        // 步骤 4：逆向状态跃迁与库存自愈回流
        // 4.1 订单状态流转为已退款 (PAID -> REFUNDED)
        ticketOrderMapper.updateStatusCas(item.getOrderId(), OrderStatus.PAID.getCode(), OrderStatus.REFUNDED.getCode());

        // 4.2 支付流水标记为已退款
        PayRecord payRecord = payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecord>().eq(PayRecord::getOrderId, item.getOrderId())
        );
        if (payRecord != null) {
            payRecord.setStatus(PayStatus.REFUNDED.getCode());
            payRecordMapper.updateById(payRecord);
        }
        // 4.3 核心拦截：优先触发候补撮合引擎！
        boolean matched = waitlistService.triggerMatching(item.getTicketCategoryId());
        if (!matched) {
            // 无人候补时，库存才允许安全回流数据库与 Redis 公共公海
            ticketCategoryService.restoreStock(item.getTicketCategoryId(), 1);
            stringRedisTemplate.opsForValue().increment("ticket:stock:" + item.getTicketCategoryId());
            log.info("[无候补排队] 释放库存已安全回流公海: categoryId={}", item.getTicketCategoryId());
        } else {
            log.info("[候补截胡成功] 释放库存已优先匹配候补队列出票，已阻断公海回流: categoryId={}", item.getTicketCategoryId());
        }


        // 4.4 释放用户限购配额（移出秒杀限购名单，允许重新抢购）
        stringRedisTemplate.opsForSet().remove("ticket:users:" + item.getTicketCategoryId(), String.valueOf(userId));

        log.info("[退票与库存回流成功] ticketItemId={}, orderId={}, 实退金额={}, 规则={}",
                ticketItemId, item.getOrderId(), actualRefund, ruleDesc);

        return RefundResultDTO.builder()
                .ticketItemId(ticketItemId)
                .orderId(item.getOrderId())
                .originalPrice(originalPrice)
                .feeRate(feeRate)
                .handlingFee(handlingFee)
                .actualRefund(actualRefund)
                .refundRuleDesc(ruleDesc)
                .build();
    }
}
