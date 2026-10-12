package com.mall.pro.module.pay.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.module.pay.entity.OutboxMessage;
import com.mall.pro.module.pay.mapper.OutboxMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;


@Slf4j
@Component
@RequiredArgsConstructor
public class OutboxRelayer {

    private final OutboxMapper outboxMapper;
    private final KafkaTemplate<String, String> kafkaTemplate;

    @Scheduled(fixedDelay = 2000)
    public void relayMessages(){
        LambdaQueryWrapper<OutboxMessage> query = new LambdaQueryWrapper<OutboxMessage>()
                .eq(OutboxMessage::getStatus, 0)
                .lt(OutboxMessage::getRetryCount, 5)
                .orderByAsc(OutboxMessage::getCreateTime)
                .last("LIMIT 50");

    List<OutboxMessage> pendingMessages = outboxMapper.selectList(query);
        if (pendingMessages.isEmpty()) {
            return;
        }

        log.info("[Outbox投递引擎] 发现 {} 条待发送信件，开始可靠推向 Kafka...", pendingMessages.size());

        for (OutboxMessage msg : pendingMessages) {
            try {
                // 2. 发送 Kafka，使用 aggregateId 作为 Key 确保进入同一个 Partition（分区有序）
                // ⚠️ 同步等待 Broker ACK 确认（超时 3 秒），拿到真实回执才算成功！
                kafkaTemplate.send(msg.getTopic(), String.valueOf(msg.getAggregateId()), msg.getPayload())
                        .get(3, TimeUnit.SECONDS);

                // 3. 投递成功：跃迁为终态 1 (SENT)
                msg.setStatus(1);
                msg.setUpdateTime(LocalDateTime.now());
                outboxMapper.updateById(msg);
                log.info("[Outbox投递成功] msgId={}, aggregateId={}, topic={}", msg.getId(), msg.getAggregateId(), msg.
                        getTopic());
            } catch (Exception e) {
                // 4. 投递失败：自增重试计数并记录异常
                int retries = msg.getRetryCount() + 1;
                msg.setRetryCount(retries);
                msg.setErrorMsg(e.getMessage());
                msg.setUpdateTime(LocalDateTime.now());

                if (retries >= 5) {
                    msg.setStatus(2); // 标记为彻底失败 (死信告警)
                    log.error("[Outbox投递超限告警] 重试已达5次上限，转为死信人工排查: msgId={}", msg.getId(), e);
                } else {
                    log.warn("[Outbox投递失败，等待下次重试] msgId={}, retryCount={}", msg.getId(), retries);
                }
                outboxMapper.updateById(msg);
            }
        }
    }
}
