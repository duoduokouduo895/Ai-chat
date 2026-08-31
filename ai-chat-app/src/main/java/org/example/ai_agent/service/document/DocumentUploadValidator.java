package org.example.ai_agent.service.document;

import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** 在写入 OSS 前验证文件名、大小、签名与 OOXML 解压边界。 */
@Component
public class DocumentUploadValidator {

    private static final Set<String> OOXML_TYPES = Set.of("docx", "pptx", "xlsx");
    private final DocumentUploadProperties properties;

    public DocumentUploadValidator(DocumentUploadProperties properties) {
        this.properties = properties;
    }

    public void validate(MultipartFile file, String[] supportedTypes) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("文件不能为空");
        }
        if (file.getSize() > properties.effectiveMaxFileBytes()) {
            throw new IllegalArgumentException("文件大小不能超过" + properties.effectiveMaxFileBytes() / (1024 * 1024) + "MB");
        }

        String fileName = normalizedFileName(file.getOriginalFilename());
        String extension = extensionOf(fileName);
        if (!isSupported(extension, supportedTypes)) {
            throw new IllegalArgumentException("不支持的文件类型: " + extension);
        }

        try {
            validateSignature(file, extension);
            if (OOXML_TYPES.contains(extension)) {
                validateOfficeArchive(file, extension);
            }
        } catch (IOException error) {
            throw new IllegalArgumentException("无法校验上传文件", error);
        }
    }

    public void validateBatch(MultipartFile[] files) {
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("请至少选择一个文件");
        }
        if (files.length > properties.effectiveMaxBatchFiles()) {
            throw new IllegalArgumentException("一次最多上传" + properties.effectiveMaxBatchFiles() + "个文件");
        }
    }

    private String normalizedFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }
        String normalized = Normalizer.normalize(fileName, Normalizer.Form.NFC).trim();
        if (normalized.length() > 255 || normalized.contains("/") || normalized.contains("\\")
                || normalized.contains("..") || normalized.chars().anyMatch(Character::isISOControl)) {
            throw new IllegalArgumentException("文件名不合法");
        }
        return normalized;
    }

    private String extensionOf(String fileName) {
        int separator = fileName.lastIndexOf('.');
        if (separator <= 0 || separator == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(separator + 1).toLowerCase(Locale.ROOT);
    }

    private boolean isSupported(String extension, String[] supportedTypes) {
        return Arrays.stream(supportedTypes == null ? new String[0] : supportedTypes)
                .map(value -> value.toLowerCase(Locale.ROOT).trim())
                .anyMatch(extension::equals);
    }

    private void validateSignature(MultipartFile file, String extension) throws IOException {
        byte[] header;
        try (InputStream input = new BufferedInputStream(file.getInputStream())) {
            header = input.readNBytes(8);
        }

        boolean valid = switch (extension) {
            case "pdf" -> startsWith(header, "%PDF-".getBytes(StandardCharsets.US_ASCII));
            case "doc" -> startsWith(header, new byte[]{(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0,
                    (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1});
            case "docx", "pptx", "xlsx" -> isZipHeader(header);
            case "txt", "md" -> isTextLike(header);
            default -> false;
        };
        if (!valid) {
            throw new IllegalArgumentException("文件内容与扩展名不匹配");
        }
    }

    private void validateOfficeArchive(MultipartFile file, String extension) throws IOException {
        int entryCount = 0;
        long uncompressedBytes = 0;
        boolean expectedDocumentPart = false;
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(file.getInputStream()))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > properties.effectiveMaxArchiveEntries()) {
                    throw new IllegalArgumentException("Office 文件条目过多");
                }
                expectedDocumentPart |= expectedEntry(extension, entry.getName());
                int read;
                while ((read = zip.read(buffer)) != -1) {
                    uncompressedBytes += read;
                    if (uncompressedBytes > properties.effectiveMaxUncompressedBytes()) {
                        throw new IllegalArgumentException("Office 文件解压后过大");
                    }
                }
            }
        }
        if (!expectedDocumentPart) {
            throw new IllegalArgumentException("Office 文件结构不合法");
        }
    }

    private boolean expectedEntry(String extension, String entryName) {
        return switch (extension) {
            case "docx" -> "word/document.xml".equals(entryName);
            case "pptx" -> "ppt/presentation.xml".equals(entryName);
            case "xlsx" -> "xl/workbook.xml".equals(entryName);
            default -> false;
        };
    }

    private boolean isZipHeader(byte[] header) {
        return header.length >= 4 && header[0] == 'P' && header[1] == 'K'
                && ((header[2] == 3 && header[3] == 4) || (header[2] == 5 && header[3] == 6));
    }

    private boolean isTextLike(byte[] header) {
        for (byte value : header) {
            if (value == 0) {
                return false;
            }
        }
        return true;
    }

    private boolean startsWith(byte[] source, byte[] prefix) {
        if (source.length < prefix.length) {
            return false;
        }
        for (int index = 0; index < prefix.length; index++) {
            if (source[index] != prefix[index]) {
                return false;
            }
        }
        return true;
    }
}
