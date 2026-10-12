package com.mall.pro.module.ticket.service;

import com.mall.pro.common.BusinessException;
import com.mall.pro.common.util.IdCardSecurityUtil;
import com.mall.pro.module.ticket.entity.TicketItem;
import com.mall.pro.module.ticket.entity.TicketTransfer;
import com.mall.pro.module.ticket.mapper.TicketItemMapper;
import com.mall.pro.module.ticket.mapper.TicketTransferMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TicketTransferService {
    private final TicketItemMapper ticketItemMapper;
    private final TicketTransferMapper ticketTransferMapper;
    private final StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    public TicketItem transferTicket(Long ticketItemId, Long fromUserId, Long toUserId, String toRealName, String toIdCard) {
        if (Objects.equals(fromUserId, toUserId)) {
            throw new BusinessException(400, "门票不能转赠给自己！");
        }
        // 🛡️ 防线一：Redis 分布式互斥锁，彻底杜绝“一边进闸机刷码，一边在手机点转赠”
        String lockKey = "lock:ticket:operate:" + ticketItemId;
        Boolean acquired = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "1", 5, TimeUnit.SECONDS);
        if (Boolean.FALSE.equals(acquired)) {
            log.warn("[转赠并发冲突] 门票当前正在核销或操作中: ticketItemId={}", ticketItemId);
            throw new BusinessException(409, "【并发冲突】该门票正在核销或流转中，请稍后再试！");
        }
        try {
            // 步骤 1：状态前置校验
            TicketItem item = ticketItemMapper.selectById(ticketItemId);
            if (item == null) {
                throw new BusinessException(404, "门票不存在: id=" + ticketItemId);
            }
            if (!Objects.equals(item.getUserId(), fromUserId)) {
                throw new BusinessException(403, "非法操作：无权转赠他人名下的门票！");
            }
            if (item.getStatus() == 1) {
                throw new BusinessException(400, "【转赠拒绝】该门票已在现场闸机核销入场，严禁转赠！");
            }
            if (item.getStatus() == 2) {
                throw new BusinessException(400, "【转赠拒绝】该门票已办理退票作废，无法转赠！");
            }

            // 步骤 2：生成全新的防伪二维码与接收人实名脱敏信息 (旧二维码当场作废)
            String newVerifyCode = "TCK_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
            String maskedName = IdCardSecurityUtil.maskName(toRealName);
            String maskedIdCard = IdCardSecurityUtil.maskIdCard(toIdCard);
            String idCardHash = IdCardSecurityUtil.sha256WithSalt(toIdCard);

            // 步骤 3：CAS 原子跃迁所有权与凭证
            int rows = ticketItemMapper.transferTicketCas(
                    ticketItemId, fromUserId, toUserId,
                    newVerifyCode, maskedName, maskedIdCard, idCardHash,
                    LocalDateTime.now()
            );
            if (rows == 0) {
                log.warn("[转赠CAS冲突] 门票状态可能已被抢先核销: ticketItemId={}", ticketItemId);
                throw new BusinessException(409, "转赠处理冲突，门票已被核销或已变更！");
            }

            // 步骤 4：写入审计流水 (满足公安溯源与反作弊审计)
            TicketTransfer transferLog = TicketTransfer.builder()
                    .ticketItemId(ticketItemId)
                    .fromUserId(fromUserId)
                    .toUserId(toUserId)
                    .oldVerifyCode(item.getVerifyCode())
                    .createTime(LocalDateTime.now())
                    .build();
            ticketTransferMapper.insert(transferLog);

            log.info("[转赠成功] ticketId={}, 转出人={}, 接收人={}, 旧防伪码={}, 新防伪码={}",
                    ticketItemId, fromUserId, toUserId, item.getVerifyCode(), newVerifyCode);

            // 刷新并返回最新票据实体
            item.setUserId(toUserId);
            item.setVerifyCode(newVerifyCode);
            item.setRealName(maskedName);
            item.setIdCardMasked(maskedIdCard);
            item.setIdCardHash(idCardHash);
            return item;
        } finally {
            stringRedisTemplate.delete(lockKey);
        }
    }
}
