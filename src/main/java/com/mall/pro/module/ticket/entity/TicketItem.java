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
@TableName("d_ticket_item")
public class TicketItem {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long orderId;

    private Long userId;

    private Long programId;

    private Long ticketCategoryId;

    private String ticketTitle;

    private String seatInfo;

    private String verifyCode;

    private Integer status; // 0: 未核销, 1: 已核销, 2: 已退票

    private String gateNo;

    private LocalDateTime verifyTime;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private String realName;

    private String idCardMasked;

    private String idCardHash;        // 底层防多抢对比用的加盐哈希 (SHA-256)

}
