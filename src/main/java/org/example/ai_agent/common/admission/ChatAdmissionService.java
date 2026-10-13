package org.example.ai_agent.common.admission;

import org.example.ai_agent.llm.ModelCatalogProperties;
import org.example.ai_agent.llm.ModelCatalogProperties.ModelConfig;
import org.example.ai_agent.llm.ModelCatalogProperties.ModelType;
import org.example.ai_agent.chat.dto.ChatRequest;
import org.example.ai_agent.auth.security.TokenRedisKeys;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 在调用模型前实施配额、模型权限和分布式并发租约。
 * 释放动作必须绑定到 SSE 流的 doFinally，租约 TTL 仅作为进程崩溃时的兜底。
 */
@Service
public class ChatAdmissionService {

    private final RedisAdmissionStore redisAdmissionStore;
    private final ChatAdmissionProperties properties;
    private final ModelCatalogProperties modelCatalogProperties;

    public ChatAdmissionService(RedisAdmissionStore redisAdmissionStore,
                                ChatAdmissionProperties properties,
                                ModelCatalogProperties modelCatalogProperties) {
        this.redisAdmissionStore = redisAdmissionStore;
        this.properties = properties;
        this.modelCatalogProperties = modelCatalogProperties;
    }

    public Mono<AdmissionLease> admit(Long userId, boolean guest, ChatRequest request) {
        return Mono.defer(() -> {
            if (!properties.isEnabled()) {
                return Mono.just(AdmissionLease.noop());
            }

            if (userId == null || userId <= 0) {
                return Mono.error(new ChatAdmissionException(HttpStatus.UNAUTHORIZED, "用户身份无效"));
            }

            if (guest) {
                validateGuestRequest(request);
            }

            String member = UUID.randomUUID().toString();
            String globalKey = TokenRedisKeys.chatConcurrencyGlobal();
            String userKey = TokenRedisKeys.chatConcurrencyUser(userId);
            int userLimit = guest ? properties.getGuest().concurrentLimit() : properties.registeredConcurrentLimit();

            return redisAdmissionStore.tryAcquireLease(globalKey, member,
                            properties.globalConcurrentLimit(), properties.concurrencyLease())
                    .flatMap(globalAcquired -> {
                        if (!globalAcquired) {
                            return Mono.error(new ChatAdmissionException(HttpStatus.TOO_MANY_REQUESTS,
                                    "系统当前对话繁忙，请稍后重试"));
                        }
                        return redisAdmissionStore.tryAcquireLease(userKey, member, userLimit,
                                        properties.concurrencyLease())
                                .flatMap(userAcquired -> {
                                    if (!userAcquired) {
                                        return release(globalKey, member)
                                                .then(Mono.error(new ChatAdmissionException(HttpStatus.TOO_MANY_REQUESTS,
                                                        "当前账户已有进行中的对话，请等待完成后再试")));
                                    }
                                    return consumeGuestQuotaIfRequired(userId, guest)
                                            .thenReturn(new AdmissionLease(redisAdmissionStore, globalKey, userKey, member))
                                            .onErrorResume(error -> release(userKey, member)
                                                    .then(release(globalKey, member))
                                                    .then(Mono.error(error)));
                                });
                    });
        });
    }

    private Mono<Void> consumeGuestQuotaIfRequired(Long userId, boolean guest) {
        if (!guest) {
            return Mono.empty();
        }
        ChatAdmissionProperties.Guest guestProperties = properties.getGuest();
        return redisAdmissionStore.tryConsume(TokenRedisKeys.chatGuestQuota(userId),
                        guestProperties.requestLimit(), guestProperties.requestWindow())
                .flatMap(accepted -> accepted ? Mono.empty() : Mono.error(
                        new ChatAdmissionException(HttpStatus.TOO_MANY_REQUESTS,
                                "游客体验次数已用尽，请登录后继续使用")));
    }

    private void validateGuestRequest(ChatRequest request) {
        ChatAdmissionProperties.Guest guestProperties = properties.getGuest();
        if (!guestProperties.isRagEnabled() && request.isRagMode()) {
            throw new ChatAdmissionException(HttpStatus.FORBIDDEN, "游客模式不支持知识库对话");
        }

        List<String> allowedModels = guestProperties.getAllowedModels();
        if (allowedModels != null && !allowedModels.isEmpty()) {
            if (!allowedModels.contains(request.getModel())) {
                throw new ChatAdmissionException(HttpStatus.FORBIDDEN, "当前模型不支持游客体验");
            }
            return;
        }

        if (!isEnabledChatModel(request.getModel())) {
            throw new ChatAdmissionException(HttpStatus.FORBIDDEN, "当前模型不支持游客体验");
        }
    }

    private boolean isEnabledChatModel(String modelAlias) {
        if (!StringUtils.hasText(modelAlias)) {
            return false;
        }
        Map<String, ModelConfig> models = modelCatalogProperties.getModels();
        if (models == null || models.isEmpty()) {
            return false;
        }
        String requested = modelAlias.trim();
        ModelConfig direct = models.get(requested);
        if (direct != null) {
            return direct.isEnabled() && isChatType(direct);
        }
        return models.values().stream().anyMatch(config -> config != null
                && config.isEnabled()
                && isChatType(config)
                && requested.equals(config.getProviderModelName()));
    }

    private static boolean isChatType(ModelConfig config) {
        return config.getType() == null || config.getType() == ModelType.CHAT;
    }

    private Mono<Void> release(String key, String member) {
        return redisAdmissionStore.releaseLease(key, member)
                .onErrorResume(ignored -> Mono.empty());
    }

    public static final class AdmissionLease {
        private static final AdmissionLease NOOP = new AdmissionLease(null, null, null, null);

        private final RedisAdmissionStore store;
        private final String globalKey;
        private final String userKey;
        private final String member;

        private AdmissionLease(RedisAdmissionStore store, String globalKey, String userKey, String member) {
            this.store = store;
            this.globalKey = globalKey;
            this.userKey = userKey;
            this.member = member;
        }

        public static AdmissionLease noop() {
            return NOOP;
        }

        public Mono<Void> release() {
            if (store == null) {
                return Mono.empty();
            }
            return store.releaseLease(userKey, member)
                    .onErrorResume(ignored -> Mono.empty())
                    .then(store.releaseLease(globalKey, member)
                            .onErrorResume(ignored -> Mono.empty()));
        }
    }
}
