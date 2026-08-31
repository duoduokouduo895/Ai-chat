package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import org.example.ai_agent.dto.auth.UserLoginDto;
import org.example.ai_agent.dto.auth.UserRegistrationDto;
import org.example.ai_agent.service.UserService;
import org.example.ai_agent.service.admission.ChatAdmissionException;
import org.example.ai_agent.service.admission.GuestCreationAdmissionService;
import org.example.ai_agent.service.impl.auth.GuestUpgradeSessionException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final GuestCreationAdmissionService guestCreationAdmissionService;

    public UserController(UserService userService, GuestCreationAdmissionService guestCreationAdmissionService) {
        this.userService = userService;
        this.guestCreationAdmissionService = guestCreationAdmissionService;
    }



    @PostMapping("/register")
    public Mono<ResponseEntity<Map<String, Object>>> registerUser(@Valid @RequestBody UserRegistrationDto registrationDto) {
        return userService.registerUser(registrationDto)
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    return ResponseEntity.ok().body(response);
                }))
                .onErrorResume(IllegalArgumentException.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().body(response));
                })
                .onErrorResume(e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "注册失败，请稍后重试");
                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 用当前游客 Token 升级同一条用户记录，避免会话和消息归属丢失。
     */
    @PostMapping("/guest/upgrade")
    public Mono<ResponseEntity<Map<String, Object>>> upgradeGuestUser(
            @Valid @RequestBody UserRegistrationDto registrationDto,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader,
            Authentication authentication) {
        return resolveAuthenticatedUserId(authentication)
                .flatMap(guestUserId -> userService.upgradeGuestUser(registrationDto, guestUserId, authorizationHeader))
                .map(token -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("token", token);
                    response.put("message", "账户升级成功，已保留原会话");
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(IllegalArgumentException.class, error -> Mono.just(ResponseEntity.badRequest()
                        .body(errorResponse(error.getMessage()))))
                .onErrorResume(SecurityException.class, error -> Mono.just(ResponseEntity.status(403)
                        .body(errorResponse(error.getMessage()))))
                .onErrorResume(GuestUpgradeSessionException.class, error -> Mono.just(ResponseEntity.status(503)
                        .body(errorResponse("账户已升级，但会话更新失败，请使用新账号登录"))))
                .onErrorResume(error -> {
                    org.slf4j.LoggerFactory.getLogger(UserController.class)
                            .error("Unexpected error during guest upgrade", error);
                    return Mono.just(ResponseEntity.status(500)
                            .body(errorResponse("账户升级失败，请稍后重试")));
                });
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> loginUser(@Valid @RequestBody UserLoginDto loginDto) {
        return userService.loginUser(loginDto.getUsername(), loginDto.getPassword())
                .map(token -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("token", token);
                    return ResponseEntity.ok().body(response);
                })
                .onErrorResume(SecurityException.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.status(401).body(response));
                })
                .onErrorResume(e -> {
                    org.slf4j.LoggerFactory.getLogger(UserController.class).error("Unexpected error during login", e);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "登录失败，请稍后重试");
                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 游客一键体验登录。
     * 后台将自动生成游客账号，并返回可用的 JWT Token。
     * @return 包含生成的 token 的响应
     */
    @PostMapping("/guest")
    public Mono<ResponseEntity<Map<String, Object>>> createGuestAndLogin(ServerWebExchange exchange) {
        return guestCreationAdmissionService.admit(exchange)
                .then(Mono.defer(userService::createGuestAndLogin))
                .map(token -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("token", token);
                    response.put("message", "游客账号创建成功");
                    return ResponseEntity.ok().body(response);
                })
                .onErrorResume(ChatAdmissionException.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", e.getReason());
                    return Mono.just(ResponseEntity.status(e.getStatusCode()).body(response));
                })
                .onErrorResume(e -> {
                    org.slf4j.LoggerFactory.getLogger(UserController.class).error("Unexpected error during guest login", e);
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "创建游客账户失败，请稍后重试");
                    return Mono.just(ResponseEntity.status(500).body(response));
                });
    }

    /**
     * 用户登出。
     * @param authorizationHeader 包含 "Bearer " token 的认证头。
     * @return 成功或失败的响应。
     */
    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logoutUser(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return userService.logoutUser(authorizationHeader)
                .then(Mono.fromCallable(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "Logout successful");
                    return ResponseEntity.ok(response);
                }))
                .onErrorResume(e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", "退出登录失败");
                    return Mono.just(ResponseEntity.status(400).body(response));
                });
    }

    /**
     * 获取当前认证用户的基本信息。
     * 这是一个受保护的端点，用于前端验证token的有效性。
     * @return 包含用户名的响应，如果token无效则返回401。
     */
    @GetMapping("/me")
    public Mono<ResponseEntity<Map<String, String>>> getMe() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .map(Authentication::getName)
                .map(username -> {
                    Map<String, String> response = new HashMap<>();
                    response.put("username", username);
                    return ResponseEntity.ok(response);
                })
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }

    private Mono<Long> resolveAuthenticatedUserId(Authentication authentication) {
        if (authentication == null || !(authentication.getDetails() instanceof Number userId)
                || userId.longValue() <= 0) {
            return Mono.error(new SecurityException("当前游客会话无效"));
        }
        return Mono.just(userId.longValue());
    }

    private Map<String, Object> errorResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return response;
    }
}


