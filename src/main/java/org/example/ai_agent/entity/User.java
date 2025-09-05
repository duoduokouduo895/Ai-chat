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
    private boolean isGuest;

    @Column("guest_id")
    private String guestId;
    
    /**
     * 判断用户是否为游客
     * 
     * @return true表示游客用户，false表示注册用户
     */
    public boolean isGuest() {
        return this.isGuest;
    }
    
    /**
     * 获取用户显示名称
     * 游客用户显示guestId，注册用户显示username
     * 
     * @return 用户显示名称
     */
    public String getDisplayName() {
        return isGuest() ? (guestId != null ? guestId : "游客") : username;
    }
}
