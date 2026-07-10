package org.example.ai_agent.controller;

import jakarta.validation.Valid;
import org.example.ai_agent.dto.admin.AdminPageDto;
import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.dto.chat.MessageDto;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.ConversationService;
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
    private final UserRepository userRepository;

    public ConversationController(ConversationService conversationService, UserRepository userRepository) {
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public Mono<Conversation> createConversation(Authentication authentication) {
        return resolveCurrentUserId(authentication)
                .flatMap(userId -> {
                    Conversation conversation = new Conversation();
                    conversation.setUserId(userId);
                    conversation.setTitle("New Conversation");
                    return conversationService.createConversation(conversation);
                });
    }

    @PostMapping("/{conversationId}/messages")
    public Mono<Message> addMessage(@PathVariable Long conversationId, @Valid @RequestBody MessageDto messageDto, Authentication authentication) {
        return resolveCurrentUserId(authentication)
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
        return resolveCurrentUserId(authentication)
                .flatMapMany(userId -> conversationService.getMessagesByConversationId(conversationId, userId));
    }

    @GetMapping("/recent")
    public Flux<ConversationWithPreview> getRecentConversations(Authentication authentication) {
        return resolveCurrentUserId(authentication)
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
        return resolveCurrentUserId(authentication)
                .flatMap(userId -> conversationService.deleteConversation(conversationId, userId))
                .thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }

    @DeleteMapping("/admin/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversationForAdmin(@PathVariable Long conversationId) {
        return conversationService.deleteConversationAsAdmin(conversationId)
                .thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }

    private Mono<Long> resolveCurrentUserId(Authentication authentication) {
        if (authentication == null) {
            return Mono.error(new SecurityException("Current user is not authenticated"));
        }

        Object details = authentication.getDetails();
        if (details instanceof Number number) {
            return Mono.just(number.longValue());
        }
        if (details instanceof String value && !value.isBlank()) {
            try {
                return Mono.just(Long.parseLong(value));
            } catch (NumberFormatException ignored) {
                // Fall back to the username lookup for legacy authentication sources.
            }
        }

        return userRepository.findByUsername(authentication.getName())
                .map(user -> user.getUserId())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")));
    }
}


