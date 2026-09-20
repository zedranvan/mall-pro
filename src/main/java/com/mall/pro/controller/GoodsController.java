package com.mall.pro.controller;

import com.mall.pro.common.Result;
import com.mall.pro.entity.Goods;
import com.mall.pro.service.GoodsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/goods")
public class GoodsController {
    @Autowired
    private GoodsService goodsService;

    @GetMapping("/{id}")
    public Result<Goods> getGoods(@PathVariable ("id") Long id){
        Goods goods = goodsService.getById(id);
        if(goods==null){
            return Result.error(404,"商品不存在");
        }
        return Result.success(goods);
    }
    @PostMapping("/{id}/deduct")
    public Result <String> deductStock(@PathVariable ("id") Long id){
        boolean success = goodsService.deducStock(id);
        if(success){
            return Result.success("扣减库存成功");
        }else{
            return Result.error(400,"库存不足，扣减失败");
        }
    }
}
