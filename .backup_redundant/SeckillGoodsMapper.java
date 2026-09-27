package com.mall.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.entity.SeckillGoods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;
import org.springframework.web.bind.annotation.PostMapping;

@Mapper
public interface SeckillGoodsMapper extends BaseMapper<SeckillGoods> {
    @Update("UPDATE seckill_goods SET stock_count = stock_count -1 WHERE id =#{id} AND stock_count > 0")
    int reduceStockAtomic(@Param("id") Long id);
}


