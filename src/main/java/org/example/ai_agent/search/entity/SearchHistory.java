package org.example.ai_agent.search.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("search_history")
public class SearchHistory {

    @Id
    private Long id;

    private Long userId;

    private String query;

    private String type;

    private LocalDateTime timestamp;
}
