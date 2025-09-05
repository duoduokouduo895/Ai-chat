package org.example.ai_agent.dto.search;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * 搜索历史数据传输对象 (DTO)。
 * 用于封装从前端传递到后端的搜索历史记录信息。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchHistoryDto {

    /**
     * 用户的搜索查询内容。
     * 例如："如何使用Java Stream API"
     */
    private String query;

    /**
     * 搜索的类型。
     * 例如："basic"（基础搜索）, "advanced"（高级搜索）等。
     */
    private String type;
}
