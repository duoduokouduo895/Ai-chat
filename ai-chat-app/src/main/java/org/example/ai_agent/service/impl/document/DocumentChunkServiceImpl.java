package org.example.ai_agent.service.impl.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.segment.TextSegment;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.constants.DocumentProcessingConstants;
import org.example.ai_agent.constants.DocumentProcessingStatus;
import org.example.ai_agent.dto.document.DocumentChunkDto;
import org.example.ai_agent.entity.KnowledgeDocumentChunk;
import org.example.ai_agent.repository.document.DocumentChunkBulkUpdateRepository;
import org.example.ai_agent.repository.knowledge.KnowledgeDocumentChunkRepository;
import org.example.ai_agent.service.DocumentChunkService;
import org.example.ai_agent.service.DocumentParserService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.IntStream;

/**
 * 文档块处理服务实现类
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class DocumentChunkServiceImpl implements DocumentChunkService {
    
    private final KnowledgeDocumentChunkRepository chunkRepository;

    private final DocumentChunkBulkUpdateRepository chunkBulkUpdateRepository;
    
    private final DocumentParserService documentParserService;
    
    private final ObjectMapper objectMapper;

    public DocumentChunkServiceImpl(KnowledgeDocumentChunkRepository chunkRepository,
                                   DocumentChunkBulkUpdateRepository chunkBulkUpdateRepository,
                                   DocumentParserService documentParserService,
                                   ObjectMapper objectMapper) {
        this.chunkRepository = chunkRepository;
        this.chunkBulkUpdateRepository = chunkBulkUpdateRepository;
        this.documentParserService = documentParserService;
        this.objectMapper = objectMapper;
    }
    
    @Override
    public Mono<List<DocumentChunkDto>> createDocumentChunks(Long documentId, Long knowledgeBaseId, String content) {
        return chunkRepository.findByDocumentIdOrderByChunkIndex(documentId)
        .collectList()
        .flatMap(existingChunks -> {
            if (!existingChunks.isEmpty()) {
                log.info("复用已创建的文档块: documentId={}, chunkCount={}", documentId, existingChunks.size());
                return Mono.just(existingChunks.stream().map(this::toDto).toList());
            }

            int contentLength = content == null ? 0 : content.length();
            return Mono.fromCallable(() -> {
            log.info("开始创建文档块: documentId={}, knowledgeBaseId={}, contentLength={}",
                    documentId, knowledgeBaseId, contentLength);
            
            // 创建文档对象
            Document document = Document.from(content);
            return document;
            })
            .flatMap(document -> documentParserService.splitDocument(document))
            .flatMap(textSegments -> {
            // 转换为实体对象
            List<KnowledgeDocumentChunk> chunks = IntStream.range(0, textSegments.size())
                .mapToObj(i -> {
                    TextSegment segment = textSegments.get(i);
                    KnowledgeDocumentChunk chunk = new KnowledgeDocumentChunk();
                    chunk.setDocumentId(documentId);
                    chunk.setGeneration(1L);
                    chunk.setKnowledgeBaseId(knowledgeBaseId);
                    chunk.setChunkIndex(i);
                    chunk.setContent(segment.text());
                    chunk.setCharacterCount(segment.text().length());
                    
                    // 安全序列化metadata为JSON
                    try {
                        String metadataJson = objectMapper.writeValueAsString(segment.metadata().toMap());
                        chunk.setMetadata(metadataJson);
                    } catch (Exception e) {
                        log.warn("序列化metadata失败: chunkIndex={}, errorType={}", i, e.getClass().getSimpleName());
                        chunk.setMetadata("{}"); // 默认空 JSON 对象
                    }
                    
                    chunk.setIsEmbedded(false);
                    chunk.setEmbeddingStatus(DocumentProcessingStatus.PENDING.value());
                    
                    LocalDateTime now = LocalDateTime.now();
                    chunk.setCreatedAt(now);
                    chunk.setUpdatedAt(now);
                    
                    return chunk;
                })
                .toList();
            
            // 批量保存
            return chunkRepository.saveAll(chunks)
                .collectList()
                .map(savedChunks -> savedChunks.stream()
                    .map(this::toDto)
                    .toList());
            });
        })
        .doOnSuccess(chunks -> log.info("文档块创建成功: documentId={}, chunkCount={}", 
                documentId, chunks.size()))
        .doOnError(error -> log.error("文档块创建失败: documentId={}, errorType={}",
                documentId, error.getClass().getSimpleName()))
        .subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Flux<DocumentChunkDto> getDocumentChunks(Long documentId) {
        return chunkRepository.findByDocumentIdOrderByChunkIndex(documentId)
            .map(this::toDto)
            .doOnSubscribe(s -> log.debug("查询文档块: documentId={}", documentId));
    }
    
    @Override
    public Flux<DocumentChunkDto> getPendingEmbeddingChunks(Long documentId) {
        return chunkRepository.findByDocumentIdAndEmbeddingStatus(
                documentId, DocumentProcessingStatus.PENDING.value())
            .map(this::toDto)
            .doOnSubscribe(s -> log.debug("查询待向量化文档块: documentId={}", documentId));
    }
    
    @Override
    public Mono<Boolean> updateChunkEmbeddingStatus(Long chunkId, String embeddingId, 
                                                   String status, String errorMessage) {
        return chunkRepository.findById(chunkId)
            .flatMap(chunk -> {
                chunk.setEmbeddingId(embeddingId);
                chunk.setEmbeddingStatus(status);
                chunk.setIsEmbedded(DocumentProcessingStatus.COMPLETED.value().equals(status));
                chunk.setEmbeddingError(errorMessage);
                chunk.setUpdatedAt(LocalDateTime.now());
                
                return chunkRepository.save(chunk);
            })
            .map(savedChunk -> true)
            .defaultIfEmpty(false)
            .doOnSuccess(result -> log.debug("更新文档块向量化状态: chunkId={}, status={}, result={}", 
                    chunkId, status, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Boolean> batchUpdateChunkEmbeddingStatus(List<Long> chunkIds, List<String> embeddingIds, String status) {
        return chunkBulkUpdateRepository.updateEmbeddingStatus(chunkIds, embeddingIds, status)
            .map(updatedRows -> updatedRows == chunkIds.size())
            .doOnSuccess(result -> log.info("批量更新文档块向量化状态: chunkCount={}, status={}, result={}", 
                    chunkIds.size(), status, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Boolean> deleteDocumentChunks(Long documentId) {
        return chunkRepository.deleteByDocumentId(documentId)
            .map(count -> count > 0)
            .doOnSuccess(result -> log.info("删除文档块: documentId={}, result={}", documentId, result))
            .onErrorReturn(false);
    }
    
    @Override
    public Mono<Integer> countDocumentChunks(Long documentId) {
        return chunkRepository.countByDocumentId(documentId)
            .map(Math::toIntExact)
            .doOnSuccess(count -> log.debug("统计文档块数量: documentId={}, count={}", documentId, count));
    }
    
    @Override
    public Mono<Integer> countEmbeddedChunks(Long documentId) {
        return chunkRepository.countByDocumentIdAndIsEmbedded(documentId, true)
            .map(Math::toIntExact)
            .doOnSuccess(count -> log.debug("统计已向量化文档块数量: documentId={}, count={}", documentId, count));
    }
    
    @Override
    public DocumentChunkDto toDto(KnowledgeDocumentChunk entity) {
        if (entity == null) {
            return null;
        }
        
        DocumentChunkDto dto = new DocumentChunkDto();
        dto.setId(entity.getId());
        dto.setDocumentId(entity.getDocumentId());
        dto.setKnowledgeBaseId(entity.getKnowledgeBaseId());
        dto.setChunkIndex(entity.getChunkIndex());
        dto.setContent(entity.getContent());
        dto.setCharacterCount(entity.getCharacterCount());
        dto.setMetadata(entity.getMetadata());
        dto.setEmbeddingId(entity.getEmbeddingId());
        dto.setIsEmbedded(entity.getIsEmbedded());
        dto.setEmbeddingStatus(entity.getEmbeddingStatus());
        dto.setEmbeddingError(entity.getEmbeddingError());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        
        return dto;
    }
}
