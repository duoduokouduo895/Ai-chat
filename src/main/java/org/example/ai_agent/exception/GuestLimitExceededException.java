package org.example.ai_agent.exception;

/**
 * 游客访问限制异常
 * 当游客用户超出访问限制时抛出此异常
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2024
 */
public class GuestLimitExceededException extends RuntimeException {
    
    public GuestLimitExceededException(String message) {
        super(message);
    }
    
    public GuestLimitExceededException(String message, Throwable cause) {
        super(message, cause);
    }
}