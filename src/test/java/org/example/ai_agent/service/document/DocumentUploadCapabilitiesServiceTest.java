package org.example.ai_agent.service.document;

import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.example.ai_agent.dto.document.DocumentUploadCapabilitiesDto;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentUploadCapabilitiesServiceTest {

    @Test
    void shouldExposeTheSameBoundedLimitsUsedByTheServer() {
        DocumentUploadProperties properties = new DocumentUploadProperties();
        properties.setMaxFileSize(DataSize.ofMegabytes(99));
        properties.setMaxRequestSize(DataSize.ofMegabytes(99));
        properties.setMaxBatchFiles(99);
        DocumentUploadCapabilitiesService service = new DocumentUploadCapabilitiesService(
                properties, new String[]{"PDF", "docx", "pdf", " "});

        DocumentUploadCapabilitiesDto capabilities = service.current();

        assertEquals(DataSize.ofMegabytes(20).toBytes(), capabilities.maxFileBytes());
        assertEquals(DataSize.ofMegabytes(30).toBytes(), capabilities.maxRequestBytes());
        assertEquals(10, capabilities.maxBatchFiles());
        assertEquals(List.of("pdf", "docx"), capabilities.supportedExtensions());
    }
}
