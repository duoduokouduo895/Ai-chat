package org.example.ai_agent.controller;

import org.example.ai_agent.dto.document.DocumentUploadCapabilitiesDto;
import org.example.ai_agent.service.document.DocumentUploadCapabilitiesService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/** 为用户端上传页面提供当前生效的服务端限制。 */
@RestController
@RequestMapping("/api/knowledge")
public class DocumentUploadCapabilitiesController {

    private final DocumentUploadCapabilitiesService capabilitiesService;

    public DocumentUploadCapabilitiesController(DocumentUploadCapabilitiesService capabilitiesService) {
        this.capabilitiesService = capabilitiesService;
    }

    @GetMapping("/upload-capabilities")
    public Mono<DocumentUploadCapabilitiesDto> getUploadCapabilities() {
        return Mono.fromSupplier(capabilitiesService::current);
    }
}
