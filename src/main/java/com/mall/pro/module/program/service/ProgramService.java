package com.mall.pro.module.program.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.common.BusinessException;
import com.mall.pro.module.program.dto.ProgramDetailDTO;
import com.mall.pro.module.program.entity.Program;
import com.mall.pro.module.program.mapper.ProgramMapper;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.mapper.TicketCategoryMapper;
import com.mall.pro.module.ticket.service.TicketCategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramService {
    private final ProgramMapper programMapper;
    private final TicketCategoryMapper ticketCategoryMapper;
    private final TicketCategoryService ticketCategoryService;

    /**
     * 1. 演出场次模糊检索 (按演出时间正序排列)
     */
    public List<Program> searchPrograms(String keyword) {
        LambdaQueryWrapper<Program> wrapper = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(keyword)) {
            wrapper.like(Program::getTitle, keyword)
                    .or().like(Program::getActor, keyword)
                    .or().like(Program::getPlace, keyword);
        }
        wrapper.orderByAsc(Program::getShowTime);
        return programMapper.selectList(wrapper);
    }
    /**
     * 2. 演出详情与票档联动 (各票档库存走 Caffeine + Redis 多级缓存)
     */
    public ProgramDetailDTO getProgramDetail(Long programId) {
        Program program = programMapper.selectById(programId);
        if (program == null) {
            throw new BusinessException(404,"演出场次不存在！");
        }

        // 查出该演出关联的票档列表
        List<TicketCategory> dbCategories = ticketCategoryMapper.selectList(
                new LambdaQueryWrapper<TicketCategory>()
                        .eq(TicketCategory::getProgramId, programId)
                        .orderByAsc(TicketCategory::getPrice)
        );

        // 核心亮点：各票档余票通过 TicketCategoryService 走 L1(Caffeine) -> L2(Redis) 多级缓存！
        List<TicketCategory> cachedCategories = dbCategories.stream()
                .map(cat -> ticketCategoryService.getById(cat.getId()))
                .toList();

        return ProgramDetailDTO.builder()
                .id(program.getId())
                .title(program.getTitle())
                .actor(program.getActor())
                .place(program.getPlace())
                .showTime(program.getShowTime())
                .ticketCategories(cachedCategories)
                .build();
    }
}

