package com.mall.pro.module.coupon.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.coupon.entity.CouponTemplate;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface CouponTemplateMapper extends BaseMapper<CouponTemplate> {

    @Update("UPDATE d_coupon_template SET remain_quota = remain_quota - 1, update_time = NOW() WHERE id = #{id} AND remain_quota > 0")
    int deductQuota(@Param("id") Long id);
}
