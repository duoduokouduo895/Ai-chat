package org.example.ai_agent.controller.support;

import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.http.codec.multipart.Part;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Mono;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Component
public class ReactiveMultipartFormReader {

    public Mono<String> readPartAsString(Part part) {
        return DataBufferUtils.join(part.content())
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

    public Mono<MultipartFile> toMultipartFile(FilePart filePart) {
        return DataBufferUtils.join(filePart.content())
                .map(dataBuffer -> {
                    try {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        return new FilePartMultipartFile(filePart, bytes);
                    } finally {
                        DataBufferUtils.release(dataBuffer);
                    }
                });
    }

    private static final class FilePartMultipartFile implements MultipartFile {
        private final FilePart filePart;
        private final byte[] bytes;

        private FilePartMultipartFile(FilePart filePart, byte[] bytes) {
            this.filePart = filePart;
            this.bytes = bytes;
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
            return bytes.length == 0;
        }

        @Override
        public long getSize() {
            return bytes.length;
        }

        @Override
        public byte[] getBytes() {
            return bytes;
        }

        @Override
        public InputStream getInputStream() {
            return new ByteArrayInputStream(bytes);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException, IllegalStateException {
            throw new UnsupportedOperationException("transferTo not supported");
        }
    }
}
