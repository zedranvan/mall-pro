package com.mall.pro.module.pay.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.module.pay.entity.OutboxMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMapper extends BaseMapper<OutboxMessage> {
}
