package com.mall.pro.module.coupon.controller;

import com.mall.pro.common.BusinessException;
import com.mall.pro.common.Result;
import com.mall.pro.common.util.UserContext;
import com.mall.pro.module.coupon.dto.CouponOptimalResultDTO;
import com.mall.pro.module.coupon.dto.CouponTemplateDTO;
import com.mall.pro.module.coupon.dto.CouponUserDTO;
import com.mall.pro.module.coupon.service.CouponService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;

@Tag(name = "营销优惠券资产与最优抵扣中枢")
@RestController
@RequestMapping("/api/coupon")
@RequiredArgsConstructor
public class CouponController {

    private final CouponService couponService;

    @Operation(summary = "全网可领取的优惠券活动模板列表")
    @GetMapping("/templates")
    public Result<List<CouponTemplateDTO>> listTemplates() {
        Long userId = UserContext.getUserId();
        return Result.success(couponService.listTemplates(userId));
    }

    @Operation(summary = "高并发抢领优惠券 (Redis Lua 原子扣发 + 限领防刷)")
    @PostMapping("/claim")
    public Result<CouponUserDTO> claimCoupon(@RequestParam Long templateId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录后再领券！");
        }
        return Result.success(couponService.claimCoupon(userId, templateId));
    }

    @Operation(summary = "查询我的卡包资产列表")
    @GetMapping("/my")
    public Result<List<CouponUserDTO>> listMyCoupons() {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录！");
        }
        return Result.success(couponService.listMyCoupons(userId));
    }

    @Operation(summary = "动态最优用券推荐试算引擎 (输入订单金额，自动计算最优券)")
    @GetMapping("/calculate")
    public Result<CouponOptimalResultDTO> calculateOptimalCoupon(@RequestParam BigDecimal orderAmount) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录！");
        }
        return Result.success(couponService.calculateOptimalCoupon(userId, orderAmount));
    }

    @Operation(summary = "下单冻结优惠券")
    @PostMapping("/freeze")
    public Result<Void> freezeCoupon(@RequestParam Long userCouponId, @RequestParam Long orderId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录！");
        }
        couponService.freezeCoupon(userId, userCouponId, orderId);
        return Result.success();
    }

    @Operation(summary = "解冻释放优惠券")
    @PostMapping("/release")
    public Result<Void> releaseCoupon(@RequestParam Long userCouponId) {
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(401, "请先登录！");
        }
        couponService.releaseCoupon(userId, userCouponId);
        return Result.success();
    }

    @Operation(summary = "运营端优惠券配额预热")
    @PostMapping("/preheat")
    public Result<Void> preheatCoupon(@RequestParam Long templateId) {
        couponService.preheatCoupon(templateId);
        return Result.success();
    }
}
