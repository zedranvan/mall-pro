package com.mall.pro.entity;

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
@TableName("order_info")
public class OrderInfo {
    @TableId
    private Long id;
    private Long userId;
    private Long goodsId;
    private String goodsName;
    private BigDecimal orderPrice;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime payTime;

}
