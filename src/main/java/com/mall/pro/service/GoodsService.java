package com.mall.pro.service;

import com.mall.pro.entity.Goods;
import com.mall.pro.mapper.GoodsMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GoodsService {
    @Autowired
    private GoodsMapper goodsMapper;
    public Goods getById(Long id){
        return goodsMapper.selectById(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean deducStock(Long id){
    int rows = goodsMapper.reduceStock(id);
    return rows > 0;

    }
}
