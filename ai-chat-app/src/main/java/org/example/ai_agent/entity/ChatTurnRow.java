package org.example.ai_agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** Persistence row for the ChatTurn lifecycle; not an HTTP response model. */
@Data
@Table("chat_turn")
public class ChatTurnRow {

    @Id
    private String id;
    @Column("actor_type") private String actorType;
    @Column("actor_id") private Long actorId;
    @Column("conversation_id") private Long conversationId;
    @Column("client_request_id") private String clientRequestId;
    @Column("model_id") private String modelId;
    private String status;
    @Column("user_message_id") private Long userMessageId;
    @Column("assistant_message_id") private Long assistantMessageId;
    private Integer attempt;
    @Column("error_code") private String errorCode;
    @Column("started_at") private LocalDateTime startedAt;
    @Column("updated_at") private LocalDateTime updatedAt;
    @Column("completed_at") private LocalDateTime completedAt;
}
