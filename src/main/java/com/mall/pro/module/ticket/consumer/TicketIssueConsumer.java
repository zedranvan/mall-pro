package com.mall.pro.module.ticket.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.service.OrderService;
import com.mall.pro.module.pay.event.OrderPaidEvent;
import com.mall.pro.module.ticket.entity.TicketItem;
import com.mall.pro.module.ticket.mapper.TicketItemMapper;
import org.springframework.messaging.handler.annotation.Header;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssueConsumer {

    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final TicketItemMapper ticketItemMapper;
    private final OrderService orderService;
    /**
     * 1. 核心出票监听器
     * - @RetryableTopic: 自动启用重试与死信机制（最多重试3次，1秒起步，2倍指数退避）
     */
    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2.0),
            autoCreateTopics = "true"
    )
    @KafkaListener(topics = "order-paid-topic", groupId = "mall-ticket-service-group")
    public void onOrderPaid(String message) {
        try {
            OrderPaidEvent event = objectMapper.readValue(message, OrderPaidEvent.class);
            Long orderId = event.getOrderId();
            log.info(">>> [Kafka收到出票消息] 正在处理电子门票: orderId={}, userId={}", orderId, event.getUserId());

            // 🛡️ 防线一：Redis SETNX 幂等去重防重复出票
            String idempotencyKey = "ticket:issued:" + orderId;
            Boolean isFirstTime = stringRedisTemplate.opsForValue()
                    .setIfAbsent(idempotencyKey, "PROCESSING", 24, TimeUnit.HOURS);

            if (Boolean.FALSE.equals(isFirstTime)) {
                log.warn("[出票幂等拦截] 订单已被处理，拒绝重复出票: orderId={}", orderId);
                return;
            }

            // 1. 模拟生成电子门票防伪核销二维码 (耗时操作)
            String verifyCode = "TCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();



            // 2. 查订单信息，分配座位并正式写入 d_ticket_item 资产表
            TicketOrder order = orderService.getOrderById(orderId);
            String seatInfo = "A区 " + (orderId % 15 + 1) + "排 " + (orderId % 30 + 1) + "座";

            // 核心：若订单有真实观演人快照则直接绑定，无则降级兜底
            String finalRealName = order.getRealName() != null ? order.getRealName() : ("观众_" + event.getUserId());
            String finalIdCardMasked = order.getIdCardMasked() != null ? order.getIdCardMasked() : ("440102********" + String.
                    format("%04d", (event.getUserId() % 10000)));
            String finalIdCardHash = order.getIdCardHash() != null ? order.getIdCardHash() : com.mall.pro.common.util.IdCardSecurityUtil.
                    sha256WithSalt("44010219980101" + String.format("%04d", (event.getUserId() % 10000)));

            TicketItem ticketItem = TicketItem.builder()
                    .orderId(orderId)
                    .userId(event.getUserId())
                    .programId(order.getProgramId())
                    .ticketCategoryId(order.getTicketCategoryId())
                    .ticketTitle(order.getTicketName())
                    .seatInfo(seatInfo)
                    .verifyCode(verifyCode)
                    .status(0) // 0: 待入场核销
                    .realName(finalRealName)
                    .idCardMasked(finalIdCardMasked)
                    .idCardHash(finalIdCardHash)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            ticketItemMapper.insert(ticketItem);
            log.info("[电子票资产落盘成功] ticketId={}, verifyCode={}, seat={}", ticketItem.getId(), verifyCode, seatInfo);
            // 2. 回填核销凭据到 Redis
            stringRedisTemplate.opsForValue().set(idempotencyKey, verifyCode, 24, TimeUnit.HOURS);

            // 3. 模拟调用短信网关发送凭证
            log.info("[短信网关模拟] 已向用户 ID={} 发送出票短信: 您的演唱会门票已出票成功！电子核销码为: {}",
                    event.getUserId(), verifyCode);

            log.info("<<< [出票流程全部完成] orderId={}, verifyCode={}", orderId, verifyCode);
        } catch (Exception e) {
            log.error(">>> [Kafka出票消费异常] message={}", message, e);
            // ⚠️ 关键设计：向上抛出异常，触发 Spring Kafka 的 @RetryableTopic 重试与死信流转
            throw new RuntimeException("出票失败触发重试与死信", e);
        }
    }
    /**
     * 2. 死信队列（DLQ）兜底处理器
     * 连续重试 3 次仍失败的毒丸消息自动流入此处
     */
    @DltHandler
    public void handleDeadLetter(String message, @Header(KafkaHeaders.RECEIVED_TOPIC) String topic) {
        log.error("💥 [死信队列告警] 消息多次消费失败，已自动移入死信队列: topic={}, payload={}", topic, message);
    }

}
