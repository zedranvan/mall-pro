package com.mall.pro.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mall.pro.entity.OrderInfo;
import com.mall.pro.entity.SeckillMessage;
import com.mall.pro.entity.SeckillOrder;
import com.mall.pro.mapper.OrderInfoMapper;
import com.mall.pro.mapper.SeckillGoodsMapper;
import com.mall.pro.mapper.SeckillOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Slf4j
@Component
public class SeckillConsumer {

    @Autowired
    private OrderInfoMapper orderInfoMapper;

    @Autowired
    private SeckillGoodsMapper seckillGoodsMapper;

    @Autowired
    private SeckillOrderMapper seckillOrderMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @KafkaListener(topics = "seckill-topic")
    @Transactional(rollbackFor = Exception.class)
    public void handleSeckillMessage(String message) {
       try{
           SeckillMessage msg = objectMapper.readValue(message,SeckillMessage.class);
           Long userId = msg.getUserId();
           Long goodsId = msg.getGoodsId();

           int rows = seckillGoodsMapper.reduceStockAtomic(goodsId);
           if(rows<=0){
           log.warn("[秒杀落库中断]商品库存已经售罄，商品ID：{}" ,goodsId);
           return;
           }
           long orderId = IdWorker.getId();
           OrderInfo orderInfo = OrderInfo.builder()
                   .id(orderId)
                   .userId(userId)
                   .goodsId(goodsId)
                   .goodsName("iPhone 16 PRO MAX 256G(秒杀)")
                   .orderPrice(new BigDecimal("999.00"))
                   .status(0)
                   .createTime(LocalDateTime.now())
                   .build();
           orderInfoMapper.insert(orderInfo);

           SeckillOrder seckillOrder = SeckillOrder.builder()
                   .userId(userId)
                   .orderId(orderId)
                   .goodsId(goodsId)
                   .build();
           seckillOrderMapper.insert(seckillOrder);

           log.info("[kafka异步落库成功] 用户:{}，成功生成订单:{}",userId,goodsId);
       }catch (Exception e){
           log.error("[kafka消费者异常或重复下单拦截]异常信息:{}",e.getMessage());
           TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();

        }
    }
}
