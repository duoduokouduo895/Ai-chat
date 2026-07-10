package org.example.ai_agent.controller.support;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.nio.charset.StandardCharsets;

import static org.mockito.Mockito.when;

class ReactiveMultipartFormReaderTest {

    private final ReactiveMultipartFormReader reader = new ReactiveMultipartFormReader();

    @Test
    void getFormValue_shouldReadStringPart() {
        LinkedMultiValueMap<String, Part> multipartData = new LinkedMultiValueMap<>();
        multipartData.add("parseMode", textPart("markdown"));

        StepVerifier.create(reader.getFormValue(multipartData, "parseMode"))
                .expectNext("markdown")
                .verifyComplete();
    }

    @Test
    void getFormValueOrDefault_shouldUseDefaultWhenMissing() {
        LinkedMultiValueMap<String, Part> multipartData = new LinkedMultiValueMap<>();

        StepVerifier.create(reader.getFormValueOrDefault(multipartData, "parseMode", "auto"))
                .expectNext("auto")
                .verifyComplete();
    }

    @Test
    void toMultipartFile_shouldConvertFilePart() {
        FilePart filePart = filePart("notes.md", "hello knowledge");

        StepVerifier.create(reader.toMultipartFile(filePart))
                .assertNext(file -> {
                    org.junit.jupiter.api.Assertions.assertEquals("file", file.getName());
                    org.junit.jupiter.api.Assertions.assertEquals("notes.md", file.getOriginalFilename());
                    org.junit.jupiter.api.Assertions.assertEquals("text/markdown", file.getContentType());
                    org.junit.jupiter.api.Assertions.assertEquals(15L, file.getSize());
                    org.junit.jupiter.api.Assertions.assertFalse(file.isEmpty());
                    org.junit.jupiter.api.Assertions.assertArrayEquals(
                            "hello knowledge".getBytes(StandardCharsets.UTF_8),
                            readBytes(file));
                })
                .verifyComplete();
    }

    @Test
    void toMultipartFile_shouldKeepEmptyFileShape() {
        FilePart filePart = filePart("empty.txt", "");

        StepVerifier.create(reader.toMultipartFile(filePart))
                .assertNext(file -> {
                    org.junit.jupiter.api.Assertions.assertEquals("empty.txt", file.getOriginalFilename());
                    org.junit.jupiter.api.Assertions.assertEquals(0L, file.getSize());
                    org.junit.jupiter.api.Assertions.assertTrue(file.isEmpty());
                })
                .verifyComplete();
    }

    private Part textPart(String value) {
        Part part = org.mockito.Mockito.mock(Part.class);
        when(part.content()).thenReturn(Flux.just(new DefaultDataBufferFactory()
                .wrap(value.getBytes(StandardCharsets.UTF_8))));
        return part;
    }

    private FilePart filePart(String filename, String content) {
        FilePart filePart = org.mockito.Mockito.mock(FilePart.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/markdown"));
        when(filePart.name()).thenReturn("file");
        when(filePart.filename()).thenReturn(filename);
        when(filePart.headers()).thenReturn(headers);
        when(filePart.content()).thenReturn(Flux.just(new DefaultDataBufferFactory()
                .wrap(content.getBytes(StandardCharsets.UTF_8))));
        return filePart;
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }
}
