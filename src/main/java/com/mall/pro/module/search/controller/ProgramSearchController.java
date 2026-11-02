package com.mall.pro.module.search.controller;

import com.mall.pro.common.Result;
import com.mall.pro.module.search.dto.ProgramSearchRequestDTO;
import com.mall.pro.module.search.dto.ProgramSearchResultDTO;
import com.mall.pro.module.search.service.ProgramSearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Elasticsearch 全文检索与多维演出中枢")
@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class ProgramSearchController {

    private final ProgramSearchService programSearchService;

    @Operation(summary = "全量演出数据预热同步至 Elasticsearch 倒排索引")
    @PostMapping("/init")
    public Result<String> initIndex() {
        int count = programSearchService.initOrSyncAllPrograms();
        return Result.success("Elasticsearch 演出索引全量构建完成，已同步演出记录: " + count + " 条");
    }

    @Operation(summary = "多条件全文检索与动态排序、高亮展示")
    @PostMapping("/programs")
    public Result<ProgramSearchResultDTO> searchPrograms(@RequestBody ProgramSearchRequestDTO dto) {
        return Result.success(programSearchService.searchPrograms(dto));
    }
}
