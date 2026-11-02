package com.mall.pro.module.coupon.dto;

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
public class CouponUserDTO {
    private Long id;
    private String couponCode;
    private Long templateId;
    private String templateName;
    private Integer type;
    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;
    private Integer status;
    private String statusDesc;
    private Long orderId;
    private LocalDateTime validEndTime;

    // 动态试算属性
    private BigDecimal calculatedDiscount;
    private Boolean canUse;
    private String cannotUseReason;
}
