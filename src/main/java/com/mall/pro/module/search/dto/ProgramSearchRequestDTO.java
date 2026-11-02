package com.mall.pro.module.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramSearchRequestDTO {
    /**
     * 检索关键字 (匹配演出名称、艺人、场馆)
     */
    private String keyword;

    /**
     * 城市筛选 (如 广州, 深圳, 佛山)
     */
    private String city;

    /**
     * 分类筛选 (如 演唱会, 话剧歌剧, 体育赛事)
     */
    private String categoryName;

    /**
     * 价格区间筛选
     */
    private BigDecimal minPrice;
    private BigDecimal maxPrice;

    /**
     * 排序规则:
     * 1: 综合热度降序 (默认)
     * 2: 演出时间升序 (近期演出优先)
     * 3: 价格由低到高
     * 4: 价格由高到低
     */
    private Integer sortType;

    private Integer pageNo;
    private Integer pageSize;

    public int getPageNo() {
        return (pageNo == null || pageNo < 1) ? 1 : pageNo;
    }

    public int getPageSize() {
        return (pageSize == null || pageSize < 1 || pageSize > 50) ? 10 : pageSize;
    }
}
