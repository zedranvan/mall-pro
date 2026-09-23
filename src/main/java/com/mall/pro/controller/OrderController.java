package com.mall.pro.controller;

import com.mall.pro.common.OrderStatus;
import com.mall.pro.common.Result;
import com.mall.pro.service.OrderStateMachineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Tag(name = "订单履约与状态机接口", description = "支持订单状态合法跃迁、版本号防乱序与事件派发")
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderStateMachineService orderStateMachineService;

    @Operation(summary = "触发订单状态机流转")
    @PostMapping("/{orderId}/transition")
    public Result<String> transitionStatus(
            @PathVariable("orderId") Long orderId,
            @RequestParam("targetStatus") int targetStatusCode) {
        try {
            OrderStatus target = OrderStatus.fromCode(targetStatusCode);
            boolean success = orderStateMachineService.transition(orderId, target);
            return Result.success("订单状态已成功跃迁至: " + target.getDescription());
        } catch (IllegalArgumentException | IllegalStateException e) {
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            return Result.error(500, "状态流转冲突: " + e.getMessage());
        }
    }
}
