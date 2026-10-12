package com.mall.pro.module.ticket.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.ticket.entity.TicketItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface TicketItemMapper  extends BaseMapper<TicketItem> {
    /**
     * 闸机扫码核销原子跃迁（CAS 乐观锁）:
     * 只有当前 status = 0 (未核销) 时，才能成功跃迁为 status = 1 (已核销)
     * @return 影响行数：返回 1 说明核销成功，开闸放行；返回 0 说明已被抢先核销，红灯报警！
     */
    @Update("UPDATE d_ticket_item SET status = 1, gate_no = #{gateNo}, verify_time = #{verifyTime}, update_time = #{now} " +
            "WHERE verify_code = #{verifyCode} AND status = 0")
    int verifyTicketCas(@Param("verifyCode") String verifyCode,
                        @Param("gateNo") String gateNo,
                        @Param("verifyTime") LocalDateTime verifyTime,
                        @Param("now") LocalDateTime now);

    @Update("UPDATE d_ticket_item SET status = 2, update_time = #{now} " +
            "WHERE id = #{ticketItemId} AND status = 0")
    int refundTicketCas(@Param("ticketItemId") Long ticketItemId,
                        @Param("now") LocalDateTime now);

    /**
     * 转增原子跃迁（CAS乐观锁）：
     * 只有当status = 0 （未入场）且所有者等于fromUsrId时，才允许过户给toUsrId并刷新防伪码与实名
     */
    @Update("UPDATE d_ticket_item " +
            "SET user_id = #{toUserId}, verify_code = #{newVerifyCode}, real_name = #{newRealName}, " +
            "id_card_masked = #{newIdCardMasked}, id_card_hash = #{newIdCardHash}, update_time = #{now} " +
            "WHERE id = #{ticketItemId} AND user_id = #{fromUserId} AND status = 0")
    int transferTicketCas(@Param("ticketItemId") Long ticketItemId,
                          @Param("fromUserId") Long fromUserId,
                          @Param("toUserId") Long toUserId,
                          @Param("newVerifyCode") String newVerifyCode,
                          @Param("newRealName") String newRealName,
                          @Param("newIdCardMasked") String newIdCardMasked,
                          @Param("newIdCardHash") String newIdCardHash,
                          @Param("now") LocalDateTime now);
}
