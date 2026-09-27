package com.mall.pro.controller;

import com.mall.pro.common.Result;
import com.mall.pro.entity.TicketCategory;
import com.mall.pro.service.TicketCategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@RestController
@RequestMapping("/api/tickets")
public class TicketCategoryController {

    @Autowired
    private TicketCategoryService ticketCategoryService;

    @GetMapping("/category/{id}")
    public Result<TicketCategory>getCategory(@PathVariable Long id){
        TicketCategory category = ticketCategoryService.getById(id);
        return Result.success(category);
    }
}
