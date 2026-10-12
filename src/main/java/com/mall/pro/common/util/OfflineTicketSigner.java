package com.mall.pro.common.util;

import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Base64;

@Slf4j
public class OfflineTicketSigner {
    private static final String ALGORITHM = "RSA";
    private static final String SIGN_ALGORITHM = "SHA256withRSA";

    // 生产环境中公钥预先烧录进闸机，私钥由服务端 KMS 保管
    private static final PrivateKey PRIVATE_KEY;
    private static final PublicKey PUBLIC_KEY;

    static {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(2048);
            KeyPair keyPair = generator.generateKeyPair();
            PRIVATE_KEY = keyPair.getPrivate();
            PUBLIC_KEY = keyPair.getPublic();
        } catch (Exception e) {
            throw new RuntimeException("初始化 RSA 离线验签密钥对失败", e);
        }
    }
    /**
     * 1. 服务端私钥签名：生成离线二维码票据凭证
     * 载荷格式: ticketId|programId|idCardHash|expireEpochSeconds
     */
    public static String signTicket(Long ticketId, Long programId, String idCardHash, long
            expireEpochSeconds) {
        try {
            String payload = String.format("%d|%d|%s|%d", ticketId, programId, idCardHash,
                    expireEpochSeconds);
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initSign(PRIVATE_KEY);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            byte[] signBytes = signature.sign();
            String signBase64 = Base64.getUrlEncoder().withoutPadding().encodeToString(signBytes);
            return payload + "." + signBase64;
        } catch (Exception e) {
            log.error("[离线票据签名失败] ticketId={}", ticketId, e);
            throw new RuntimeException("生成离线防伪凭据失败", e);
        }
    }

    /**
     * 2. 闸机公钥离线验签（完全断网环境下纯数学验证，耗时 < 0.5ms）
     */
    public static boolean verifyTicket(String payload, String signBase64) {
        try {
            byte[] signBytes = Base64.getUrlDecoder().decode(signBase64);
            Signature signature = Signature.getInstance(SIGN_ALGORITHM);
            signature.initVerify(PUBLIC_KEY);
            signature.update(payload.getBytes(StandardCharsets.UTF_8));
            return signature.verify(signBytes);
        } catch (Exception e) {
            log.warn("[离线凭据验签异常] 数据可能被非法篡改", e);
            return false;
        }
    }


}
