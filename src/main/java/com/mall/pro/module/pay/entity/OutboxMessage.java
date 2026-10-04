package com.mall.pro.module.pay.entity;

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
@TableName("t_outbox")
public class OutboxMessage {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String aggregateType;
    private Long aggregateId;
    private String eventType;
    private String topic;
    private String payload;
    private Integer status;
    private Integer retryCount;
    private String errorMsg;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

}
