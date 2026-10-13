package org.example.ai_agent.chat.controller;

import jakarta.validation.Valid;
import org.example.ai_agent.admin.dto.AdminPageDto;
import org.example.ai_agent.chat.dto.ConversationWithPreview;
import org.example.ai_agent.chat.dto.MessageDto;
import org.example.ai_agent.auth.security.RequestActorResolver;
import org.example.ai_agent.chat.entity.Conversation;
import org.example.ai_agent.chat.entity.Message;
import org.example.ai_agent.chat.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/conversations")
public class ConversationController {

    private final ConversationService conversationService;
    private final RequestActorResolver requestActorResolver;

    public ConversationController(ConversationService conversationService, RequestActorResolver requestActorResolver) {
        this.conversationService = conversationService;
        this.requestActorResolver = requestActorResolver;
    }

    @PostMapping
    public Mono<Conversation> createConversation(Authentication authentication) {
        return requestActorResolver.requireLegacyUserScopedActorId(authentication)
                .flatMap(userId -> {
                    Conversation conversation = new Conversation();
                    conversation.setUserId(userId);
                    conversation.setTitle("New Conversation");
                    return conversationService.createConversation(conversation);
                });
    }

    @PostMapping("/{conversationId}/messages")
    public Mono<Message> addMessage(@PathVariable Long conversationId, @Valid @RequestBody MessageDto messageDto, Authentication authentication) {
        return requestActorResolver.requireLegacyUserScopedActorId(authentication)
                .flatMap(userId -> conversationService.getConversationById(conversationId, userId)
                        .flatMap(conversation -> {
                            Message message = new Message();
                            message.setConversationId(conversation.getId());
                            message.setSender("user");
                            message.setContent(messageDto.getContent());
                            message.setModel(messageDto.getModel());
                            if (org.springframework.util.StringUtils.hasText(messageDto.getClientMessageId())) {
                                message.setDeduplicationKey("user:" + userId + ":" + messageDto.getClientMessageId().trim());
                            }
                            return conversationService.addMessageToConversation(message);
                        }));
    }

    @GetMapping("/{conversationId}/messages")
    public Flux<Message> getMessages(@PathVariable Long conversationId, Authentication authentication) {
        return requestActorResolver.requireLegacyUserScopedActorId(authentication)
                .flatMapMany(userId -> conversationService.getMessagesByConversationId(conversationId, userId));
    }

    @GetMapping("/recent")
    public Flux<ConversationWithPreview> getRecentConversations(Authentication authentication) {
        return requestActorResolver.requireLegacyUserScopedActorId(authentication)
                .flatMapMany(conversationService::getRecentConversationsWithPreview);
    }

    @GetMapping("/admin")
    public Flux<ConversationWithPreview> getAllConversationsForAdmin(@RequestParam(defaultValue = "0") Integer page,
                                                                     @RequestParam(defaultValue = "20") Integer size) {
        return conversationService.getAllRecentConversations(page, size);
    }

    @GetMapping("/admin/page")
    public Mono<AdminPageDto<ConversationWithPreview>> getAllConversationsPageForAdmin(
            @RequestParam(defaultValue = "0") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(required = false) String keyword) {
        return conversationService.getAllRecentConversationsPage(page, size, keyword);
    }

    @DeleteMapping("/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversation(@PathVariable Long conversationId, Authentication authentication) {
        return requestActorResolver.requireLegacyUserScopedActorId(authentication)
                .flatMap(userId -> conversationService.deleteConversation(conversationId, userId))
                .thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }

    @DeleteMapping("/admin/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversationForAdmin(@PathVariable Long conversationId) {
        return conversationService.deleteConversationAsAdmin(conversationId)
                .thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }

}
