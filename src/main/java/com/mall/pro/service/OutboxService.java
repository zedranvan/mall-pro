package com.mall.pro.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.entity.OutboxMessage;
import com.mall.pro.mapper.OutboxMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class OutboxService {

    @Autowired
    private OutboxMapper outboxMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private static final int MAX_RETRY = 3;
    private static final int BATCH_SIZE = 50;

    /**
     * 写入发件箱记录：
     * 必须加入当前的业务强事务（Propagation.MANDATORY 或默认 REQUIRED）
     * 确保发件箱记录与业务数据（如订单）在同一个数据库本地事务中原子提交！
     */
    @Transactional(propagation = Propagation.REQUIRED, rollbackFor = Exception.class)
    public void saveEvent(String aggregateType, Long aggregateId, String eventType, String topic, Object payload) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxMessage outbox = OutboxMessage.builder()
                    .id(IdWorker.getId())
                    .aggregateType(aggregateType)
                    .aggregateId(aggregateId)
                    .eventType(eventType)
                    .topic(topic)
                    .payload(payloadJson)
                    .status(0) // 0: 待发送
                    .retryCount(0)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();

            outboxMapper.insert(outbox);
            log.debug("[Outbox已保存事件] aggregateId={}, eventType={}, topic={}", aggregateId, eventType, topic);
        } catch (JsonProcessingException e) {
            log.error("[Outbox序列化失败] aggregateId={}", aggregateId, e);
            throw new RuntimeException("Outbox payload serialization failed", e);
        }
    }

    /**
     * 批量拉取并向 Kafka 投递事件：
     * 采用独立事务逐条处理，防止单条失败影响整个批次
     */
    @Transactional(rollbackFor = Exception.class)
    public int publishPendingBatch() {
        List<OutboxMessage> messages = outboxMapper.pollPendingMessagesForUpdate(BATCH_SIZE, MAX_RETRY);
        if (messages == null || messages.isEmpty()) {
            return 0;
        }

        log.info("[Outbox引擎拉取事件] 获取待投递记录数: {}", messages.size());

        for (OutboxMessage msg : messages) {
            try {
                // 向 Kafka 可靠投递
                kafkaTemplate.send(msg.getTopic(), String.valueOf(msg.getAggregateId()), msg.getPayload())
                        .whenComplete((result, ex) -> {
                            if (ex == null) {
                                outboxMapper.markAsPublished(msg.getId());
                                log.debug("[Outbox投递成功] id={}, eventType={}", msg.getId(), msg.getEventType());
                            } else {
                                String error = ex.getMessage();
                                if (error != null && error.length() > 500) {
                                    error = error.substring(0, 500);
                                }
                                outboxMapper.markAsFailed(msg.getId(), error, MAX_RETRY);
                                log.error("[Outbox投递失败] id={}, error={}", msg.getId(), ex.getMessage());
                            }
                        });
            } catch (Exception e) {
                outboxMapper.markAsFailed(msg.getId(), e.getMessage(), MAX_RETRY);
                log.error("[Outbox投递异常] id={}", msg.getId(), e);
            }
        }

        return messages.size();
    }
}
