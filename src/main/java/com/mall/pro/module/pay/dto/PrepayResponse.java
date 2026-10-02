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
public class PrepayResponse {
    private String paySn;

    private Long orderId;

    private BigDecimal amount;

    private String cashierUrl;

}
