package com.mall.pro.entity;

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
@TableName("d_ticket_order")
public class TicketOrder {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long userId;

    private Long programId;

    private Long ticketCategoryId;

    private String ticketName;

    private BigDecimal orderPrice;

    private Integer status;

    /** 乐观锁 */
    private Integer version;

    private LocalDateTime createTime;

    private LocalDateTime payTime;
}
