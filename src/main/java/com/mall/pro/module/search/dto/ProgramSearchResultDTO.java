package com.mall.pro.module.search.dto;

import com.mall.pro.module.search.document.ProgramDocument;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProgramSearchResultDTO {
    private Long total;
    private Integer pageNo;
    private Integer pageSize;
    private List<ProgramDocument> list;
    /**
     * 高亮标红映射: id -> 带 <em> 标签的高亮标题
     */
    private Map<Long, String> highlightMap;
}
