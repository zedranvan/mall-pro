package com.mall.pro.module.user.entity;

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
@TableName("d_user_attendee")
public class UserAttendee {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String realName;
    private String idCardMasked;
    private String idCardHash;
    private String phone;
    private LocalDateTime createTime;
}
