package org.example.ai_agent.service.document;

import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.example.ai_agent.dto.document.DocumentUploadCapabilitiesDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** 将动态上传配置收敛为前端可消费的只读能力声明。 */
@Service
public class DocumentUploadCapabilitiesService {

    private final DocumentUploadProperties uploadProperties;
    private final String[] supportedTypes;

    public DocumentUploadCapabilitiesService(
            DocumentUploadProperties uploadProperties,
            @Value("${knowledge-base.document.supported-types:pdf,docx,doc,txt,md,pptx,xlsx}") String[] supportedTypes) {
        this.uploadProperties = uploadProperties;
        this.supportedTypes = supportedTypes == null ? new String[0] : supportedTypes.clone();
    }

    public DocumentUploadCapabilitiesDto current() {
        List<String> extensions = Arrays.stream(supportedTypes)
                .filter(type -> type != null && !type.isBlank())
                .map(type -> type.trim().toLowerCase(Locale.ROOT))
                .distinct()
                .toList();
        return new DocumentUploadCapabilitiesDto(
                uploadProperties.effectiveMaxFileBytes(),
                uploadProperties.effectiveMaxRequestBytes(),
                uploadProperties.effectiveMaxBatchFiles(),
                extensions);
    }
}
