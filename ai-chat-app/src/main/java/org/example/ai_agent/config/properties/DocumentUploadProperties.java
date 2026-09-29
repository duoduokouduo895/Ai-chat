package org.example.ai_agent.config.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

/** 上传入口的硬资源边界。配置中心值会再被代码上限约束。 */
@Data
@Component
@ConfigurationProperties(prefix = "knowledge-base.upload")
public class DocumentUploadProperties {

    private DataSize maxFileSize = DataSize.ofMegabytes(10);
    private DataSize maxRequestSize = DataSize.ofMegabytes(15);
    private DataSize maxFormFieldSize = DataSize.ofKilobytes(8);
    private DataSize maxUncompressedSize = DataSize.ofMegabytes(50);
    private int maxBatchFiles = 5;
    private int maxArchiveEntries = 1_000;

    public long effectiveMaxFileBytes() {
        return bounded(maxFileSize, DataSize.ofMegabytes(1), DataSize.ofMegabytes(20));
    }

    public long effectiveMaxRequestBytes() {
        return bounded(maxRequestSize, DataSize.ofMegabytes(2), DataSize.ofMegabytes(30));
    }

    public int effectiveMaxBatchFiles() {
        return Math.max(1, Math.min(maxBatchFiles, 10));
    }

    public long effectiveMaxFormFieldBytes() {
        return bounded(maxFormFieldSize, DataSize.ofKilobytes(1), DataSize.ofKilobytes(32));
    }

    public long effectiveMaxUncompressedBytes() {
        return bounded(maxUncompressedSize, DataSize.ofMegabytes(5), DataSize.ofMegabytes(100));
    }

    public int effectiveMaxArchiveEntries() {
        return Math.max(10, Math.min(maxArchiveEntries, 2_000));
    }

    private long bounded(DataSize value, DataSize minimum, DataSize maximum) {
        long bytes = value == null ? minimum.toBytes() : value.toBytes();
        return Math.max(minimum.toBytes(), Math.min(bytes, maximum.toBytes()));
    }
}
