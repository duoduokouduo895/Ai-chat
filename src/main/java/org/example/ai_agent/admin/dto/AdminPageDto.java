package org.example.ai_agent.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * 管理端列表分页响应。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdminPageDto<T> {

    private List<T> items;

    private Long total;

    private Map<String, Object> summary;
}
