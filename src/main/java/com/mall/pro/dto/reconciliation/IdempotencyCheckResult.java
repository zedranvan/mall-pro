package com.mall.pro.dto.reconciliation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IdempotencyCheckResult {

    /** 秒杀资格表总记录数 */
    private long totalSeckillOrder;
    /** 独立抢购成功的用户数量 */
    private long distinctUsers;
    /** 重复下单违规数量，理应为0 */
    private long duplicateCount;
    /** 孤儿订单书，即订单信息与秒杀订单信息不匹配的异常订单 */
    private long orphanOrders;
    /**本维度是否完全通过 */
    private boolean passed;
    /** 诊断结果描述 */
    private String message;
}
