package com.mall.pro.module.waitlist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WaitlistResponseDTO {
    private Long waitlistId;
    private Long programId;
    private Long ticketCategoryId;
    private String realName;
    private String idCardMasked;
    private Integer status; // 0: 排队中, 1: 撮合成功, 2: 已取消, 3: 已过期
    private Long queuePosition; // 👈 核心亮点：当前排在第几位（如：排在第 1 位）
    private LocalDateTime createTime;
}