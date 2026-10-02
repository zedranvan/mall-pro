package com.mall.pro.module.pay.controller;

import com.mall.pro.common.Result;
import com.mall.pro.module.pay.dto.PayNotifyRequest;
import com.mall.pro.module.pay.dto.PrepayResponse;
import com.mall.pro.module.pay.service.PayService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/pay")
@RequiredArgsConstructor
public class PayController {


    private final PayService payService;

    @PostMapping("/prepay")
    public Result<PrepayResponse> prepay(
            @RequestParam("orderId") Long orderId){
        PrepayResponse response = payService.prepay(orderId);
        return Result.success(response);
    }

    @PostMapping("/notify")
    public String payNotify(@RequestBody PayNotifyRequest request){
        boolean success = payService.handleNotify(request);
        return success ? "success" : "fail";

    }
    @PostMapping("/mock/cashier")
    public Result<String> mockCashierPay(@RequestParam("paySn") String paySn){
        String tradeNo = "MOCK_TRADE_"+ UUID.randomUUID().toString().replaceAll("-","").substring(0, 16);

        BigDecimal amount = new BigDecimal("580.00");
        String sign = payService.generateSign(paySn,amount,"SUCCESS");
        PayNotifyRequest notifyRequest = PayNotifyRequest.builder()
                .paySn(paySn)
                .tradeNo(tradeNo)
                .amount(amount)
                .payStatus("SUCCESS")
                .sign(sign)
                .build();
        boolean success = payService.handleNotify(notifyRequest);
        return success ? Result.success("支付成功！已完成扣款与订单履约。"):Result.error(500,"支付失败或验签异常");


    }
}
