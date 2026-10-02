package com.mall.pro.module.pay.entity;

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
@TableName("d_pay_record")
public class PayRecord {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private String paySn;

    private Long orderId;

    private Long userId;

    private String payChannel;

    private BigDecimal amount;

    private Integer status;

    private String tradeNo;

    private LocalDateTime createTime;

    private LocalDateTime payTime;
}