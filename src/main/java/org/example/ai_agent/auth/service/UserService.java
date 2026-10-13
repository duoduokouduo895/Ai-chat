package org.example.ai_agent.auth.service;

import org.example.ai_agent.auth.dto.UserRegistrationDto;
import org.example.ai_agent.auth.entity.User; // 添加缺失的导入
import reactor.core.publisher.Mono;

public interface UserService {

    Mono<Void> registerUser(UserRegistrationDto registrationDto);

    Mono<String> loginUser(String username, String password);

    Mono<String> createGuestAndLogin();

    /**
     * 将当前已认证游客升级为正式用户；用户主键不变，因此已有会话与消息会被保留。
     */
    Mono<String> upgradeGuestUser(UserRegistrationDto registrationDto, Long guestUserId,
                                  String authorizationHeader);

    /**
     * 用户登出。
     * @param authorizationHeader 完整的 "Authorization" 请求头 (例如, "Bearer ...")。
     * @return 操作完成的 Mono<Void>。
     */
    Mono<Void> logoutUser(String authorizationHeader);

    Mono<Void> deleteRegisteredUserAsAdmin(Long userId);

}
