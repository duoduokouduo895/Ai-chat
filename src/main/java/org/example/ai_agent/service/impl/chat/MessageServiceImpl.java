package org.example.ai_agent.service.impl.chat;

import org.example.ai_agent.entity.Message;
import org.example.ai_agent.repository.chat.MessageRepository;
import org.example.ai_agent.service.MessageService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;

@Service
public class MessageServiceImpl implements MessageService {

    private final MessageRepository messageRepository;

    public MessageServiceImpl(MessageRepository messageRepository) {
        this.messageRepository = messageRepository;
    }

    @Override
    public Mono<Message> saveMessage(Message message) {
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }
        return messageRepository.save(message);
    }

    @Override
    public Flux<Message> findMessagesByConversationId(Long conversationId) {
        return messageRepository.findByConversationIdOrderByTimestampAsc(conversationId);
    }
}
