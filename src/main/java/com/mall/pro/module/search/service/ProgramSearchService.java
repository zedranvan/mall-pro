package com.mall.pro.module.search.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.mall.pro.module.program.entity.Program;
import com.mall.pro.module.program.mapper.ProgramMapper;
import com.mall.pro.module.search.document.ProgramDocument;
import com.mall.pro.module.search.dto.ProgramSearchRequestDTO;
import com.mall.pro.module.search.dto.ProgramSearchResultDTO;
import com.mall.pro.module.search.repository.ProgramElasticsearchRepository;
import com.mall.pro.module.ticket.entity.TicketCategory;
import com.mall.pro.module.ticket.mapper.TicketCategoryMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProgramSearchService {

    private final ProgramElasticsearchRepository programRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ProgramMapper programMapper;
    private final TicketCategoryMapper ticketCategoryMapper;

    /**
     * 1. 全量预热与同步：将 PostgreSQL 中的演出与票档数据灌入 Elasticsearch 倒排索引
     */
    public int initOrSyncAllPrograms() {
        List<Program> programs = programMapper.selectList(null);
        if (programs.isEmpty()) {
            return 0;
        }

        List<ProgramDocument> docList = new ArrayList<>();
        for (Program p : programs) {
            // 查询关联票档取最低起步价
            List<TicketCategory> categories = ticketCategoryMapper.selectList(
                    new LambdaQueryWrapper<TicketCategory>().eq(TicketCategory::getProgramId, p.getId())
            );
            BigDecimal minPrice = categories.stream()
                    .map(TicketCategory::getPrice)
                    .min(BigDecimal::compareTo)
                    .orElse(new BigDecimal("280.00"));

            // 智能提取举办城市
            String city = "广州";
            if (p.getPlace() != null) {
                if (p.getPlace().contains("深圳")) city = "深圳";
                else if (p.getPlace().contains("佛山")) city = "佛山";
                else if (p.getPlace().contains("北京")) city = "北京";
                else if (p.getPlace().contains("上海")) city = "上海";
                else if (p.getPlace().contains("广州")) city = "广州";
            }

            // 智能提取分类
            String categoryName = "流行演唱会";
            if (p.getTitle() != null) {
                if (p.getTitle().contains("话剧") || p.getTitle().contains("歌剧")) categoryName = "话剧歌剧";
                else if (p.getTitle().contains("比赛") || p.getTitle().contains("球赛")) categoryName = "体育赛事";
                else if (p.getTitle().contains("音乐节")) categoryName = "音乐节";
            }

            ProgramDocument doc = ProgramDocument.builder()
                    .id(p.getId())
                    .title(p.getTitle())
                    .actor(p.getActor())
                    .place(p.getPlace())
                    .city(city)
                    .categoryName(categoryName)
                    .minPrice(minPrice)
                    .showTime(p.getShowTime() != null ? p.getShowTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null)
                    .hotScore(1000L + p.getId() * 50)
                    .build();
            docList.add(doc);
        }

        programRepository.saveAll(docList);
        log.info("Elasticsearch 演出索引全量初始化同步完成，文档总数: {}", docList.size());
        return docList.size();
    }

    /**
     * 2. 多条件全文检索与动态排序、高亮展示
     */
    public ProgramSearchResultDTO searchPrograms(ProgramSearchRequestDTO dto) {
        Criteria criteria = null;

        // 关键字全文检索 (演出标题、艺人、场馆分词倒排匹配)
        if (StringUtils.hasText(dto.getKeyword())) {
            String kw = dto.getKeyword().trim();
            criteria = new Criteria("title").matches(kw)
                    .or(new Criteria("actor").matches(kw))
                    .or(new Criteria("place").matches(kw));
        }

        // 城市精准筛选
        if (StringUtils.hasText(dto.getCity())) {
            Criteria cityCriteria = new Criteria("city").is(dto.getCity().trim());
            criteria = (criteria == null) ? cityCriteria : criteria.and(cityCriteria);
        }

        // 分类精准筛选
        if (StringUtils.hasText(dto.getCategoryName())) {
            Criteria catCriteria = new Criteria("categoryName").is(dto.getCategoryName().trim());
            criteria = (criteria == null) ? catCriteria : criteria.and(catCriteria);
        }

        // 价格区间筛选
        if (dto.getMinPrice() != null) {
            Criteria minPriceCriteria = new Criteria("minPrice").greaterThanEqual(dto.getMinPrice().doubleValue());
            criteria = (criteria == null) ? minPriceCriteria : criteria.and(minPriceCriteria);
        }
        if (dto.getMaxPrice() != null) {
            Criteria maxPriceCriteria = new Criteria("minPrice").lessThanEqual(dto.getMaxPrice().doubleValue());
            criteria = (criteria == null) ? maxPriceCriteria : criteria.and(maxPriceCriteria);
        }

        CriteriaQuery query = (criteria != null) ? new CriteriaQuery(criteria) : new CriteriaQuery(new Criteria());
        Sort sort = Sort.by(Sort.Direction.DESC, "hotScore"); // 默认热度降序
        if (dto.getSortType() != null) {
            switch (dto.getSortType()) {
                case 2 -> sort = Sort.by(Sort.Direction.ASC, "showTime"); // 时间升序
                case 3 -> sort = Sort.by(Sort.Direction.ASC, "minPrice"); // 价格升序
                case 4 -> sort = Sort.by(Sort.Direction.DESC, "minPrice"); // 价格降序
            }
        }

        PageRequest pageRequest = PageRequest.of(dto.getPageNo() - 1, dto.getPageSize(), sort);
        query.setPageable(pageRequest);

        SearchHits<ProgramDocument> searchHits = elasticsearchOperations.search(query, ProgramDocument.class);

        List<ProgramDocument> list = new ArrayList<>();
        Map<Long, String> highlightMap = new HashMap<>();

        for (SearchHit<ProgramDocument> hit : searchHits) {
            ProgramDocument doc = hit.getContent();
            list.add(doc);

            // 关键字高亮标红计算 (用 <em> 标签包裹关键字)
            if (StringUtils.hasText(dto.getKeyword()) && doc.getTitle() != null) {
                String kw = dto.getKeyword().trim();
                if (doc.getTitle().contains(kw)) {
                    String highlighted = doc.getTitle().replace(kw, "<em style='color:red'>" + kw + "</em>");
                    highlightMap.put(doc.getId(), highlighted);
                }
            }
        }

        return ProgramSearchResultDTO.builder()
                .total(searchHits.getTotalHits())
                .pageNo(dto.getPageNo())
                .pageSize(dto.getPageSize())
                .list(list)
                .highlightMap(highlightMap)
                .build();
    }
}
