package com.mall.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.entity.OrderInfo;
import org.apache.ibatis.annotations.Mapper;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface OrderInfoMapper extends BaseMapper<OrderInfo> {

    /**
     * 乐观锁 + 状态机原子跃迁：
     * 只有当前数据库中的状态等于 expectedStatus，且版本号等于 currentVersion 时，才允许跃迁！
     * 成功执行后版本号 version 自增 1。
     */
    @Update("UPDATE order_info SET " +
            "status = #{targetStatus}, " +
            "version = version + 1, " +
            "update_time = CURRENT_TIMESTAMP, " +
            "pay_time = CASE WHEN #{targetStatus} = 1 THEN CURRENT_TIMESTAMP ELSE pay_time END " +
            "WHERE id = #{orderId} AND status = #{expectedStatus} AND version = #{currentVersion}")
    int transitionStatusWithOptimisticLock(
            @Param("orderId") Long orderId,
            @Param("expectedStatus") int expectedStatus,
            @Param("targetStatus") int targetStatus,
            @Param("currentVersion") int currentVersion);
}
