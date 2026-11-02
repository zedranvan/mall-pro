package com.mall.pro.module.coupon.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CouponOptimalResultDTO {
    private BigDecimal orderAmount;
    private CouponUserDTO recommendedCoupon;
    private BigDecimal maxDiscountAmount;
    private BigDecimal finalPayAmount;
    private List<CouponUserDTO> availableCoupons;
    private List<CouponUserDTO> unavailableCoupons;
}
