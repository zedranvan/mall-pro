package com.mall.pro.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@TableName("seckill_goods")

public class SeckillGoods {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long goodsId;
    private BigDecimal seckillPrice;
    private Integer stockCount;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
}
