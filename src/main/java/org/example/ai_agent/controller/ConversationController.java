package org.example.ai_agent.controller;

import org.example.ai_agent.dto.chat.ConversationWithPreview;
import org.example.ai_agent.dto.chat.MessageDto;
import org.example.ai_agent.entity.Conversation;
import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.ConversationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
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

import java.security.Principal;

@RestController
@RequestMapping("/api/conversations")
@CrossOrigin(origins = {"https://dduo.chat", "http://dduo.chat", "http://localhost:3000", "http://localhost:5173"}, allowCredentials = "true")
public class ConversationController {

    private final ConversationService conversationService;
    private final UserRepository userRepository;

    public ConversationController(ConversationService conversationService, UserRepository userRepository) {
        this.conversationService = conversationService;
        this.userRepository = userRepository;
    }

    @PostMapping
    public Mono<Conversation> createConversation(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")))
                .flatMap(user -> {
                    Conversation conversation = new Conversation();
                    conversation.setUserId(user.getUserId());
                    conversation.setTitle("New Conversation");
                    return conversationService.createConversation(conversation);
                });
    }

    @PostMapping("/{conversationId}/messages")
    public Mono<Message> addMessage(@PathVariable Long conversationId, @RequestBody MessageDto messageDto, Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")))
                .flatMap(user -> conversationService.getConversationById(conversationId, user.getUserId()))
                .flatMap(conversation -> {
                    Message message = new Message();
                    message.setConversationId(conversation.getId());
                    message.setSender(messageDto.getSender());
                    message.setContent((String) messageDto.getContent());
                    message.setModel(messageDto.getModel());
                    return conversationService.addMessageToConversation(message);
                });
    }

    @GetMapping("/{conversationId}/messages")
    public Flux<Message> getMessages(@PathVariable Long conversationId, Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")))
                .flatMapMany(user -> conversationService.getMessagesByConversationId(conversationId, user.getUserId()));
    }

    @GetMapping("/recent")
    public Flux<ConversationWithPreview> getRecentConversations(Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")))
                .flatMapMany(user -> conversationService.getRecentConversationsWithPreview(user.getUserId()));
    }

    @GetMapping("/admin")
    public Flux<ConversationWithPreview> getAllConversationsForAdmin(@RequestParam(defaultValue = "0") Integer page,
                                                                     @RequestParam(defaultValue = "20") Integer size) {
        return conversationService.getAllRecentConversations(page, size);
    }

    @DeleteMapping("/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversation(@PathVariable Long conversationId, Principal principal) {
        return userRepository.findByUsername(principal.getName())
                .switchIfEmpty(Mono.error(new SecurityException("Current user does not exist")))
                .flatMap(user -> conversationService.deleteConversation(conversationId, user.getUserId()))
                .thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }

    @DeleteMapping("/admin/{conversationId}")
    public Mono<ResponseEntity<Void>> deleteConversationForAdmin(@PathVariable Long conversationId) {
        return conversationService.deleteConversationAsAdmin(conversationId)
                .thenReturn(new ResponseEntity<Void>(HttpStatus.NO_CONTENT));
    }
}


