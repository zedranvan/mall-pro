package com.mall.pro.module.seat.controller;

import com.mall.pro.common.BusinessException;
import com.mall.pro.common.Result;
import com.mall.pro.common.util.UserContext;
import com.mall.pro.module.seat.dto.*;
import com.mall.pro.module.seat.service.SeatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "场馆物理选座与位图并发中枢")
@RestController
@RequestMapping("/api/seats")
@RequiredArgsConstructor
public class SeatController {

    private final SeatService seatService;

    @Operation(summary = "全景座位大厅状态拉取 (基于 Redis Bitmap 毫秒级核验)")
    @GetMapping("/map")
    public Result<List<SeatDTO>> getSeatMap(
            @RequestParam Long programId,
            @RequestParam(required = false) Long categoryId) {
        Long userId = UserContext.getUserId();
        return Result.success(seatService.getSeatMap(programId, categoryId, userId));
    }

    @Operation(summary = "原子锁定物理座位 (Bitmap 占位 + 5分钟排他租约)")
    @PostMapping("/lock")
    public Result<SeatLockResponseDTO> lockSeats(@Valid @RequestBody SeatLockRequestDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再选择座位！");
        }
        return Result.success(seatService.lockSeats(userId, dto.getProgramId(), dto.getSeatIds()));
    }

    @Operation(summary = "主动释放/取消锁定座位")
    @PostMapping("/unlock")
    public Result<Void> unlockSeats(@Valid @RequestBody SeatUnlockRequestDTO dto) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录！");
        }
        seatService.unlockSeats(userId, dto.getProgramId(), dto.getSeatIds());
        return Result.success();
    }

    @Operation(summary = "滑动窗口连续相邻座位智能推荐")
    @GetMapping("/recommend")
    public Result<List<SeatDTO>> recommendAdjacentSeats(
            @RequestParam Long programId,
            @RequestParam Long categoryId,
            @RequestParam(defaultValue = "2") Integer count) {
        return Result.success(seatService.recommendAdjacentSeats(programId, categoryId, count));
    }

    @Operation(summary = "场馆位图与状态全量预热/重置")
    @PostMapping("/sync")
    public Result<Void> syncVenueBitmap(@RequestParam Long programId) {
        seatService.syncVenueBitmap(programId);
        return Result.success();
    }
}
