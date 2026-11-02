package com.mall.pro.module.coupon.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("d_coupon_user")
public class CouponUser {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String couponCode;
    private Long userId;
    private Long templateId;

    /**
     * 0: 未使用 (Available)
     * 1: 下单冻结中 (Frozen)
     * 2: 已核销使用 (Used)
     * 3: 已失效 (Expired)
     */
    private Integer status;

    private Long orderId;
    private LocalDateTime usedTime;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
