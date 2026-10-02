package com.mall.pro.module.seckill.service;

import com.mall.pro.common.BusinessException;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.service.OrderService;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SeckillService {


    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillDeductScript;
    private final TicketCategoryService ticketCategoryService;
    private final OrderService orderService;

    //预热库存
    public void preheatStock(Long ticketCategoryId){
        TicketCategory category = ticketCategoryService.getById(ticketCategoryId);
        if(category == null){
            throw new BusinessException(400,"票档不存在: id="+ticketCategoryId);
        }

        //将数据库的剩余库存写入Redis
        String stockKey = "ticket:stock:"+ ticketCategoryId;
        stringRedisTemplate.opsForValue().set(stockKey,String.valueOf(category.getRemainStock()));

        //清空上一轮测试的已购用户名单
        String userSetKey = "ticket:users:"+ticketCategoryId;
        stringRedisTemplate.delete(userSetKey);

        log.info("[库存预热完成]categoryId={},预热库存={}",ticketCategoryId,category.getRemainStock());
    }

    //秒杀发声：Redis+Lua原子预扣 一人一票
    public TicketOrder seckill(Long userId,Long ticketCategoryId){
        List<String> keys = Arrays.asList(
                "ticket:stock:"+ticketCategoryId,
                "ticket:users:"+ticketCategoryId
        );
        //执行Lua脚本（单人限购1张）
        Long result = stringRedisTemplate.execute(
                seckillDeductScript,
                keys,
                String.valueOf(userId),
                "1"
        );

        if(result == null){
            throw new BusinessException(500,"秒杀服务异常，未获取到执行结果");
        }
        if(result == -1L){
            log.warn("[秒杀限购阻断]用户已购买过该票档：userId={},categoryId={}",userId,ticketCategoryId);
            throw new BusinessException(400,"手慢了，该票档已经售罄！");
        }
        if(result == 0L){
            log.warn("[秒杀售罄阻断]票档库存不足：categoryId={}",ticketCategoryId);
            throw new BusinessException(400,"您已抢购过该票档，每人限购一张");
        }

        //result == 1L:说明成功斩获Redis内存令牌，放行去落库写订单！
        log.info("[秒杀成功-活动创建订单的令牌] userid={},categoryId={}",userId,ticketCategoryId);
        return orderService.createOrder(userId,ticketCategoryId,1);
    }

}
