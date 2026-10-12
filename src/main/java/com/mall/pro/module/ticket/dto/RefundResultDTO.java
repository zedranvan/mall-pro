package com.mall.pro.module.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefundResultDTO {
    private Long ticketItemId;          // 退票ID
    private Long orderId;               // 关联订单ID
    private BigDecimal originalPrice;   // 原票价
    private BigDecimal feeRate;         // 手续费率 (如 0.20 代表 20%)
    private BigDecimal handlingFee;     // 扣除手续费
    private BigDecimal actualRefund;    // 实退金额
    private String refundRuleDesc;      // 规则说明 (如 "开演前48小时退票，扣除20%手续费")
}
