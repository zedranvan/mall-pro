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
            @RequestParam("userId") Long userId,
            @RequestParam("ticketCategoryId") Long ticketCategoryId){
        TicketOrder order = seckillService.seckill(userId,ticketCategoryId);
        return Result.success(order);
    }

}
