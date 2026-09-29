package com.mall.pro.controller;


import com.mall.pro.common.Result;
import com.mall.pro.entity.TicketOrder;
import com.mall.pro.service.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")

public class OrderController {
    @Autowired
    private OrderService orderService;

    @PostMapping("/create")
    public Result<TicketOrder> createOreder(
            @RequestParam("userId")Long userId,
            @RequestParam("ticketCategoryId") Long ticketCategoryId,
            @RequestParam(value = "count",defaultValue = "1")Integer count
    ){
        TicketOrder order = orderService.createOrder(userId,ticketCategoryId,count);
        return Result.success(order);
    }

    @PostMapping("/{id}/pay")
    public Result<TicketOrder> payOder(@PathVariable Long id){
        TicketOrder order = orderService.payOrder(id);
        return Result.success(order);
    }

    @GetMapping("/{id}")
    public Result<TicketOrder> getOrderById(@PathVariable Long id){
        TicketOrder order = orderService.getOrderById(id);
        return Result.success(order);
    }


    @PostMapping("/{id}/cancel")
    public Result<String> cancelOrder(@PathVariable Long id){
        boolean success = orderService.cancelOrder(id,"用户支持取消");
        if(!success){
            return Result.error(400,"订单取消失败，当前状态不支持取消！");
        }

        return Result.success("订单取消成功，库存已经得到释放");
    }

}
