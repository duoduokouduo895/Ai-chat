package org.example.ai_agent.service;

import org.example.ai_agent.dto.auth.UserRegistrationDto;
import org.example.ai_agent.entity.User; // 添加缺失的导入
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<Void> registerUser(UserRegistrationDto registrationDto);

    Mono<String> loginUser(String username, String password);

    /**
     * 用户登出。
     * @param authorizationHeader 完整的 "Authorization" 请求头 (例如, "Bearer ...")。
     * @return 操作完成的 Mono<Void>。
     */
    Mono<Void> logoutUser(String authorizationHeader);

    Mono<User> createGuestUser();

    Mono<String> generateTokenForGuest(User guest);
    
    /**
     * 清理创建时间超过1天的游客用户
     * @return 清理的用户数量
     */
    Mono<Integer> cleanupExpiredGuestUsers();
}
