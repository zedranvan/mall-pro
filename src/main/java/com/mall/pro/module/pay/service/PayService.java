package com.mall.pro.module.pay.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.service.OrderService;
import com.mall.pro.module.pay.dto.PayNotifyRequest;
import com.mall.pro.module.pay.dto.PrepayResponse;
import com.mall.pro.module.pay.entity.OutboxMessage;
import com.mall.pro.module.pay.entity.PayRecord;
import com.mall.pro.module.pay.enums.PayStatus;
import com.mall.pro.module.pay.event.OrderPaidEvent;
import com.mall.pro.module.pay.mapper.OutboxMapper;
import com.mall.pro.module.pay.mapper.PayRecordMapper;
import com.mall.pro.module.pay.util.PaySignHelper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {


    private final PayRecordMapper payRecordMapper;
    private final OrderService orderService;
    private final ObjectMapper objectMapper;
    private final PaySignHelper paySignHelper;
    private final OutboxMapper outboxMapper;




    @Transactional(rollbackFor = Exception.class)
    public PrepayResponse prepay(Long orderId) {
        TicketOrder order = orderService.getOrderById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在:id=" + orderId);
        }
        if (order.getStatus() != OrderStatus.CREATED.getCode()) {
            throw new BusinessException(400, "订单当前状态无法发起支付！当前状态： "
                    + order.getStatus());
        }
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String paySn = "PAY_" + timeStr + "_" + UUID.randomUUID().toString().substring(0, 6).toLowerCase();

        PayRecord record = PayRecord.builder()
                .paySn(paySn)
                .orderId(order.getId())
                .userId(order.getUserId())
                .payChannel("MOCK_WECHAT")
                .amount(order.getOrderPrice())
                .status(PayStatus.INIT.getCode())
                .createTime(LocalDateTime.now())
                .build();
        payRecordMapper.insert(record);
        log.info("[预下单成功]生成支付流水:paySn={},orderId={},amount={}",
                paySn, orderId, order.getOrderPrice());

        return PrepayResponse.builder()
                .paySn(paySn)
                .orderId(orderId)
                .amount(order.getOrderPrice())
                .cashierUrl("http://localhost:8080/api/pay/mock/cashier?paySn=" + paySn)
                .build();
    }


    @Transactional(rollbackFor = Exception.class)
    public boolean handleNotify(PayNotifyRequest request) {
        // 验证签名
        // 步骤 1：防伪验签
        if (!paySignHelper.verifySign(request)) {
            log.warn("[支付回调] 签名校验失败: paySn={}", request.getPaySn());
            return false;
        }

        // 步骤 2：防重幂等与金额防篡改校验
        PayRecord record = payRecordMapper.selectOne(
                new LambdaQueryWrapper<PayRecord>().eq(PayRecord::getPaySn, request.getPaySn())
        );
        if (record == null) return false;
        if (Objects.equals(record.getStatus(), PayStatus.SUCCESS.getCode())) return true;
        if (record.getAmount().compareTo(request.getAmount()) != 0) return false;

        // 步骤 3：状态跃迁（流水成功 + 订单已支付）
        record.setStatus(PayStatus.SUCCESS.getCode());
        record.setTradeNo(request.getTradeNo());
        record.setPayTime(LocalDateTime.now());
        payRecordMapper.updateById(record);
        orderService.payOrder(record.getOrderId());

        // 步骤 4：本地消息表落库（将出票信件存入 t_outbox）
        saveOutboxEvent(record);

        log.info("[支付回调成功] 流水与订单完成履约: paySn={}, orderId={}", record.getPaySn(), record.getOrderId());
        return true;
    }

    /**
     * 私有辅助：在同一事务中持久化 Outbox 领域事件
     */
    private void saveOutboxEvent(PayRecord record) {
        try {
            OrderPaidEvent event = OrderPaidEvent.builder()
                    .orderId(record.getOrderId())
                    .paySn(record.getPaySn())
                    .tradeNo(record.getTradeNo())
                    .userId(record.getUserId())
                    .amount(record.getAmount())
                    .payTime(record.getPayTime())
                    .build();

            OutboxMessage outbox = OutboxMessage.builder()
                    .aggregateType("ORDER")
                    .aggregateId(record.getOrderId())
                    .eventType("ORDER_PAID")
                    .topic("order-paid-topic")
                    .payload(objectMapper.writeValueAsString(event))
                    .status(0)       // 0: 待投递
                    .retryCount(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            outboxMapper.insert(outbox);
            log.info("[本地消息表落库] orderId={}, topic={}", record.getOrderId(), outbox.getTopic());
        } catch (Exception e) {
            log.error("[本地消息表落库异常] orderId={}", record.getOrderId(), e);
            throw new BusinessException(500, "记录出票事件失败，支付事务回滚");
        }
    }
        public String generateSign(String paySn, BigDecimal amount, String payStatus) {
            return paySignHelper.generateSign(paySn, amount, payStatus);
        }

    }

