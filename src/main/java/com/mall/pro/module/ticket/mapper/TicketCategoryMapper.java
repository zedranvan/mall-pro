package com.mall.pro.module.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.ticket.entity.TicketCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TicketCategoryMapper extends BaseMapper<TicketCategory> {

    @Update("UPDATE d_ticket_category " +
            "SET remain_stock = remain_stock - #{count}, update_time = NOW() " +
            "WHERE id = #{id} AND remain_stock >= #{count}")
    int deductStock(@Param("id") Long id, @Param("count") Integer count);

    @Update("UPDATE d_ticket_category " +
            "SET remain_stock = remain_stock + #{count}, update_time = NOW() " +
            "WHERE id = #{id}")
    int restoreStock(@Param("id") Long id, @Param("count") Integer count);
}
