package org.example.ai_agent.search.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "搜索内容不能为空")
    @Size(max = 255, message = "搜索内容长度不能超过255个字符")
    private String query;

    /**
     * 搜索的类型。
     * 例如："basic"（基础搜索）, "advanced"（高级搜索）等。
     */
    @Size(max = 50, message = "搜索类型长度不能超过50个字符")
    private String type;
}
