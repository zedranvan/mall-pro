package com.mall.pro.controller;

import com.mall.pro.common.Result;
import com.mall.pro.service.SeckillService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/seckill")
public class SeckillController {
    @Autowired
    private SeckillService seckillService;

    @PostMapping("/{id}/naive")
    public Result<String> naive(@PathVariable("id") Long id) {
        boolean success = seckillService.naiveSeckill(id);
        if(success){
            return Result.success("秒杀成功！抢到商品");
        }else{
            return Result.error(400,"秒杀失败：已售罄");
        }
    }
    @PostMapping("/{id}/safe")
    public Result<String>safe(@PathVariable("id") Long id){
        boolean success = seckillService.safeSeckill(id);
        if(success){
            return Result.success("秒杀成功！抢到商品");
        }else {
            return Result.error(400,"秒杀失败：商品售罄");
        }
    }

    @PostMapping("/{id}/init")
    public Result<String>initStock(@PathVariable("id") Long id, @RequestParam(defaultValue = "10") int count){
        seckillService.initStockToRedis(id,count);
        return Result.success("Reids库存预热成功，当前库存：" +count);

    }

    @PostMapping("/{id}/redis")
    public Result<String> redisSeckill(@PathVariable("id") Long id){{
        boolean success = seckillService.redisSeckill(id);
        if(success){
            return Result.success("秒杀成功！已经获取下单资格");
        }else{
            return Result.error(400,"m秒杀失败，已经售罄");
        }
    }
    }

    @PostMapping("/{id}/mq")
    public Result<String>mq(@PathVariable("id") Long id,@RequestParam(defaultValue = "1001") Long userId){
        boolean success = seckillService.seckillWithMq(id,userId);
        if(success){
            return Result.success("恭喜你当了大冤种，订单在后台骗钱中...");
        }else{
            return Result.error(400,"秒杀失败：已经卖完了，亲");
        }

    }

    @PostMapping("/{id}/result")
    public Result<Long>result(@PathVariable("id") Long id,@RequestParam Long userId){
        Long result = seckillService.getseckillResult(userId,id);
        return Result.success(result);
    }

}
