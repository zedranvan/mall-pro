package com.mall.pro.module.program.entity;

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
@TableName("d_program")
public class Program {

    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String title;
    private String actor;
    private String place;
    private LocalDateTime showTime;
    private LocalDateTime createTime;
}
