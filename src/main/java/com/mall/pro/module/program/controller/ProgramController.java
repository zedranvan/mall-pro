package com.mall.pro.module.program.controller;


import com.mall.pro.common.Result;
import com.mall.pro.module.program.dto.ProgramDetailDTO;
import com.mall.pro.module.program.entity.Program;
import com.mall.pro.module.program.service.ProgramService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/program")
@RequiredArgsConstructor
public class ProgramController {

    private final ProgramService programService;
    /**
     * 1. 演出场次检索 (支持关键词模糊匹配，不传查全部)
     */
    @GetMapping("/search")
    public Result<List<Program>> searchPrograms(@RequestParam(required = false) String keyword) {
        List<Program> list = programService.searchPrograms(keyword);
        return Result.success(list);
    }


    /**
     * 2. 演出详情与票档联动查询 (票档余票走 Caffeine + Redis 两级缓存)
     */
    @GetMapping("/{id}")
    public Result<ProgramDetailDTO> getProgramDetail(@PathVariable Long id) {
        ProgramDetailDTO detail = programService.getProgramDetail(id);
        return Result.success(detail);
    }
}
