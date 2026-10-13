package org.example.ai_agent.chat.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
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
@Table("message")
public class Message {
    @org.springframework.data.annotation.Transient
    private Object retrieval;
    @org.springframework.data.annotation.Transient
    private String chatTurnId;
    @Id
    private Long id;
    
    @Column("conversation_id")
    private Long conversationId;
    
    @Column("sender")
    private String sender; // 'user', 'ai', 'system'
    
    @Column("content")
    private String content;
    
    @Column("model")
    private String model; // 使用的AI模型

    @JsonIgnore
    @Column("deduplication_key")
    private String deduplicationKey;
    
    @Column("timestamp")
    private LocalDateTime timestamp;
    
    // R2DBC不支持@PrePersist，需要在Service层手动设置时间戳
    public void setTimestampIfNull() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
