package com.mall.pro.module.pay.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {
    private Long orderId;
    private String paySn;
    private String tradeNo;
    private Long userId;
    private BigDecimal amount;
    private LocalDateTime payTime;
}
