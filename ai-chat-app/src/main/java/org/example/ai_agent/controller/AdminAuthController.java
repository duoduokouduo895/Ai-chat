package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import org.example.ai_agent.dto.auth.AdminLoginDto;
import org.example.ai_agent.service.AdminAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/auth")
public class AdminAuthController {

    private final AdminAuthService adminAuthService;

    public AdminAuthController(AdminAuthService adminAuthService) {
        this.adminAuthService = adminAuthService;
    }

    @PostMapping("/login")
    public Mono<ResponseEntity<Map<String, Object>>> login(@Valid @RequestBody AdminLoginDto loginDto) {
        return adminAuthService.login(loginDto.getUsername(), loginDto.getPassword())
                .map(token -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("token", token);
                    return ResponseEntity.ok(response);
                })
                .onErrorResume(SecurityException.class, e -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", false);
                    response.put("message", e.getMessage());
                    return Mono.just(ResponseEntity.status(401).body(response));
                });
    }

    @PostMapping("/logout")
    public Mono<ResponseEntity<Map<String, Object>>> logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorizationHeader) {
        return adminAuthService.logout(authorizationHeader)
                .then(Mono.fromSupplier(() -> {
                    Map<String, Object> response = new HashMap<>();
                    response.put("success", true);
                    response.put("message", "管理员退出成功");
                    return ResponseEntity.ok(response);
                }));
    }

    @GetMapping("/me")
    public Mono<ResponseEntity<Object>> me() {
        return ReactiveSecurityContextHolder.getContext()
                .map(SecurityContext::getAuthentication)
                .flatMap(Authentication -> adminAuthService.getCurrentAdmin(Authentication.getName()))
                .map(profile -> ResponseEntity.ok().body((Object) profile))
                .defaultIfEmpty(ResponseEntity.status(401).build());
    }
}


