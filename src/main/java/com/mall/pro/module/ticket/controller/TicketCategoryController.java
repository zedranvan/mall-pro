package com.mall.pro.module.ticket.controller;

import com.mall.pro.common.Result;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketCategoryController {


    private final TicketCategoryService ticketCategoryService;

    @GetMapping("/category/{id}")
    public Result<TicketCategory> getCategoryById(@PathVariable Long id) {
        TicketCategory category = ticketCategoryService.getById(id);
        return Result.success(category);
    }

    @PostMapping("/category/{id}/deduct")
    public Result<String> deductStock(
            @PathVariable Long id,
            @RequestParam(defaultValue = "1") Integer count
    ) {
        boolean success = ticketCategoryService.deductStock(id, count);
        if (success) {
            return Result.success("扣减成功");
        } else {
            return Result.error(400, "库存不足或已售罄");
        }
    }
}
