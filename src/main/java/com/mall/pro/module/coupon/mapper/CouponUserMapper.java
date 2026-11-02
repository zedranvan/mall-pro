package com.mall.pro.module.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.coupon.entity.CouponUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponUserMapper extends BaseMapper<CouponUser> {

    @Update("UPDATE d_coupon_user SET status = 1, order_id = #{orderId}, update_time = NOW() WHERE id = #{id} AND status = 0 AND user_id = #{userId}")
    int freezeCoupon(@Param("id") Long id, @Param("userId") Long userId, @Param("orderId") Long orderId);

    @Update("UPDATE d_coupon_user SET status = 2, used_time = NOW(), update_time = NOW() WHERE id = #{id} AND status = 1 AND user_id = #{userId}")
    int useCoupon(@Param("id") Long id, @Param("userId") Long userId);

    @Update("UPDATE d_coupon_user SET status = 0, order_id = NULL, update_time = NOW() WHERE id = #{id} AND status = 1 AND user_id = #{userId}")
    int releaseCoupon(@Param("id") Long id, @Param("userId") Long userId);
}
