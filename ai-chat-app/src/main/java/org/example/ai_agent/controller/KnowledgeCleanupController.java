package org.example.ai_agent.controller;

import org.example.ai_agent.controller.support.KnowledgeAccessContextResolver;
import org.example.ai_agent.knowledge.ingestion.CleanupOperation;
import org.example.ai_agent.service.impl.knowledge.KnowledgeCleanupCommands;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/knowledge/cleanup-operations")
public class KnowledgeCleanupController {
    private final KnowledgeCleanupCommands commands;
    private final KnowledgeAccessContextResolver access;

    public KnowledgeCleanupController(KnowledgeCleanupCommands commands, KnowledgeAccessContextResolver access) {
        this.commands = commands; this.access = access;
    }

    @GetMapping("/{id}")
    public Mono<CleanupOperation> get(@PathVariable String id, Authentication authentication) {
        return access.resolve(authentication).flatMap(actor -> commands.get(id, actor.userId(), actor.isAdmin()));
    }

    @GetMapping
    public Flux<CleanupOperation> failed(Authentication authentication) {
        return access.resolve(authentication).flatMapMany(actor -> commands.failed(actor.isAdmin()));
    }

    @PostMapping("/{id}/retry")
    public Mono<CleanupOperation> retry(@PathVariable String id, Authentication authentication) {
        return access.resolve(authentication).flatMap(actor -> commands.retry(id, actor.userId(), actor.isAdmin()));
    }
}
