package com.mall.pro.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.mall.pro.entity.SeckillGoods;
import com.mall.pro.entity.SeckillOrder;
import com.mall.pro.mapper.SeckillGoodsMapper;
import com.mall.pro.mapper.SeckillOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static java.util.UUID.randomUUID;

@Service
public class SeckillService {

    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> localSoldOutMap = new java.util.concurrent.ConcurrentHashMap<>();
    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private StringRedisTemplate stringRedisTemplate;
    //定义lua脚本字符串，保证差库存，扣减的绝对原子性
    private static final String SECKILL_LUA =
            "local stock = redis.call('get',KEYS[1]) " +
            "if(not stock) then return -1 end " +
            "if(tonumber(stock)<= 0) then return 0 end "+
            "redis.call('decr',KEYS[1]) " +
            "return 1";
    private static final DefaultRedisScript<Long> REDIS_SCRIPT = new DefaultRedisScript<>(SECKILL_LUA,Long.class);

    public void initStockToRedis(Long seckillGoodsId,int count){
        stringRedisTemplate.opsForValue().set("seckill:stock:"+seckillGoodsId,String.valueOf(count));
        localSoldOutMap.put(seckillGoodsId,false);
    }
    public boolean redisSeckill(Long seckillGoodsId){
        if(Boolean.TRUE.equals(localSoldOutMap.get(seckillGoodsId))){
            return false;
        }
        String key = "seckill:stock:"+seckillGoodsId;
        Long result = stringRedisTemplate.execute(REDIS_SCRIPT, Collections.singletonList(key));
        if(result != null && result ==0L){
            localSoldOutMap.put(seckillGoodsId,true);
        }
        return result != null && result  ==1L;
    }

    @Autowired
    private org.springframework.kafka.core.KafkaTemplate<String, String> kafkaTemplate;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    public boolean seckillWithMq(Long seckillGoodsId, Long userId) {
        // 【步骤 1】Redis Lua 脚本内存极速预扣库存 (0.8ms)
        boolean success = redisSeckill(seckillGoodsId);
        if (!success) {
            return false;
        }

        // 【步骤 2】使用 Builder 模式打包强类型消息并发送 Kafka
        try {
            com.mall.pro.entity.SeckillMessage message = com.mall.pro.entity.SeckillMessage.builder()
                    .userId(userId)
                    .goodsId(seckillGoodsId)
                    .build();
            String json = objectMapper.writeValueAsString(message);
            kafkaTemplate.send("seckill-topic", json);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean naiveSeckill(Long seckillGoodsId){
        SeckillGoods goods = seckillGoodsMapper.selectById(seckillGoodsId);
            if(goods == null){
                return false;
            }
            if (goods.getStockCount() <= 0){
                return false;
            }

            try{
                Thread.sleep(10);
            }catch(InterruptedException ignored){

            }
            goods.setStockCount(goods.getStockCount() - 1);
            seckillGoodsMapper.updateById(goods);
            return true;
        }

        public boolean safeSeckill(Long seckillGoodsId){
            int affectedRows = seckillGoodsMapper.reduceStockAtomic(seckillGoodsId);
            return affectedRows > 0;
        }

        @Autowired
        private SeckillOrderMapper seckillOrderMapper;
        public Long getseckillResult(Long userId,Long goodsId){
            SeckillOrder order = seckillOrderMapper.selectOne(
                    Wrappers.<SeckillOrder>lambdaQuery()
                            .eq(SeckillOrder::getUserId,userId)
                            .eq(SeckillOrder::getGoodsId,goodsId)
            );

            if(order != null){
                return order.getOrderId();
            }
            return Boolean.TRUE.equals(localSoldOutMap.get(goodsId)) ? -1L : 0L;

        }

        public String createPath(Long userId,Long goodsId){
            String path = randomUUID().toString().replace("_","");
            stringRedisTemplate.opsForValue().set("seckill:path:"+userId+":"+goodsId,path,60, TimeUnit.SECONDS);
            return path;
        }

        public boolean checkPath(Long userId,Long goodsid,String path){
            if(path == null){
                return false;
            }
            String redisPath = stringRedisTemplate.opsForValue().get("seckill:path:"+userId+":"+goodsid);
            return path.equals(redisPath);
        }

    }

