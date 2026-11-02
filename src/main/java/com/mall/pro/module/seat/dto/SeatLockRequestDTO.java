package com.mall.pro.module.seat.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatLockRequestDTO {
    @NotNull(message = "演出ID不能为空")
    private Long programId;

    @NotEmpty(message = "请至少选择一个座位")
    @Size(max = 6, message = "单次选座最多不可超过6个")
    private List<Long> seatIds;
}
