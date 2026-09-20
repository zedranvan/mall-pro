package com.mall.pro.entity;


import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("goods")
public class Goods {


    @TableId(type = IdType.AUTO)
    private Long id;
    private  String name;
    private BigDecimal price;
    private Integer stock;
    private String detail;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;


}
