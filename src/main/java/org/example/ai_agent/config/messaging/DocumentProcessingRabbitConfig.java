package org.example.ai_agent.config.messaging;

import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 文档处理RabbitMQ配置
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Configuration
public class DocumentProcessingRabbitConfig {
    
    /**
     * 文档处理交换机
     */
    @Bean
    public TopicExchange documentProcessingExchange() {
        return ExchangeBuilder
            .topicExchange(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING)
            .durable(true)
            .build();
    }
    
    /**
     * 死信交换机
     */
    @Bean
    public DirectExchange documentDlxExchange() {
        return ExchangeBuilder
            .directExchange(DocumentProcessingConstants.Exchange.DOCUMENT_DLX)
            .durable(true)
            .build();
    }
    
    /**
     * 文档解析队列
     */
    @Bean
    public Queue documentParseQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_PARSE)
            .deadLetterExchange(DocumentProcessingConstants.Exchange.DOCUMENT_DLX)
            .deadLetterRoutingKey(DocumentProcessingConstants.RoutingKey.DOCUMENT_DLQ)
            .build();
    }

    /**
     * 文档解析重试队列
     */
    @Bean
    public Queue documentParseRetryQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_PARSE_RETRY)
            .ttl((int) DocumentProcessingConstants.Retry.RETRY_DELAY_MS)
            .deadLetterExchange(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING)
            .deadLetterRoutingKey(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE)
            .build();
    }
    
    /**
     * 文档分块队列
     */
    @Bean
    public Queue documentChunkQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_CHUNK)
            .deadLetterExchange(DocumentProcessingConstants.Exchange.DOCUMENT_DLX)
            .deadLetterRoutingKey(DocumentProcessingConstants.RoutingKey.DOCUMENT_DLQ)
            .build();
    }

    /**
     * 文档分块重试队列
     */
    @Bean
    public Queue documentChunkRetryQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_CHUNK_RETRY)
            .ttl((int) DocumentProcessingConstants.Retry.RETRY_DELAY_MS)
            .deadLetterExchange(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING)
            .deadLetterRoutingKey(DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK)
            .build();
    }
    
    /**
     * 文档向量化队列
     */
    @Bean
    public Queue documentEmbedQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_EMBED)
            .deadLetterExchange(DocumentProcessingConstants.Exchange.DOCUMENT_DLX)
            .deadLetterRoutingKey(DocumentProcessingConstants.RoutingKey.DOCUMENT_DLQ)
            .build();
    }

    /**
     * 文档向量化重试队列
     */
    @Bean
    public Queue documentEmbedRetryQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_EMBED_RETRY)
            .ttl((int) DocumentProcessingConstants.Retry.RETRY_DELAY_MS)
            .deadLetterExchange(DocumentProcessingConstants.Exchange.DOCUMENT_PROCESSING)
            .deadLetterRoutingKey(DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED)
            .build();
    }
    
    /**
     * 死信队列
     */
    @Bean
    public Queue documentDlqQueue() {
        return QueueBuilder
            .durable(DocumentProcessingConstants.Queue.DOCUMENT_DLQ)
            .build();
    }
    
    /**
     * 绑定解析队列到交换机
     */
    @Bean
    public Binding documentParseBinding() {
        return BindingBuilder
            .bind(documentParseQueue())
            .to(documentProcessingExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE);
    }

    /**
     * 绑定解析重试队列到交换机
     */
    @Bean
    public Binding documentParseRetryBinding() {
        return BindingBuilder
            .bind(documentParseRetryQueue())
            .to(documentProcessingExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_PARSE_RETRY);
    }
    
    /**
     * 绑定分块队列到交换机
     */
    @Bean
    public Binding documentChunkBinding() {
        return BindingBuilder
            .bind(documentChunkQueue())
            .to(documentProcessingExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK);
    }

    /**
     * 绑定分块重试队列到交换机
     */
    @Bean
    public Binding documentChunkRetryBinding() {
        return BindingBuilder
            .bind(documentChunkRetryQueue())
            .to(documentProcessingExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_CHUNK_RETRY);
    }
    
    /**
     * 绑定向量化队列到交换机
     */
    @Bean
    public Binding documentEmbedBinding() {
        return BindingBuilder
            .bind(documentEmbedQueue())
            .to(documentProcessingExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED);
    }

    /**
     * 绑定向量化重试队列到交换机
     */
    @Bean
    public Binding documentEmbedRetryBinding() {
        return BindingBuilder
            .bind(documentEmbedRetryQueue())
            .to(documentProcessingExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_EMBED_RETRY);
    }
    
    /**
     * 绑定死信队列到死信交换机
     */
    @Bean
    public Binding documentDlqBinding() {
        return BindingBuilder
            .bind(documentDlqQueue())
            .to(documentDlxExchange())
            .with(DocumentProcessingConstants.RoutingKey.DOCUMENT_DLQ);
    }
}
