package com.mall.pro.module.coupon.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.module.coupon.dto.CouponOptimalResultDTO;
import com.mall.pro.module.coupon.dto.CouponTemplateDTO;
import com.mall.pro.module.coupon.dto.CouponUserDTO;
import com.mall.pro.module.coupon.entity.CouponTemplate;
import com.mall.pro.module.coupon.entity.CouponUser;
import com.mall.pro.module.coupon.mapper.CouponTemplateMapper;
import com.mall.pro.module.coupon.mapper.CouponUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class CouponService {

    private final CouponTemplateMapper templateMapper;
    private final CouponUserMapper couponUserMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final DefaultRedisScript<Long> couponClaimScript;

    private static final String QUOTA_KEY_PREFIX = "coupon:quota:";
    private static final String USERS_KEY_PREFIX = "coupon:users:";

    /**
     * 1. 查询当前可用领取的优惠券活动模板列表
     */
    public List<CouponTemplateDTO> listTemplates(Long currentUserId) {
        List<CouponTemplate> templates = templateMapper.selectList(
                new LambdaQueryWrapper<CouponTemplate>()
                        .eq(CouponTemplate::getStatus, 1)
                        .gt(CouponTemplate::getValidEndTime, LocalDateTime.now())
                        .orderByAsc(CouponTemplate::getId)
        );

        List<CouponTemplateDTO> dtoList = new ArrayList<>();
        for (CouponTemplate t : templates) {
            boolean isClaimed = false;
            if (currentUserId != null) {
                Boolean member = stringRedisTemplate.opsForSet().isMember(
                        USERS_KEY_PREFIX + t.getId(), String.valueOf(currentUserId)
                );
                isClaimed = Boolean.TRUE.equals(member);
            }
            dtoList.add(CouponTemplateDTO.builder()
                    .id(t.getId())
                    .name(t.getName())
                    .type(t.getType())
                    .thresholdAmount(t.getThresholdAmount())
                    .discountAmount(t.getDiscountAmount())
                    .discountRate(t.getDiscountRate())
                    .maxDiscountAmount(t.getMaxDiscountAmount())
                    .remainQuota(t.getRemainQuota())
                    .validStartTime(t.getValidStartTime())
                    .validEndTime(t.getValidEndTime())
                    .isClaimed(isClaimed)
                    .build());
        }
        return dtoList;
    }

    /**
     * 2. Redis + Lua 原子扣发与防刷限领 (大促狂欢抢券)
     */
    @Transactional(rollbackFor = Exception.class)
    public CouponUserDTO claimCoupon(Long userId, Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getStatus() != 1) {
            throw new BusinessException(404, "优惠券活动不存在或已下架！");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(template.getValidStartTime()) || now.isAfter(template.getValidEndTime())) {
            throw new BusinessException(400, "该优惠券不在可领取时间范围内！");
        }

        String quotaKey = QUOTA_KEY_PREFIX + templateId;
        String usersKey = USERS_KEY_PREFIX + templateId;

        // 若 Redis 配额尚未预热，则进行惰性预热
        stringRedisTemplate.opsForValue().setIfAbsent(quotaKey, String.valueOf(template.getRemainQuota()));

        // 执行 Lua 脚本原子判定：
        // 返回 1: 成功; -1: 已领过(限领1张); 0: 配额已售罄
        Long result = stringRedisTemplate.execute(
                couponClaimScript,
                Arrays.asList(quotaKey, usersKey),
                String.valueOf(userId)
        );

        if (result == null || result == 0) {
            throw new BusinessException(400, "手慢了，该优惠券配额已被抢光！");
        }
        if (result == -1) {
            throw new BusinessException(400, "您已领取过该优惠券，每人限领一张！");
        }

        // DB 扣减持久层配额
        templateMapper.deductQuota(templateId);

        // 生成唯一防伪券码
        String code = "CP" + System.currentTimeMillis() + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        CouponUser couponUser = CouponUser.builder()
                .couponCode(code)
                .userId(userId)
                .templateId(templateId)
                .status(0) // 未使用
                .build();
        couponUserMapper.insert(couponUser);

        log.info("用户领券成功: userId={}, templateId={}, couponCode={}", userId, templateId, code);

        return buildUserCouponDTO(couponUser, template);
    }

    /**
     * 3. 查询我的优惠券卡包资产
     */
    public List<CouponUserDTO> listMyCoupons(Long userId) {
        List<CouponUser> userCoupons = couponUserMapper.selectList(
                new LambdaQueryWrapper<CouponUser>()
                        .eq(CouponUser::getUserId, userId)
                        .orderByDesc(CouponUser::getCreateTime)
        );
        if (userCoupons.isEmpty()) {
            return Collections.emptyList();
        }

        Set<Long> templateIds = userCoupons.stream().map(CouponUser::getTemplateId).collect(Collectors.toSet());
        Map<Long, CouponTemplate> templateMap = templateMapper.selectBatchIds(templateIds).stream()
                .collect(Collectors.toMap(CouponTemplate::getId, t -> t));

        return userCoupons.stream()
                .map(uc -> buildUserCouponDTO(uc, templateMap.get(uc.getTemplateId())))
                .collect(Collectors.toList());
    }

    /**
     * 4. 动态最优用券推荐算法 (Dynamic Optimal Coupon Calculation Engine)
     */
    public CouponOptimalResultDTO calculateOptimalCoupon(Long userId, BigDecimal orderAmount) {
        if (orderAmount == null || orderAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new BusinessException(400, "订单计算金额必须大于 0！");
        }

        List<CouponUser> myCoupons = couponUserMapper.selectList(
                new LambdaQueryWrapper<CouponUser>()
                        .eq(CouponUser::getUserId, userId)
                        .eq(CouponUser::getStatus, 0) // 仅筛选未使用的券
        );

        if (myCoupons.isEmpty()) {
            return CouponOptimalResultDTO.builder()
                    .orderAmount(orderAmount)
                    .recommendedCoupon(null)
                    .maxDiscountAmount(BigDecimal.ZERO)
                    .finalPayAmount(orderAmount)
                    .availableCoupons(Collections.emptyList())
                    .unavailableCoupons(Collections.emptyList())
                    .build();
        }

        Set<Long> templateIds = myCoupons.stream().map(CouponUser::getTemplateId).collect(Collectors.toSet());
        Map<Long, CouponTemplate> templateMap = templateMapper.selectBatchIds(templateIds).stream()
                .collect(Collectors.toMap(CouponTemplate::getId, t -> t));

        List<CouponUserDTO> availableCoupons = new ArrayList<>();
        List<CouponUserDTO> unavailableCoupons = new ArrayList<>();

        LocalDateTime now = LocalDateTime.now();

        for (CouponUser uc : myCoupons) {
            CouponTemplate template = templateMap.get(uc.getTemplateId());
            if (template == null) continue;

            CouponUserDTO dto = buildUserCouponDTO(uc, template);

            // 规则核验 1：是否已过期
            if (now.isAfter(template.getValidEndTime())) {
                dto.setCanUse(false);
                dto.setCannotUseReason("优惠券已过有效期");
                dto.setCalculatedDiscount(BigDecimal.ZERO);
                unavailableCoupons.add(dto);
                continue;
            }

            // 规则核验 2：门槛金额核验
            if (orderAmount.compareTo(template.getThresholdAmount()) < 0) {
                dto.setCanUse(false);
                dto.setCannotUseReason(String.format("未达到使用门槛 (满 ¥%s 可用)", template.getThresholdAmount()));
                dto.setCalculatedDiscount(BigDecimal.ZERO);
                unavailableCoupons.add(dto);
                continue;
            }

            // 计算具体减免金额
            BigDecimal discount = BigDecimal.ZERO;
            if (template.getType() == 1 || template.getType() == 3) {
                // 满减券 或 无门槛立减券
                discount = template.getDiscountAmount().min(orderAmount);
            } else if (template.getType() == 2) {
                // 折扣券 (例如 0.85 折，减免 15%)
                BigDecimal rate = BigDecimal.ONE.subtract(template.getDiscountRate());
                discount = orderAmount.multiply(rate).setScale(2, RoundingMode.HALF_UP);
                if (template.getMaxDiscountAmount() != null && discount.compareTo(template.getMaxDiscountAmount()) > 0) {
                    discount = template.getMaxDiscountAmount();
                }
            }

            dto.setCanUse(true);
            dto.setCalculatedDiscount(discount);
            availableCoupons.add(dto);
        }

        // 贪心排序：按减免金额由高到低排序，寻找最优解
        availableCoupons.sort((a, b) -> b.getCalculatedDiscount().compareTo(a.getCalculatedDiscount()));

        CouponUserDTO recommended = availableCoupons.isEmpty() ? null : availableCoupons.get(0);
        BigDecimal maxDiscount = (recommended != null) ? recommended.getCalculatedDiscount() : BigDecimal.ZERO;
        BigDecimal finalPay = orderAmount.subtract(maxDiscount).max(new BigDecimal("0.01"));

        return CouponOptimalResultDTO.builder()
                .orderAmount(orderAmount)
                .recommendedCoupon(recommended)
                .maxDiscountAmount(maxDiscount)
                .finalPayAmount(finalPay)
                .availableCoupons(availableCoupons)
                .unavailableCoupons(unavailableCoupons)
                .build();
    }

    /**
     * 5. 下单冻结优惠券 (Atomic Freeze: status 0 -> 1)
     */
    @Transactional(rollbackFor = Exception.class)
    public void freezeCoupon(Long userId, Long userCouponId, Long orderId) {
        int updated = couponUserMapper.freezeCoupon(userCouponId, userId, orderId);
        if (updated == 0) {
            throw new BusinessException(409, "优惠券已被冻结或已使用，不可重复抵扣！");
        }
        log.info("优惠券下单锁定成功: userCouponId={}, orderId={}, userId={}", userCouponId, orderId, userId);
    }

    /**
     * 6. 支付完成核销优惠券 (Atomic Use: status 1 -> 2)
     */
    @Transactional(rollbackFor = Exception.class)
    public void useCoupon(Long userId, Long userCouponId) {
        int updated = couponUserMapper.useCoupon(userCouponId, userId);
        if (updated == 0) {
            throw new BusinessException(400, "优惠券核销失败：非冻结中状态！");
        }
        log.info("优惠券核销成功: userCouponId={}, userId={}", userCouponId, userId);
    }

    /**
     * 7. 关单/退款解冻回滚优惠券 (Atomic Release: status 1 -> 0)
     */
    @Transactional(rollbackFor = Exception.class)
    public void releaseCoupon(Long userId, Long userCouponId) {
        int updated = couponUserMapper.releaseCoupon(userCouponId, userId);
        if (updated == 0) {
            log.warn("优惠券解除冻结未更新任何行: userCouponId={}, userId={}", userCouponId, userId);
            return;
        }
        log.info("优惠券解除冻结自愈成功: userCouponId={}, userId={}", userCouponId, userId);
    }

    /**
     * 8. 预热/重置优惠券活动配额与名单
     */
    public void preheatCoupon(Long templateId) {
        CouponTemplate template = templateMapper.selectById(templateId);
        if (template == null) {
            throw new BusinessException(404, "优惠券不存在");
        }
        String quotaKey = QUOTA_KEY_PREFIX + templateId;
        String usersKey = USERS_KEY_PREFIX + templateId;

        stringRedisTemplate.opsForValue().set(quotaKey, String.valueOf(template.getRemainQuota()));
        stringRedisTemplate.delete(usersKey);
        log.info("优惠券配额预热完成: templateId={}, 配额={}", templateId, template.getRemainQuota());
    }

    private CouponUserDTO buildUserCouponDTO(CouponUser uc, CouponTemplate template) {
        String statusDesc = "未使用";
        if (uc.getStatus() == 1) statusDesc = "下单冻结中";
        else if (uc.getStatus() == 2) statusDesc = "已核销使用";
        else if (uc.getStatus() == 3) statusDesc = "已失效";

        return CouponUserDTO.builder()
                .id(uc.getId())
                .couponCode(uc.getCouponCode())
                .templateId(uc.getTemplateId())
                .templateName(template != null ? template.getName() : "")
                .type(template != null ? template.getType() : 1)
                .thresholdAmount(template != null ? template.getThresholdAmount() : BigDecimal.ZERO)
                .discountAmount(template != null ? template.getDiscountAmount() : BigDecimal.ZERO)
                .discountRate(template != null ? template.getDiscountRate() : null)
                .maxDiscountAmount(template != null ? template.getMaxDiscountAmount() : null)
                .status(uc.getStatus())
                .statusDesc(statusDesc)
                .orderId(uc.getOrderId())
                .validEndTime(template != null ? template.getValidEndTime() : null)
                .build();
    }
}
