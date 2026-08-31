package org.example.ai_agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("admin_users")
public class AdminUser {

    @Id
    @Column("admin_id")
    private Long adminId;

    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("display_name")
    private String displayName;

    @Column("email")
    private String email;

    @Column("status")
    private String status;

    @Column("last_login_at")
    private LocalDateTime lastLoginAt;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("updated_at")
    private LocalDateTime updatedAt;
}
