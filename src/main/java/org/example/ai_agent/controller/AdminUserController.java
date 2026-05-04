package org.example.ai_agent.controller;

import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.dto.admin.AdminUserItemDto;
import org.example.ai_agent.dto.document.DocumentDto;
import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.DocumentService;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.UserService;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
@CrossOrigin(origins = {"https://dduo.chat", "http://dduo.chat", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class AdminUserController {

    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final DocumentService documentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final UserService userService;

    public AdminUserController(UserRepository userRepository,
                               ConversationService conversationService,
                               DocumentService documentService,
                               KnowledgeBaseService knowledgeBaseService,
                               UserService userService) {
        this.userRepository = userRepository;
        this.conversationService = conversationService;
        this.documentService = documentService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.userService = userService;
    }

    @GetMapping
    public Mono<Map<String, Object>> getUsers(@RequestParam(defaultValue = "0") Integer page,
                                              @RequestParam(defaultValue = "10") Integer size,
                                              @RequestParam(required = false) String keyword,
                                              @RequestParam(required = false) Boolean guestOnly) {
        int finalPage = page != null && page >= 0 ? page : 0;
        int finalSize = size != null && size > 0 && size <= 100 ? size : 10;
        long offset = (long) finalPage * finalSize;

        Mono<List<AdminUserItemDto>> itemsMono = userRepository.findAdminUsers(keyword, guestOnly, finalSize, offset)
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

    @GetMapping("/{userId}/conversations")
    public Flux<ConversationWithPreview> getUserConversations(@PathVariable Long userId,
                                                              @RequestParam(defaultValue = "0") Integer page,
                                                              @RequestParam(defaultValue = "10") Integer size) {
        return conversationService.getConversationsForAdminByUserId(userId, page, size);
    }

    @GetMapping("/{userId}/documents")
    public Flux<DocumentDto> getUserDocuments(@PathVariable Long userId,
                                              @RequestParam(defaultValue = "0") Integer page,
                                              @RequestParam(defaultValue = "10") Integer size) {
        return documentService.getDocumentsByUploaderId(userId, page, size);
    }

    @GetMapping("/{userId}/knowledge-bases")
    public Flux<KnowledgeBaseDto> getUserKnowledgeBases(@PathVariable Long userId,
                                                        @RequestParam(defaultValue = "0") Integer page,
                                                        @RequestParam(defaultValue = "10") Integer size) {
        return knowledgeBaseService.getKnowledgeBasesByCreatorId(userId, page, size);
    }

    @DeleteMapping("/{userId}")
    public Mono<Map<String, Object>> deleteUser(@PathVariable Long userId) {
        return userService.deleteRegisteredUserAsAdmin(userId)
                .thenReturn(Map.of("message", "用户删除成功"));
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


