package org.example.ai_agent.service.impl.admin;

import org.example.ai_agent.dto.admin.AdminUserItemDto;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.admin.AdminUserQuery;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class RepositoryAdminUserQuery implements AdminUserQuery {

    private final UserRepository userRepository;

    public RepositoryAdminUserQuery(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public Mono<Map<String, Object>> getUsers(int page, int size, String keyword, Boolean guestOnly) {
        long offset = (long) page * size;
        Mono<List<AdminUserItemDto>> itemsMono = userRepository.findAdminUsers(keyword, guestOnly, size, offset)
                .map(this::toDto)
                .collectList();
        Mono<Long> totalMono = userRepository.countAdminUsers(keyword, guestOnly);
        Mono<Long> totalUsersMono = userRepository.count();
        Mono<Long> guestUsersMono = userRepository.countByIsGuest(true).defaultIfEmpty(0L);

        return Mono.zip(itemsMono, totalMono, totalUsersMono, guestUsersMono)
                .map(tuple -> {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("items", tuple.getT1());
                    payload.put("total", tuple.getT2());

                    Map<String, Object> summary = new HashMap<>();
                    summary.put("totalUsers", tuple.getT3());
                    summary.put("guestUsers", tuple.getT4());
                    summary.put("registeredUsers", Math.max(0L, tuple.getT3() - tuple.getT4()));
                    payload.put("summary", summary);
                    return payload;
                });
    }

    private AdminUserItemDto toDto(User user) {
        AdminUserItemDto dto = new AdminUserItemDto();
        dto.setUserId(user.getUserId());
        dto.setUsername(user.getUsername());
        dto.setEmail(user.getEmail());
        dto.setIsGuest(Boolean.TRUE.equals(user.getIsGuest()));
        dto.setCreatedAt(user.getCreatedAt());
        dto.setLastActiveAt(user.getLastActiveAt());
        return dto;
    }
}
