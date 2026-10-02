package com.mall.pro.module.ticket.entity;

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
@TableName("d_ticket_category")
public class TicketCategory {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long programId;

    private String name;

    private BigDecimal price;

    private Integer totalStock;

    private Integer remainStock;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
