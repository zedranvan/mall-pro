package com.mall.pro.module.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatDTO {
    private Long id;
    private Long programId;
    private Long categoryId;
    private String area;
    private Integer rowNum;
    private Integer colNum;
    private Integer seatIndex;
    private Integer status;
    private Long remainingLockSeconds;
    private Boolean isMyLocked;
}
