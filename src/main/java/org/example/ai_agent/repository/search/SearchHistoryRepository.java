package org.example.ai_agent.repository.search;

import org.example.ai_agent.entity.SearchHistory;
import org.springframework.data.r2dbc.repository.R2dbcRepository;

public interface SearchHistoryRepository extends R2dbcRepository<SearchHistory, Long> {
}
