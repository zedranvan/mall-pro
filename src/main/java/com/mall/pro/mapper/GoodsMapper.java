package com.mall.pro.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.pro.entity.Goods;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface GoodsMapper extends BaseMapper<Goods> {
    @Update("UPDATE goods SET stock = stock - 1 WHERE id = #{goodId} AND stock > 0")
    int reduceStock(@Param("goodId") Long goodId);
}
