package com.mall.pro.module.pay.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import com.mall.pro.module.pay.dto.PayNotifyRequest;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;

@Component
public class PaySignHelper {public static final String PAY_SECRET = "MALL_PAY_SECRET_8888";

    public String generateSign(String paySn, BigDecimal amount, String payStatus) {
        try {
            String rawText = String.format("amount=%s&paySn=%s&payStatus=%s",
                    amount.setScale(2, RoundingMode.HALF_UP), paySn, payStatus);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(PAY_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] hash = mac.doFinal(rawText.getBytes(StandardCharsets.UTF_8));

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

}
