package org.example.ai_agent.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;
import org.springframework.data.relational.core.mapping.Column;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("conversation")
public class Conversation {
    @Id
    private Long id;
    
    @Column("title")
    private String title;
    
    @Column("created_at")
    private LocalDateTime createdAt;
    
    @Column("updated_at")
    private LocalDateTime updatedAt;

    @Column("user_id")
    private Long userId;
    
    // R2DBC不支持@PrePersist和@PreUpdate，需要在Service层手动设置时间
}