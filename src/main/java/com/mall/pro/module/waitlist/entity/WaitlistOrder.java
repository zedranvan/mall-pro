package com.mall.pro.module.waitlist.entity;

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
@TableName("d_waitlist_order")
public class WaitlistOrder {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private Long programId;
    private Long ticketCategoryId;
    private Long attendeeId;
    private String realName;
    private String idCardMasked;
    private String idCardHash;
    private Integer status; //0--排队，1--撮合成功已出票，2--主动取消，3--超时关闭
    private LocalDateTime createTime;
    private LocalDateTime matchTime;

}
