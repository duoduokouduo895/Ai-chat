package org.example.ai_agent.controller;

import org.example.ai_agent.dto.chat.MessageDto;
import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.exception.GuestLimitExceededException;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.ConversationService;
import org.example.ai_agent.service.SearchService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = {"http://localhost:3000"}, allowCredentials = "true")
public class ConversationController {

    private final ConversationService conversationService;
    private final SearchService searchService;
    private final UserRepository userRepository;

    public ConversationController(ConversationService conversationService, SearchService searchService, UserRepository userRepository) {
        this.conversationService = conversationService;
        this.searchService = searchService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public Mono<Conversation> createConversation(Principal principal) {
        String principalName = principal.getName();
        // 首先尝试按用户名查找（针对注册用户），如果找不到，则尝试按GuestId查找（针对游客）
        return userRepository.findByUsername(principalName)
                .switchIfEmpty(userRepository.findByGuestId(principalName))
                .flatMap(user -> {
                    Conversation conversation = new Conversation();
                    conversation.setUserId(user.getUserId());
                    conversation.setTitle("New Conversation"); // 可以设置为一个更动态的标题
                    return conversationService.createConversation(conversation);
                })
                .switchIfEmpty(Mono.error(new IllegalStateException("无法为 principal 创建会话: " + principalName)));
    }

    @PostMapping("/{conversationId}/messages")
    public Mono<Message> addMessage(@PathVariable Long conversationId, @RequestBody MessageDto messageDto) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setSender(messageDto.getSender());
        message.setContent((String) messageDto.getContent());
        message.setModel(messageDto.getModel());
        return conversationService.addMessageToConversation(message);
    }

    @GetMapping("/{conversationId}/messages")
    public Flux<Message> getMessages(@PathVariable Long conversationId) {
        return conversationService.getMessagesByConversationId(conversationId);
    }

    @GetMapping("/recent")
    public Flux<ConversationWithPreview> getRecentConversations(Principal principal) {
        // 根据用户名查找用户，然后获取该用户的最近会话（包含预览信息）
        return userRepository.findByUsername(principal.getName())
                .flatMapMany(user -> conversationService.getRecentConversationsWithPreview(user.getUserId()));
    }

    @DeleteMapping("/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversation(@PathVariable Long conversationId, Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .switchIfEmpty(userRepository.findByGuestId(principal.getName()))
                .flatMap(user -> conversationService.deleteConversation(conversationId, user.getUserId()))
                .then(Mono.just(new ResponseEntity<Void>(HttpStatus.NO_CONTENT)))
                .defaultIfEmpty(new ResponseEntity<>(HttpStatus.NOT_FOUND));
    }

    @ExceptionHandler(GuestLimitExceededException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGuestLimitExceeded(GuestLimitExceededException ex) {
        return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("success", false, "message", ex.getMessage())));
    }
}
