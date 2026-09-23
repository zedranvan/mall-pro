package com.mall.pro.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class OutboxPublisher {

    @Autowired
    private OutboxService outboxService;

    /**
     * 后台定时可靠投递任务：
     * 默认每 1000ms 扫描一次待发送事件。
     * 多实例下依赖 FOR UPDATE SKIP LOCKED 协同，零锁竞争。
     */
    @Scheduled(fixedDelay = 1000)
    public void schedulePublish() {
        try {
            int processed = outboxService.publishPendingBatch();
            if (processed > 0) {
                log.info("[Outbox引擎轮询] 本批次成功拉取投递 {} 条事件", processed);
            }
        } catch (Exception e) {
            log.error("[Outbox引擎轮询异常]", e);
        }
    }
}
