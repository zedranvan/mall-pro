package com.mall.pro.dto.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor

public class OutboxCheckResult {
    /** 主订单表记录总数 */
    private long totalOrder;

    /** 发件箱中ORDER_CREATE 事件总数 */
    private long outboxCreatedEvents;

    /** 成功进入kafka的事件数 */
    private long publishedCount;

    /** 仍在待投递队列中的积压数 */
    private long pendingCount;

    /** 重试超限失败的死信数 */
    private long failedCount;

    /** 漏发时事件差额，理应为0 */
    private long missingEvents;

    /** 本维度是否完全通过 */
    private boolean passed;

    /** 诊断结果描述 */
    private String message;
}
