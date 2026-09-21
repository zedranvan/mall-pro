package com.mall.pro.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.entity.SeckillMessage;
import com.mall.pro.entity.SeckillOrder;
import com.mall.pro.mapper.SeckillOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;



@Service
public class SeckillService {
    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    private final ObjectMapper  objectMapper = new ObjectMapper();
    private final Map<Long,Boolean>localSoldOutMap = new ConcurrentHashMap<>();

    //Lua脚本
    private static final String SECKILL_LUA =
            "local stock = redis.call('get', KEYS[1]) " +
                    "if(not stock) then return -1 end " +
                    "if(tonumber(stock) <= 0) then return 0 end " +
                    "redis.call('decr', KEYS[1]) " +
                    "return 1";
    private static final DefaultRedisScript<Long> REDIS_SCIRPT = new DefaultRedisScript<>(SECKILL_LUA,Long.class);


    //核心业务
    //预热redis的初始库存
    public void initStockToRedis(Long goodsId,int count) {
        stringRedisTemplate.opsForValue().set("seckill:stock:"+goodsId,String.valueOf(count));
        localSoldOutMap.put(goodsId,false);
    }
    //动态地址
    public String createPath(Long userId,Long goodsId){
        String path = UUID.randomUUID().toString().replace("-","");
        stringRedisTemplate.opsForValue().set("seckill:path:"+userId+":"+goodsId,path,60,TimeUnit.SECONDS);
        return path;
    }
    //检查地址
    public boolean checkPath(Long userId,Long goodsId,String path){
        if(path == null){
            return false;
        }

        String redisPath = stringRedisTemplate.opsForValue().get("seckill:path:"+userId+":"+goodsId);
        return path.equals(redisPath);
    }
    //预扣和异步消息
    public boolean seckillWithMq(Long goodsId,Long userId){
        //内存标记拦截
        if(Boolean.TRUE.equals(localSoldOutMap.get(goodsId))){
            return false;
        }
        //Redis执行
        String key ="seckill:stock:"+goodsId;
        Long result = stringRedisTemplate.execute(REDIS_SCIRPT,Collections.singletonList(key));
        if (result != null && result == 0L) {

            localSoldOutMap.put(goodsId,true);
        }
        if(result == null || result != 1L){
            return false;
        }
        //redis这里工作完成，发消息给kafka
        try{
            SeckillMessage message = SeckillMessage.builder()
                    .userId(userId)
                    .goodsId(goodsId)
                    .build();
            kafkaTemplate.send("seckill-topic", objectMapper.writeValueAsString(message));
            return true;
        }catch (Exception e){
            return false;
        }
    }

    //查询结果 >0 即orderId，0即排队，-1即售罄
    public Long getseckillResult(Long userId, Long goodsId) {
        SeckillOrder order = seckillOrderMapper.selectOne(
                Wrappers.<SeckillOrder>lambdaQuery()
                        .eq(SeckillOrder::getUserId, userId)
                        .eq(SeckillOrder::getGoodsId, goodsId)
        );
        if (order != null){
            return order.getOrderId();
        }
        return Boolean.TRUE.equals(localSoldOutMap.get(goodsId)) ? -1L : 0L;
    }
    }


