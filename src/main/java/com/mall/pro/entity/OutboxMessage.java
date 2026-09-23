package com.mall.pro.entity;

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
@TableName("t_outbox")
public class OutboxMessage {

    @TableId
    private Long id;

    private String aggregateType;

    private Long aggregateId;

    private String eventType;

    private String topic;

    private String payload;

    /**
     * 0: 待发送 (PENDING)
     * 1: 发送成功 (PUBLISHED)
     * 2: 发送失败 (FAILED)
     */
    private Integer status;

    private Integer retryCount;

    private String errorMsg;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
