package com.mall.pro.module.ticket.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.module.pay.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketIssueConsumer {

    private final ObjectMapper objectMapper;

    @KafkaListener(topics = "order-paid-topic", groupId = "mall-ticket-service-group")
    public void onOrderPaid(String message) {
        try {
            OrderPaidEvent event = objectMapper.readValue(message, OrderPaidEvent.class);
            log.info(">>> [Kafka收到出票消息] 正在为订单处理电子门票: orderId={}, userId={}",
                    event.getOrderId(), event.getUserId());

            // 1. 模拟生成电子门票防伪核销二维码 (耗时操作)
            String verifyCode = "TCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();

            // 2. 模拟调用短信网关向用户发送通知
            log.info("[短信网关模拟] 已向用户 ID={} 发送出票短信: 您的演唱会门票已出票成功！电子核销码为: {}",
                    event.getUserId(), verifyCode);

            log.info("<<< [出票流程全部完成] orderId={}, verifyCode={}", event.getOrderId(), verifyCode);
        } catch (Exception e) {
            log.error(">>> [Kafka出票消费异常] 消息解析或业务处理失败: message={}", message, e);
        }
    }
}
