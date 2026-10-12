package com.mall.pro.module.ticket.service;

import com.mall.pro.common.Result;
import com.mall.pro.common.util.IdCardSecurityUtil;
import com.mall.pro.common.util.OfflineTicketSigner;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 现场闸机边缘离线核验引擎 (Edge Device Offline Engine)
 * 架构规范：严禁注入任何 Mapper 或 Redis！纯纯依托本地公钥与内存运行，模拟断网边缘计算
 */
@Slf4j
@Component
public class OfflineGateEngine {

    // 闸机本地边缘去重缓存 (防止断网期间同一二维码被二次扫码重放)
    private final ConcurrentHashMap<Long, String> offlineVerifiedCache = new ConcurrentHashMap<>();

    /**
     * 闸机断网离线验签核验 (耗时 < 0.2ms)
     */
    public Result<String> verifyOffline(String offlineCert, String gateNo, String idCardNo) {
        int dotIndex = offlineCert.indexOf('.');
        if (dotIndex <= 0) {
            return Result.error(400, "【假票伪造拦截】凭据格式非法！");
        }
        String payload = offlineCert.substring(0, dotIndex);
        String signature = offlineCert.substring(dotIndex + 1);

        // 1. RSA-2048 纯公钥离线验签 (数学逆运算，零网络请求)
        boolean valid = OfflineTicketSigner.verifyTicket(payload, signature);
        if (!valid) {
            log.warn("[闸机离线拦截-假票] 签名失效或被篡改: payload={}", payload);
            return Result.error(403, "【假票伪造拦截】数字证书失效或内容已被黑客篡改！");
        }

        // 2. 解析载荷: ticketId|programId|idCardHash|expireEpoch
        String[] parts = payload.split("\\|");
        Long ticketId = Long.parseLong(parts[0]);
        String idCardHash = parts[2];
        long expireEpoch = Long.parseLong(parts[3]);

        // 3. 校验凭证有效期
        if (System.currentTimeMillis() / 1000 > expireEpoch) {
            return Result.error(400, "【红灯拦截】该门票数字凭证已过期！");
        }

        // 4. 强实名比对 (人证票三合一合规校验)
        if (StringUtils.hasText(idCardNo)) {
            String inputHash = IdCardSecurityUtil.sha256WithSalt(idCardNo);
            if (!inputHash.equals(idCardHash)) {
                log.warn("[闸机离线拦截-冒名] 刷卡身份证与离线凭据实名不符: ticketId={}", ticketId);
                return Result.error(403, "【红灯拦截】人证不一致！非购票实名本人，禁止入场！");
            }
        }

        // 5. 本地防重放去重校验 (防二次刷码作弊)
        String record = gateNo + "@" + LocalDateTime.now();
        String previous = offlineVerifiedCache.putIfAbsent(ticketId, record);
        if (previous != null) {
            log.warn("[闸机离线拦截-二次刷码] 门票已在离线状态核销过: ticketId={}, record={}", ticketId,
                    previous);
            return Result.error(400, "【红灯拦截】该门票在断网期间已被核销入场，严禁重复通行！");
        }

        log.info("[闸机断网离线通行-绿灯放行] 验签极速通过, ticketId={}, gateNo={}", ticketId, gateNo);
        return Result.success("【离线绿灯通行】数字证书与人证核验通过，欢迎入场！闸机: " + gateNo);
    }
}
