package com.mall.pro.dto.reconciliation;

import com.mall.pro.dto.InventoryCheckResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReconciliationReport {

    /** 对账商品 ID */
    private Long goodsId;

    /** 对账执行时间 */
    private LocalDateTime checkTime;

    /** 总体对账结论：三个维度全部 passed 才为 true */
    private boolean passed;

    /** 诊断总结 */
    private String summary;

    /** 维度一：库存守恒 */
    private InventoryCheckResult inventory;

    /** 维度二：防重幂等 */
    private IdempotencyCheckResult idempotency;

    /** 维度三：事务发件箱一致性 */
    private OutboxCheckResult outbox;

}
