package org.example.ai_agent.document.pipeline;

import dev.langchain4j.data.document.Document;
import org.example.ai_agent.document.config.DocumentProcessingProperties;
import org.example.ai_agent.knowledge.entity.KnowledgeDocument;
import org.example.ai_agent.document.service.DocumentChunkService;
import org.example.ai_agent.document.service.DocumentParserService;
import org.example.ai_agent.knowledge.service.KnowledgeChunkSearchService;
import org.example.ai_agent.common.storage.OssService;
import org.example.ai_agent.llm.AiModelRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.example.ai_agent.document.service.DocumentParsingTimeoutException;

@ExtendWith(MockitoExtension.class)
class DocumentProcessingStagesTest {

    @Mock
    private DocumentParserService documentParserService;
    @Mock
    private DocumentChunkService documentChunkService;
    @Mock
    private OssService ossService;
    @Mock
    private KnowledgeChunkSearchService knowledgeChunkSearchService;
    @Mock
    private AiModelRegistry aiModelRegistry;

    @Test
    void parseDocumentFromOss_shouldCloseTheOssStreamAfterSuccessfulParsing() {
        CloseTrackingInputStream stream = new CloseTrackingInputStream();
        KnowledgeDocument document = document(1L);
        when(ossService.getFileInputStream("kb_1/readme.md")).thenReturn(Mono.just(stream));
        when(documentParserService.parseDocument(any(), any(), any()))
                .thenReturn(Mono.just(Document.from("parsed")));

        StepVerifier.create(stages().parseDocumentFromOss(document, "auto"))
                .expectNext("parsed")
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertTrue(stream.closed);
    }

    @Test
    void parseDocumentFromOss_shouldCloseTheOssStreamWhenParsingTimesOut() {
        CloseTrackingInputStream stream = new CloseTrackingInputStream();
        KnowledgeDocument document = document(2L);
        DocumentProcessingProperties properties = properties();
        properties.setParseTimeout(java.time.Duration.ofMillis(1));
        when(ossService.getFileInputStream("kb_1/readme.md")).thenReturn(Mono.just(stream));
        when(documentParserService.parseDocument(any(), any(), any())).thenReturn(Mono.never());

        StepVerifier.create(stages(properties).parseDocumentFromOss(document, "auto"))
                .expectError(DocumentParsingTimeoutException.class)
                .verify();

        org.junit.jupiter.api.Assertions.assertTrue(stream.closed);
    }

    private DocumentProcessingStages stages() {
        return stages(properties());
    }

    private DocumentProcessingStages stages(DocumentProcessingProperties properties) {
        return new DocumentProcessingStages(
                documentParserService,
                documentChunkService,
                ossService,
                knowledgeChunkSearchService,
                aiModelRegistry,
                properties);
    }

    private DocumentProcessingProperties properties() {
        return new DocumentProcessingProperties();
    }

    private KnowledgeDocument document(Long id) {
        KnowledgeDocument document = new KnowledgeDocument();
        document.setId(id);
        document.setOssPath("kb_1/readme.md");
        document.setFileName("readme.md");
        document.setFileType("md");
        return document;
    }

    private static final class CloseTrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private CloseTrackingInputStream() {
            super(new byte[0]);
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}
