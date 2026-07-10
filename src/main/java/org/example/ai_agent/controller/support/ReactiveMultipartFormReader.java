package org.example.ai_agent.controller.support;

import org.example.ai_agent.config.properties.DocumentUploadProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class ReactiveMultipartFormReader {

    private final DocumentUploadProperties uploadProperties;

    public ReactiveMultipartFormReader() {
        this(new DocumentUploadProperties());
    }

    @Autowired
    public ReactiveMultipartFormReader(DocumentUploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    public Mono<String> readPartAsString(Part part) {
        return DataBufferUtils.join(part.content(), (int) uploadProperties.effectiveMaxFormFieldBytes())
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return new String(bytes, StandardCharsets.UTF_8);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    public Mono<String> getFormValue(MultiValueMap<String, Part> multipartData, String key) {
        Part part = multipartData.getFirst(key);
        if (part == null) {
            return Mono.empty();
        }
        return readPartAsString(part);
    }

    public Mono<String> getRequiredFormValue(MultiValueMap<String, Part> multipartData,
                                             String key,
                                             String errorMessage) {
        return getFormValue(multipartData, key)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(errorMessage)));
    }

    public Mono<String> getFormValueOrDefault(MultiValueMap<String, Part> multipartData,
                                              String key,
                                              String defaultValue) {
        return getFormValue(multipartData, key)
                .defaultIfEmpty(defaultValue);
    }

    public Mono<FilePart> getRequiredFile(MultiValueMap<String, Part> multipartData, String key) {
        Part part = multipartData.getFirst(key);
        if (part instanceof FilePart filePart) {
            return Mono.just(filePart);
        }
        return Mono.error(new IllegalArgumentException("文件不能为空"));
    }

    public Mono<TemporaryMultipartFile> stageToTemporaryFile(FilePart filePart) {
        return Mono.fromCallable(() -> Files.createTempFile("ai-chat-upload-", ".part"))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(path -> filePart.transferTo(path)
                        .then(Mono.fromCallable(() -> new TemporaryMultipartFile(filePart, path)))
                        .onErrorResume(error -> deleteTemporaryFile(path).then(Mono.error(error))));
    }

    private Mono<Void> deleteTemporaryFile(Path path) {
        return Mono.<Void>fromRunnable(() -> {
            try {
                Files.deleteIfExists(path);
            } catch (IOException ignored) {
                // The operating system will eventually clean the temporary directory.
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    public static final class TemporaryMultipartFile implements MultipartFile, AutoCloseable {
        private final FilePart filePart;
        private final Path path;
        private final long size;

        private TemporaryMultipartFile(FilePart filePart, Path path) throws IOException {
            this.filePart = filePart;
            this.path = path;
            this.size = Files.size(path);
        }

        @Override
        public String getName() {
            return filePart.name();
        }

        @Override
        public String getOriginalFilename() {
            return filePart.filename();
        }

        @Override
        public String getContentType() {
            return filePart.headers().getContentType() != null
                    ? filePart.headers().getContentType().toString()
                    : "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return size == 0;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public byte[] getBytes() {
            throw new UnsupportedOperationException("临时上传文件不支持整体读取");
        }

        @Override
        public InputStream getInputStream() {
            try {
                return Files.newInputStream(path);
            } catch (IOException error) {
                throw new IllegalStateException("无法读取临时上传文件", error);
            }
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            Files.copy(path, dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public void close() throws IOException {
            Files.deleteIfExists(path);
        }
    }
}
