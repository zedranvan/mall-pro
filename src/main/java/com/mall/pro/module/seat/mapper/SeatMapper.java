package com.mall.pro.module.seat.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.seat.entity.Seat;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SeatMapper extends BaseMapper<Seat> {

    @Update("UPDATE d_seat SET status = 1, lock_user_id = #{userId}, lock_time = NOW(), version = version + 1 WHERE id = #{seatId} AND status = 0")
    int lockSeat(@Param("seatId") Long seatId, @Param("userId") Long userId);

    @Update("UPDATE d_seat SET status = 0, lock_user_id = NULL, lock_time = NULL, version = version + 1 WHERE id = #{seatId} AND lock_user_id = #{userId} AND status = 1")
    int unlockSeat(@Param("seatId") Long seatId, @Param("userId") Long userId);

    @Update("UPDATE d_seat SET status = 0, lock_user_id = NULL, lock_time = NULL, version = version + 1 WHERE id = #{seatId} AND status = 1")
    int resetTimeoutSeat(@Param("seatId") Long seatId);

    @Update("UPDATE d_seat SET status = 2, version = version + 1 WHERE id = #{seatId} AND status = 1 AND lock_user_id = #{userId}")
    int markSoldSeat(@Param("seatId") Long seatId, @Param("userId") Long userId);
}
