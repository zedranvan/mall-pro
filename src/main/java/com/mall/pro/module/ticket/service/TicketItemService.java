package com.mall.pro.module.ticket.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.Result;
import com.mall.pro.common.util.OfflineTicketSigner;
import com.mall.pro.module.ticket.entity.TicketItem;
import com.mall.pro.module.ticket.mapper.TicketItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketItemService {

    private final TicketItemMapper ticketItemMapper;
    private final StringRedisTemplate stringRedisTemplate;
    // 闸机边缘设备本地核销记录缓存 (断网防二次重放刷码)
    private final ConcurrentHashMap<Long, String> offlineVerifiedCache = new ConcurrentHashMap<>();


    /** 查询用户名下拥有的所有真实的票据 **/
    public List<TicketItem> getMyTickets(Long userId) {
        return ticketItemMapper.selectList(
                new LambdaQueryWrapper<TicketItem>()
                        .eq(TicketItem::getUserId, userId)
                        .orderByDesc(TicketItem::getCreateTime)
        );
    }

    public Result<String> verifyTicket(String verifyCode, String gateNo, String idCardNo) {
        // 步骤 1：先查出票据实体
        TicketItem item = ticketItemMapper.selectOne(
                new LambdaQueryWrapper<TicketItem>().eq(TicketItem::getVerifyCode, verifyCode)
        );
        if (item == null) {
            log.warn("[闸机拦截-假票] 查无此防伪码: verifyCode={}", verifyCode);
            return Result.error(404, "【假票伪造拦截】查无此门票防伪码，严禁入场！");
        }

        // 步骤 2：上全局互斥锁（与转赠共享 lock:ticket:operate:{ticketId}，防一边转赠一边进场）
        String lockKey = "lock:ticket:operate:" + item.getId();
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[闸机作弊拦截] 检测到该门票正在进行转赠或核销操作: ticketId={}", item.getId());
            return Result.error(409, "【黄牛作弊拦截】该门票正在转赠或核销中，禁止同时刷码！");
        }

        try {
            if (item.getStatus() == 1) {
                log.warn("[闸机拦截-二次刷码] 门票已被核销: verifyCode={}, 首次核销闸机={}, 时间={}",
                        verifyCode, item.getGateNo(), item.getVerifyTime());
                return Result.error(400, String.format("【红灯拦截】门票已被核销！已于 %s 在 %s 闸机刷码进场，严禁重复入场！",
                        item.getVerifyTime(), item.getGateNo()));
            }
            if (item.getStatus() == 2) {
                log.warn("[闸机拦截-废票] 门票已办理退票作废: verifyCode={}", verifyCode);
                return Result.error(400, "【红灯拦截】该门票已办理退票作废，禁止入场！");
            }

            // 步骤 3：强实名合规比对 (人证票合一)
            if (org.springframework.util.StringUtils.hasText(idCardNo) && org.springframework.util.StringUtils.hasText(item.
                    getIdCardHash())) {
                String inputHash = com.mall.pro.common.util.IdCardSecurityUtil.sha256WithSalt(idCardNo);
                if (!inputHash.equals(item.getIdCardHash())) {
                    log.warn("[闸机拦截-人证不符] 刷卡身份证与门票绑定实名不符: 票号={}, 刷卡={}",
                            verifyCode, com.mall.pro.common.util.IdCardSecurityUtil.maskIdCard(idCardNo));
                    return Result.error(403, "【红灯拦截】人证不一致！非购票实名本人，禁止入场！");
                }
            }

            LocalDateTime now = LocalDateTime.now();
            // 步骤 4：数据库 CAS 原子跃迁 (WHERE verify_code = ? AND status = 0)
            int rows = ticketItemMapper.verifyTicketCas(verifyCode, gateNo, now, now);
            if (rows > 0) {
                log.info("[闸机核销成功-绿灯放行] verifyCode={}, gateNo={}", verifyCode, gateNo);
                return Result.success("【绿灯通行】人证票三合一核验通过，欢迎入场！闸机: " + gateNo);
            }

            return Result.error(400, "【状态异常】当前门票状态不支持入场: status=" + item.getStatus());
        } finally {
            stringRedisTemplate.delete(lockKey);
        }

    }
    /**
     * 5. 服务端私钥签发离线防伪凭据（供用户手机端展示离线二维码）
     */
    public Result<String> getOfflineCert(Long ticketItemId, Long userId) {
        TicketItem item = ticketItemMapper.selectById(ticketItemId);
        if (item == null) {
            return Result.error(404, "门票不存在！");
        }
        if (!item.getUserId().equals(userId)) {
            return Result.error(403, "非法操作：无权获取他人门票凭证！");
        }
        if (item.getStatus() != 0) {
            return Result.error(400, "当前门票状态不支持入场 (已核销或已退票)！");
        }

        // 设定凭证有效截止时间为开演后 3 天 (Epoch 秒)
        long expireEpoch = System.currentTimeMillis() / 1000 + 86400 * 3;
        String cert = com.mall.pro.common.util.OfflineTicketSigner.signTicket(
                item.getId(), item.getProgramId(), item.getIdCardHash(), expireEpoch
        );
        return Result.success(cert);
    }
}
