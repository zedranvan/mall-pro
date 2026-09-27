package com.mall.pro.controller;

import com.mall.pro.common.Result;
import com.mall.pro.service.SeckillService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
@Tag(name =  "秒杀核心接口",description = "包含库存预热，动态地址获取，异步安全下单与结果轮询")
@RestController
@RequestMapping("/api/seckill")
public class SeckillController {

    @Autowired
    private SeckillService seckillService;
    @Operation(summary = "1.预热商品库存到Redis")
    @PostMapping("/{id}/init")
    public Result<String>initStock(@PathVariable ("id") Long id,@RequestParam (defaultValue = "10")int count){
        seckillService.initStockToRedis(id,count);
        return Result.success("Redis库存预热成功，当前库存："+count);

    }
    @Operation(summary = "2.获取动态秒杀地址令牌(60秒有效)")
    @GetMapping("/{id}/path")
    public Result<String>path(@PathVariable ("id") Long id,@RequestParam Long userId){
        String path = seckillService.createPath(userId ,id);
        return Result.success(path);
   }
    @Operation(summary = "3.异步安全秒杀下单(带动态令牌)")
    @PostMapping("/{id}/{path}/order")
    public Result<String> seckillOrder(
            @PathVariable("id") Long id,
            @PathVariable("path") String path,
            @RequestParam(defaultValue = "1001") Long userId){
        if(!seckillService.checkPath(userId,id,path)){
            return Result.error(403,"非法请求：秒杀地址无效或已过期！");
        }
        boolean success = seckillService.seckillWithMq(id,userId);
        return success?Result.success("排队中，已锁定抢购名额"):Result.error(400,"秒杀失败：已售罄");
    }

    @Operation(summary = "4.前端轮询订单结果")
    @GetMapping("/{id}/result")
    public Result<Long>getResult(@PathVariable("id") Long id,@RequestParam Long userId){
        Long result =seckillService.getseckillResult(userId,id);
        return Result.success(result);

    }



}
