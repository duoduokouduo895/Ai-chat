package org.example.ai_agent.config.messaging;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.autoconfigure.amqp.RabbitTemplateCustomizer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RabbitMQ消息队列配置类
 * 功能说明：
 * 1. 配置AI处理任务的队列、交换机和绑定关系
 * 2. 支持异步AI请求处理，提升系统响应速度
 * 3. 实现任务重试机制，提高处理成功率
 * 4. 支持流量削峰，保护系统资源
 * 5. 集成Nacos配置中心，支持动态配置管理
 * 
 * 故障排除说明：
 * 如果遇到 "Failed to check/redeclare auto-delete queue(s)" 错误：
 * 1. 检查RabbitMQ管理界面，删除冲突的队列和交换机
 * 2. 或者使用以下命令清理（在RabbitMQ服务器上执行）：
 *    rabbitmqctl delete_queue ai-task-queue
 *    rabbitmqctl delete_queue ai-task-dlq
 *    rabbitmqctl delete_exchange ai-task-exchange
 *    rabbitmqctl delete_exchange ai-task-dlx
 * 3. 确保所有队列和交换机的参数（durable、autoDelete、exclusive）保持一致
 * 4. 重启应用程序以重新创建队列和交换机
 */
@Configuration
@RefreshScope
public class RabbitMQConfig {

    private static final Logger logger = LoggerFactory.getLogger(RabbitMQConfig.class);

    // AI处理任务队列名称
    @Value("${rabbitmq.queue.ai-task:ai-task-queue}")
    private String aiTaskQueue;

    // AI处理任务死信队列名称
    @Value("${rabbitmq.queue.ai-task-dlq:ai-task-dlq}")
    private String aiTaskDlq;

    // AI处理任务交换机名称
    @Value("${rabbitmq.exchange.ai-task:ai-task-exchange}")
    private String aiTaskExchange;

    // AI处理任务死信交换机名称
    @Value("${rabbitmq.exchange.ai-task-dlx:ai-task-dlx}")
    private String aiTaskDlx;

    // AI处理任务路由键
    @Value("${rabbitmq.routing-key.ai-task:ai.task.process}")
    private String aiTaskRoutingKey;

    // AI处理任务死信路由键
    @Value("${rabbitmq.routing-key.ai-task-dlq:ai.task.dlq}")
    private String aiTaskDlqRoutingKey;

    /**
     * 配置消息转换器
     * 作用：将Java对象转换为JSON格式进行消息传输
     * 注意：这个Bean不会与Spring Boot自动配置冲突
     */
    @Bean("rabbitMessageConverter")
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    /**
     * 使用RabbitTemplateCustomizer自定义RabbitTemplate配置
     * 作用：避免与Spring Boot自动配置的RabbitTemplate Bean冲突
     * 原理：Spring Boot会自动创建RabbitTemplate，我们通过Customizer来定制它
     * 优势：不会产生Bean名称冲突，同时能够自定义消息转换器
     * 注意：使用自定义Bean名称避免与默认配置冲突
     */
    @Bean("customRabbitTemplateCustomizer")
    public RabbitTemplateCustomizer rabbitTemplateCustomizer() {
        return rabbitTemplate -> {
            // 设置JSON消息转换器，用于对象序列化
            rabbitTemplate.setMessageConverter(rabbitMessageConverter());
            // 可以在这里添加其他RabbitTemplate自定义配置
            // 例如：rabbitTemplate.setMandatory(true); // 强制路由
        };
    }

    /**
     * 创建AI处理任务死信交换机
     * 作用：处理失败的消息，实现消息重试机制
     * 参数说明：
     * - name: 交换机名称
     * - durable: true - 持久化，服务器重启后交换机仍存在
     * - 默认不自动删除，避免队列重新声明错误
     */
    @Bean
    public DirectExchange aiTaskDeadLetterExchange() {
        try {
            logger.info("创建AI任务死信交换机: {}", aiTaskDlx);
            return ExchangeBuilder.directExchange(aiTaskDlx)
                    .durable(true)
                    .build();
        } catch (Exception e) {
            logger.error("创建AI任务死信交换机失败: {}, 错误: {}", aiTaskDlx, e.getMessage());
            throw new RuntimeException("Failed to create dead letter exchange: " + aiTaskDlx, e);
        }
    }

