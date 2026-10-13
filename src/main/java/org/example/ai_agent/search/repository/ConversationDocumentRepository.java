package org.example.ai_agent.search.repository;

import org.example.ai_agent.search.entity.ConversationDocument;
import org.springframework.data.domain.Pageable;
import org.springframework.data.elasticsearch.repository.ReactiveElasticsearchRepository;
import reactor.core.publisher.Flux;

public interface ConversationDocumentRepository extends ReactiveElasticsearchRepository<ConversationDocument, String> {
    /**
     * 根据标题或内容模糊查询
     *
     * @param title 标题
     * @param content 内容
     * @param pageable 分页参数
     * @return 查询结果
     */
    Flux<ConversationDocument> findByTitleContainingOrContentContaining(String title, String content, Pageable pageable);

    /**
     * 根据标题模糊查询
     *
     * @param title 标题
     * @param pageable 分页参数
     * @return 查询结果
     */
    Flux<ConversationDocument> findByTitleContaining(String title, Pageable pageable);
}
