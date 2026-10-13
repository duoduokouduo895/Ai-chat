package org.example.ai_agent.admin.controller;

import org.example.ai_agent.chat.dto.ConversationWithPreview;
import org.example.ai_agent.document.dto.DocumentDto;
import org.example.ai_agent.knowledge.dto.KnowledgeBaseDto;
import org.example.ai_agent.admin.service.AdminUserQuery;
import org.example.ai_agent.chat.service.ConversationService;
import org.example.ai_agent.document.service.DocumentService;
import org.example.ai_agent.knowledge.service.KnowledgeBaseService;
import org.example.ai_agent.auth.service.UserService;
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
public class AdminUserController {

    private final AdminUserQuery adminUserQuery;
    private final ConversationService conversationService;
    private final DocumentService documentService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final UserService userService;

    public AdminUserController(AdminUserQuery adminUserQuery,
                               ConversationService conversationService,
                               DocumentService documentService,
                               KnowledgeBaseService knowledgeBaseService,
                               UserService userService) {
        this.adminUserQuery = adminUserQuery;
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
        return adminUserQuery.getUsers(finalPage, finalSize, keyword, guestOnly);
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

}


