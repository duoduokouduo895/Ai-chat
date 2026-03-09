package org.example.ai_agent.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;

@Data
@Table("users")
public class User {

    @Id
    @Column("user_id")
    private Long userId;
    
    @Column("username")
    private String username;

    @Column("password")
    private String password;

    @Column("email")
    private String email;

    @Column("created_at")
    private LocalDateTime createdAt;

    @Column("is_guest")
    private Boolean isGuest;


    
    /**
     * 获取用户显示名称
     * 
     * @return 用户显示名称
     */
    public String getDisplayName() {
        return username;
    }
}
