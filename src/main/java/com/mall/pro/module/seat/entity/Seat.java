package com.mall.pro.module.seat.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 场馆物理选座实体
 * seatIndex: 对应 Redis Bitmap 全局连续 bit 偏移下标 (0-based)
 * status: 0-可选/空闲, 1-锁定中, 2-已售出
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("d_seat")
public class Seat {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;

    private Long programId;
    private Long categoryId;
    private String area;
    private Integer rowNum;
    private Integer colNum;
    private Integer seatIndex;
    
    /**
     * 0: 可选 (Available)
     * 1: 锁定待付款 (Locked)
     * 2: 已售出 (Sold)
     */
    private Integer status;

    private Long lockUserId;
    private LocalDateTime lockTime;

    @Version
    private Integer version;

    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
