package com.mall.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxMessage> {

    /**
     * 高并发安全拉取待发送的 Outbox 消息：
     * 采用 PostgreSQL 特有的 FOR UPDATE SKIP LOCKED：
     * 1. 排他锁定选中的待处理行；
     * 2. 如果已被其他实例或线程锁定，则直接跳过，绝不阻塞！
     * 3. 实现多实例并发拉取无竞争、高吞吐。
     */
    @Select("SELECT * FROM t_outbox " +
            "WHERE status = 0 AND retry_count < #{maxRetry} " +
            "ORDER BY create_time ASC " +
            "LIMIT #{batchSize} " +
            "FOR UPDATE SKIP LOCKED")
    List<OutboxMessage> pollPendingMessagesForUpdate(@Param("batchSize") int batchSize, @Param("maxRetry") int maxRetry);

    /**
     * 标记为发送成功
     */
    @Update("UPDATE t_outbox SET status = 1, update_time = CURRENT_TIMESTAMP WHERE id = #{id}")
    int markAsPublished(@Param("id") Long id);

    /**
     * 标记为发送失败并累加重试次数
     */
    @Update("UPDATE t_outbox SET retry_count = retry_count + 1, " +
            "status = CASE WHEN retry_count + 1 >= #{maxRetry} THEN 2 ELSE 0 END, " +
            "error_msg = #{errorMsg}, " +
            "update_time = CURRENT_TIMESTAMP " +
            "WHERE id = #{id}")
    int markAsFailed(@Param("id") Long id, @Param("errorMsg") String errorMsg, @Param("maxRetry") int maxRetry);
}
