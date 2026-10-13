package org.example.ai_agent.chat.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.domain.Persistable;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

/** Persistence row for the ChatTurn lifecycle; not an HTTP response model. */
@Data
@Table("chat_turn")
public class ChatTurnRow implements Persistable<String> {

    @Id
    private String id;
    @Column("actor_type") private String actorType;
    @Column("actor_id") private Long actorId;
    @Column("conversation_id") private Long conversationId;
    @Column("client_request_id") private String clientRequestId;
    @Column("request_fingerprint") private String requestFingerprint;
    @Column("model_id") private String modelId;
    @Column("retrieval_json") private String retrievalJson;
    private String status;
    @Column("user_message_id") private Long userMessageId;
    @Column("assistant_message_id") private Long assistantMessageId;
    private Integer attempt;
    @Column("error_code") private String errorCode;
    @Column("started_at") private LocalDateTime startedAt;
    @Column("updated_at") private LocalDateTime updatedAt;
    @Column("completed_at") private LocalDateTime completedAt;

    /**
     * ChatTurn IDs are generated before their first write. Without this flag,
     * R2DBC interprets a non-null ID as an UPDATE and silently skips the
     * initial insert. The lifecycle switches to an existing row before it
     * writes the user-message ID in a second save.
     */
    @Transient
    private boolean newEntity;

    @Override
    public boolean isNew() {
        return newEntity;
    }

    public void markNew() {
        this.newEntity = true;
    }

    public void markPersisted() {
        this.newEntity = false;
    }
}
