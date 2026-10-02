package com.mall.pro.module.pay.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayNotifyRequest {
    private String paySn;
    private String tradeNo;
    private BigDecimal amount;
    private String payStatus;
    private String sign;
}
