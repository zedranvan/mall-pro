package com.mall.pro.module.seat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatUnlockRequestDTO {
    @NotNull(message = "演出ID不能为空")
    private Long programId;

    @NotEmpty(message = "释放座位列表不能为空")
    private List<Long> seatIds;
}
