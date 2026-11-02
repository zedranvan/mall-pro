package com.mall.pro.module.search.repository;

import com.mall.pro.module.search.document.ProgramDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProgramElasticsearchRepository extends ElasticsearchRepository<ProgramDocument, Long> {

    List<ProgramDocument> findByCity(String city);

    List<ProgramDocument> findByCategoryName(String categoryName);
}
