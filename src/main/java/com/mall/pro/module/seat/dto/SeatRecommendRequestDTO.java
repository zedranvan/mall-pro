package com.mall.pro.module.seat.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatRecommendRequestDTO {
    @NotNull(message = "演出ID不能为空")
    private Long programId;

    @NotNull(message = "票档ID不能为空")
    private Long categoryId;

    @NotNull(message = "推荐座位数不能为空")
    @Min(value = 1, message = "推荐座位数至少为1")
    @Max(value = 6, message = "推荐座位数最多为6")
    private Integer count;
}
