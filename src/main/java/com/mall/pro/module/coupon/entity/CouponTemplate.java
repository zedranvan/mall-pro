package com.mall.pro.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
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
@TableName("d_coupon_template")
public class CouponTemplate {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String name;
    
    /**
     * 券类型: 1-满减券, 2-折扣券, 3-无门槛立减
     */
    private Integer type;

    private BigDecimal thresholdAmount;
    private BigDecimal discountAmount;
    private BigDecimal discountRate;
    private BigDecimal maxDiscountAmount;

    private Integer totalQuota;
    private Integer remainQuota;

    private LocalDateTime validStartTime;
    private LocalDateTime validEndTime;

    /**
     * 1-启用, 0-停用
     */
    private Integer status;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
