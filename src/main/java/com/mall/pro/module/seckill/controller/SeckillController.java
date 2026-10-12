package com.mall.pro.module.seckill.controller;


import com.mall.pro.common.Result;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.seckill.service.SeckillService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/seckill")
@RequiredArgsConstructor
public class SeckillController {

    private final SeckillService seckillService;

    /**
     * 运营/测试段预热票档库存到 Redis
     */
    @PostMapping("preheat")
    public Result<String> preheat(@RequestParam("ticketCategoryId") Long ticketCategoryId){
        seckillService.preheatStock(ticketCategoryId);
        return Result.success("票档库存预热成功！");
    }

    @PostMapping("/order")
    public Result<TicketOrder> seckill(
            @RequestParam("ticketCategoryId") Long ticketCategoryId,
            @RequestParam(value = "attendeeId", required = false) Long attendeeId) {

        // 核心安全防线：从 JWT 线程上下文中无感提取当前用户，彻底根治水平越权！
        Long currentUserId = com.mall.pro.common.util.UserContext.getUserId();
        TicketOrder order = seckillService.seckill(currentUserId, ticketCategoryId, attendeeId);
        return Result.success(order);
    }

}
