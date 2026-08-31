package org.example.ai_agent.controller.support;

import org.example.ai_agent.dto.knowledge.KnowledgeBaseDto;
import org.example.ai_agent.entity.User;
import org.example.ai_agent.repository.auth.UserRepository;
import org.example.ai_agent.service.KnowledgeBaseService;
import org.example.ai_agent.service.impl.identity.RepositoryActorIdentityLookup;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class KnowledgeAccessContextResolverTest {

    @Mock
    private KnowledgeBaseService knowledgeBaseService;
    @Mock
    private UserRepository userRepository;

    @Test
    void resolve_shouldReturnUserContext() {
        KnowledgeAccessContextResolver resolver =
                resolver();

        StepVerifier.create(resolver.resolve(authentication(11L, "ROLE_USER")))
                .assertNext(access -> {
                    org.junit.jupiter.api.Assertions.assertEquals(11L, access.userId());
                    org.junit.jupiter.api.Assertions.assertFalse(access.isAdmin());
                })
                .verifyComplete();
    }

    @Test
    void requireAdmin_shouldAllowAdmin() {
        KnowledgeAccessContextResolver resolver =
                resolver();

        StepVerifier.create(resolver.requireAdmin(authentication(1L, "ROLE_ADMIN")))
                .verifyComplete();
    }

    @Test
    void resolve_shouldRejectUnauthenticatedRequest() {
        KnowledgeAccessContextResolver resolver =
                resolver();

        StepVerifier.create(resolver.resolve(null))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals("当前用户未认证", error.getMessage());
                })
                .verify();
    }

    @Test
    void requireUser_shouldRejectInvalidDetails() {
        KnowledgeAccessContextResolver resolver =
                resolver();
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "alice", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER")));
        authentication.setDetails("invalid-id");

        StepVerifier.create(resolver.requireUser(authentication))
                .expectErrorSatisfies(error -> {
                    org.junit.jupiter.api.Assertions.assertInstanceOf(SecurityException.class, error);
                    org.junit.jupiter.api.Assertions.assertEquals("无法识别当前用户", error.getMessage());
                })
                .verify();
    }

    @Test
    void resolveAdminUploaderId_shouldFallbackToKnowledgeBaseCreator() {
        KnowledgeAccessContextResolver resolver =
                resolver();
        KnowledgeBaseDto knowledgeBase = new KnowledgeBaseDto();
        knowledgeBase.setId(66L);
        knowledgeBase.setCreatorId(501L);
        User creator = new User();
        creator.setUserId(501L);

        when(knowledgeBaseService.getKnowledgeBase(66L)).thenReturn(Mono.just(knowledgeBase));
        when(userRepository.findById(501L)).thenReturn(Mono.just(creator));

        StepVerifier.create(resolver.resolveAdminUploaderId(66L, null))
                .expectNext(501L)
                .verifyComplete();
    }

    private Authentication authentication(Object details, String role) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "actor", "n/a", List.of(new SimpleGrantedAuthority(role)));
        authentication.setDetails(details);
        return authentication;
    }

    private KnowledgeAccessContextResolver resolver() {
        return new KnowledgeAccessContextResolver(knowledgeBaseService,
                new RepositoryActorIdentityLookup(userRepository),
                new RequestActorResolver(new RepositoryActorIdentityLookup(userRepository)));
    }
}
