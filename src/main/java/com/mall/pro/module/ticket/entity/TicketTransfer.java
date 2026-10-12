package com.mall.pro.module.ticket.entity;


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
@TableName("d_ticket_transfer")
public class TicketTransfer {

    @TableId(type= IdType.ASSIGN_ID)

    private Long id;
    private Long ticketItemId;
    private Long fromUserId;
    private Long toUserId;
    private String oldVerifyCode;
    private LocalDateTime createTime;
}
