package com.mall.pro.module.seat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatLockResponseDTO {
    private List<SeatDTO> lockedSeats;
    private Long lockExpireSeconds;
    private String message;
}
