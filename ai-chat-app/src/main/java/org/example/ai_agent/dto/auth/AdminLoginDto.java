package org.example.ai_agent.dto.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class AdminLoginDto {
    @NotBlank(message = "管理员用户名不能为空")
    @Size(max = 50, message = "管理员用户名长度不能超过50个字符")
    private String username;

    @NotBlank(message = "管理员密码不能为空")
    @Size(max = 100, message = "管理员密码长度不能超过100个字符")
    private String password;
}
