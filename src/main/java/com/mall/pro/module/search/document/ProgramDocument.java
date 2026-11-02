package com.mall.pro.module.search.document;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(indexName = "d_program_index")
public class ProgramDocument {

    @Id
    private Long id;

    /**
     * 演出标题：支持全文分词模糊匹配
     */
    @Field(type = FieldType.Text)
    private String title;

    /**
     * 演职人员 / 艺人
     */
    @Field(type = FieldType.Text)
    private String actor;

    /**
     * 演出场馆地点
     */
    @Field(type = FieldType.Text)
    private String place;

    /**
     * 举办城市 (如 广州, 深圳, 佛山)
     */
    @Field(type = FieldType.Keyword)
    private String city;

    /**
     * 演出分类名称 (如 演唱会, 话剧歌剧, 体育比赛)
     */
    @Field(type = FieldType.Keyword)
    private String categoryName;

    /**
     * 起步票价 (用于价格区间筛选与排序)
     */
    @Field(type = FieldType.Double)
    private BigDecimal minPrice;

    /**
     * 演出时间 (格式: yyyy-MM-dd HH:mm:ss，支持字符序时间排序)
     */
    @Field(type = FieldType.Keyword)
    private String showTime;

    /**
     * 演出热度值 (用于综合热度排序)
     */
    @Field(type = FieldType.Long)
    private Long hotScore;
}
