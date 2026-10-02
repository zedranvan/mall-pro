package com.mall.pro.module.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.order.entity.TicketOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface TicketOrderMapper extends BaseMapper<TicketOrder> {

    @Update("UPDATE d_ticket_order " +
            "SET status = #{targetStatus}, version = version + 1 " +
            "WHERE id = #{id} AND status = #{expectStatus}")
    int updateStatusCas(@Param("id") Long id,
                        @Param("expectStatus") int expectStatus,
                        @Param("targetStatus") int targetStatus);
}
