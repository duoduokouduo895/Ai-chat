package org.example.ai_agent.service.document;

import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentUploadValidatorTest {

    private final DocumentUploadValidator validator = new DocumentUploadValidator(new DocumentUploadProperties());
    private final String[] supportedTypes = {"pdf", "docx", "doc", "txt", "md", "pptx", "xlsx"};

    @Test
    void shouldRejectFileWhenSignatureDoesNotMatchExtension() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.pdf", "application/pdf", "not a pdf".getBytes());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(file, supportedTypes));
    }

    @Test
    void shouldAcceptWellFormedDocxArchive() throws IOException {
        MockMultipartFile file = new MockMultipartFile("file", "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", officeArchive("word/document.xml"));

        assertDoesNotThrow(() -> validator.validate(file, supportedTypes));
    }

    @Test
    void shouldRejectPathLikeFileName() {
        MockMultipartFile file = new MockMultipartFile("file", "../contract.txt", "text/plain", "safe".getBytes());

        assertThrows(IllegalArgumentException.class, () -> validator.validate(file, supportedTypes));
    }

    @Test
    void shouldRejectOfficeArchiveWithTooManyEntries() throws IOException {
        DocumentUploadProperties properties = new DocumentUploadProperties();
        properties.setMaxArchiveEntries(10);
        DocumentUploadValidator strictValidator = new DocumentUploadValidator(properties);
        MockMultipartFile file = new MockMultipartFile("file", "contract.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", officeArchiveWithEntries(11));

        assertThrows(IllegalArgumentException.class, () -> strictValidator.validate(file, supportedTypes));
    }

    private byte[] officeArchive(String documentEntry) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(documentEntry));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
        }
        return output.toByteArray();
    }

    private byte[] officeArchiveWithEntries(int entryCount) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry("word/document.xml"));
            zip.write("<document/>".getBytes());
            zip.closeEntry();
            for (int index = 1; index < entryCount; index++) {
                zip.putNextEntry(new ZipEntry("word/extra-" + index + ".xml"));
                zip.write("<part/>".getBytes());
                zip.closeEntry();
            }
        }
        return output.toByteArray();
    }
}
