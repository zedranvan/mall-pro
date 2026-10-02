package com.mall.pro.module.pay.service;

import com.mall.pro.common.BusinessException;
import com.mall.pro.module.order.entity.TicketOrder;
import com.mall.pro.module.order.enums.OrderStatus;
import com.mall.pro.module.order.service.OrderService;
import com.mall.pro.module.pay.dto.PayNotifyRequest;
import com.mall.pro.module.pay.dto.PrepayResponse;
import com.mall.pro.module.pay.entity.PayRecord;
import com.mall.pro.module.pay.enums.PayStatus;
import com.mall.pro.module.pay.mapper.PayRecordMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.RoundingMode;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class PayService {


    private final PayRecordMapper payRecordMapper;
    private final OrderService orderService;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> seckillDeductScript;


    @Transactional(rollbackFor = Exception.class)
    public PrepayResponse prepay(Long orderId) {
        TicketOrder order = orderService.getOrderById(orderId);
        if (order == null) {
            throw new BusinessException(404, "订单不存在:id=" + orderId);
        }
        if (order.getStatus() != OrderStatus.CREATED.getCode()) {
            throw new BusinessException(400, "订单当前状态无法发起支付！当前状态： "
                    + order.getStatus());
        }
        String timeStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String paySn = "PAY_" + timeStr + "_" + UUID.randomUUID().toString().substring(0, 6).toLowerCase();

        PayRecord record = PayRecord.builder()
                .paySn(paySn)
                .orderId(order.getId())
                .userId(order.getUserId())
                .payChannel("MOCK_WECHAT")
                .amount(order.getOrderPrice())
                .status(PayStatus.INIT.getCode())
                .createTime(LocalDateTime.now())
                .build();
        payRecordMapper.insert(record);
        log.info("[预下单成功]生成支付流水:paySn={},orderId={},amount={}",
                paySn, orderId, order.getOrderPrice());

        return PrepayResponse.builder()
                .paySn(paySn)
                .orderId(orderId)
                .amount(order.getOrderPrice())
                .cashierUrl("http://localhost:8080/api/pay/mock/cashier?paySn=" + paySn)
                .build();
    }

    public static final String PAY_SECRET = "MALL_PAY_SECRET_8888";

    public String generateSign(String paySn, java.math.BigDecimal amount, String payStatus) {
        try {
            String rawText = String.format("amount=%s&paySn=%s&payStatus=%s",
                    amount.setScale(2, RoundingMode.HALF_UP), paySn, payStatus);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(PAY_SECRET.getBytes(java.nio.charset
                    .StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawText.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                hexString.append(String.format("%02x", b));
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("生成签名失败", e);
        }
    }

    public boolean verifySign(PayNotifyRequest request) {
        if (request == null || request.getSign() == null) {
            return false;
        }
        String calculatedSign = generateSign(
                request.getPaySn(),
                request.getAmount(),
                request.getPayStatus());
        return calculatedSign.equalsIgnoreCase(request.getSign());
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean handleNotify(PayNotifyRequest request) {
        // 验证签名
        if(!verifySign(request)){

        log.warn("[支付回调]签名检验失败，paySn={}",request.getPaySn());
        return false;
        }

        //查流水单号
        PayRecord record = payRecordMapper.selectOne(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<PayRecord>()
                        .eq(PayRecord::getPaySn,request.getPaySn())
        );
        if(record == null){
            log.warn("[支付回调]流水不存在，paySn={}",request.getPaySn());
            return false;
        }
        //幂等性检验
        if(java.util.Objects.equals(record.getStatus(),PayStatus.SUCCESS.getCode())){
            log.info("[支付回调]该流水已处理成功，忽略重复回调:paySn={}",
                    request.getPaySn());
                    return true;
        }
        //金额精确比对
        if(record.getAmount().compareTo(request.getAmount()) != 0){
            log.error("[支付回调] 金额篡改告警！库内金额={},回调金额={},paySn={}",
                    record.getAmount(),request.getAmount(),request.getPaySn());
            return false;
        }
        //状态跃迁与订单履约
        record.setStatus(PayStatus.SUCCESS.getCode());
        record.setTradeNo(request.getTradeNo());
        record.setPayTime(LocalDateTime.now());
        payRecordMapper.updateById(record);

        orderService.payOrder(record.getOrderId());
        log.info("[支付回调成功]支付流水与订单状态完成跃迁:paySn={},orderId={}",request.getPaySn(),record.getOrderId());

        return true;
        }

        public void seckill(Long userId,Long ticketCategoryId,Integer count){
            List<String> keys = Arrays.asList(
                    "ticket:stock:"+ticketCategoryId,
                    "ticket:users:"+ticketCategoryId
            );
            Long result = stringRedisTemplate.execute(
                    seckillDeductScript,
                    keys,
                    String.valueOf(userId),
                    String.valueOf(count)
            );

            if(result == -1L){
                throw new BusinessException(400,"您已经购买过该票档，一人限购一张！");
            }
            if(result == 0L){
                throw new BusinessException(400,"手慢了，该票档已经被售罄！");
            }
        }
    }