    /**
     * 创建AI处理任务死信队列
     * 作用：存储处理失败的AI任务消息
     * 参数说明：
     * - durable: true - 持久化队列，服务器重启后队列仍存在
     * - 默认不自动删除，避免队列重新声明错误
     * - 默认为非独占队列，允许多个连接访问
     */
    @Bean
    public Queue aiTaskDeadLetterQueue() {
        try {
            logger.info("创建AI任务死信队列: {}", aiTaskDlq);
            return QueueBuilder.durable(aiTaskDlq)
                    .build();
        } catch (Exception e) {
            logger.error("创建AI任务死信队列失败: {}, 错误: {}", aiTaskDlq, e.getMessage());
            throw new RuntimeException("Failed to create dead letter queue: " + aiTaskDlq, e);
        }
    }

    /**
     * 绑定AI处理任务死信队列到死信交换机
     * 作用：建立死信队列和死信交换机的路由关系
     */
    @Bean
    public Binding aiTaskDeadLetterBinding() {
        return BindingBuilder
                .bind(aiTaskDeadLetterQueue())
                .to(aiTaskDeadLetterExchange())
                .with(aiTaskDlqRoutingKey);
    }

    /**
     * 创建AI处理任务交换机
     * 作用：接收AI处理任务消息并路由到相应队列
     * 参数说明：
     * - name: 交换机名称
     * - durable: true - 持久化，服务器重启后交换机仍存在
     * - 默认不自动删除，避免队列重新声明错误
     */
    @Bean
    public DirectExchange aiTaskExchange() {
        try {
            logger.info("创建AI任务交换机: {}", aiTaskExchange);
            return ExchangeBuilder.directExchange(aiTaskExchange)
                    .durable(true)
                    .build();
        } catch (Exception e) {
            logger.error("创建AI任务交换机失败: {}, 错误: {}", aiTaskExchange, e.getMessage());
            throw new RuntimeException("Failed to create task exchange: " + aiTaskExchange, e);
        }
    }

    /**
     * 创建AI处理任务队列
     * 作用：存储待处理的AI任务消息，配置死信队列支持重试
     * 参数说明：
     * - durable: true - 持久化队列，服务器重启后队列仍存在
     * - 默认不自动删除，避免队列重新声明错误
     * - 默认为非独占队列，允许多个连接访问
     * - x-dead-letter-exchange: 死信交换机，处理失败消息
     * - x-dead-letter-routing-key: 死信路由键
     * - x-message-ttl: 消息存活时间，5分钟后进入死信队列
     */
    @Bean
    public Queue aiTaskQueue() {
        try {
            logger.info("创建AI任务队列: {}, 死信交换机: {}", aiTaskQueue, aiTaskDlx);
            return QueueBuilder.durable(aiTaskQueue)
                    .withArgument("x-dead-letter-exchange", aiTaskDlx)
                    .withArgument("x-dead-letter-routing-key", aiTaskDlqRoutingKey)
                    .withArgument("x-message-ttl", 300000) // 消息TTL 5分钟
                    .build();
        } catch (Exception e) {
            logger.error("创建AI任务队列失败: {}, 错误: {}", aiTaskQueue, e.getMessage());
            throw new RuntimeException("Failed to create task queue: " + aiTaskQueue, e);
        }
    }

    /**
     * 绑定AI处理任务队列到交换机
     * 作用：建立AI任务队列和交换机的路由关系
     */
    @Bean
    public Binding aiTaskBinding() {
        return BindingBuilder
                .bind(aiTaskQueue())
                .to(aiTaskExchange())
                .with(aiTaskRoutingKey);
    }
}