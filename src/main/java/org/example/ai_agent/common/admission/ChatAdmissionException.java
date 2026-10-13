package org.example.ai_agent.common.admission;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/** 请求在建立 SSE 响应前被成本/权限策略拒绝。 */
public class ChatAdmissionException extends ResponseStatusException {

    public ChatAdmissionException(HttpStatus status, String reason) {
        super(status, reason);
    }
}
