package org.example.ai_agent.service.impl.infrastructure;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import com.aliyun.oss.model.OSSObject;
import lombok.extern.slf4j.Slf4j;
import org.example.ai_agent.config.properties.OssProperties;
import org.example.ai_agent.service.OssService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 阿里云OSS服务实现类
 * 
 * @author AI Agent
 * @version 1.0
 * @since 2025
 */
@Slf4j
@Service
public class OssServiceImpl implements OssService {
    
    @Autowired
    private OssProperties ossProperties;
    
    private OSS ossClient;
    
    @PostConstruct
    public void init() {
        ossClient = new OSSClientBuilder().build(
                ossProperties.getEndpoint(),
                ossProperties.getAccessKeyId(),
                ossProperties.getAccessKeySecret()
        );
        log.info("阿里云OSS客户端初始化完成: endpoint={}, bucket={}", 
                ossProperties.getEndpoint(), ossProperties.getBucketName());
    }
    
    @PreDestroy
    public void destroy() {
        if (ossClient != null) {
            ossClient.shutdown();
            log.info("阿里云OSS客户端已关闭");
        }
    }
    
    @Override
    public Mono<String> uploadFile(String fileName, InputStream inputStream, Long fileSize, String contentType) {
        return Mono.fromCallable(() -> {
            try {
                String objectKey = generateObjectKey(fileName);
                
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(fileSize);
                metadata.setContentType(contentType);
                metadata.setContentDisposition("inline;filename=" + fileName);
                
                ossClient.putObject(ossProperties.getBucketName(), objectKey, inputStream, metadata);
                
                log.info("文件上传成功: size={}", fileSize);
                return objectKey;
                
            } catch (Exception e) {
                log.error("文件上传失败: errorType={}", e.getClass().getSimpleName());
                throw new RuntimeException("文件上传失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<OssFileInfo> uploadKnowledgeDocument(Long knowledgeBaseId, String fileName, 
                                                   InputStream inputStream, Long fileSize, String contentType) {
        return Mono.fromCallable(() -> {
            try {
                String objectKey = generateKnowledgeDocumentKey(knowledgeBaseId, fileName);
                
                ObjectMetadata metadata = new ObjectMetadata();
                metadata.setContentLength(fileSize);
                metadata.setContentType(contentType);
                metadata.setContentDisposition("inline;filename=" + fileName);
                // 添加自定义元数据
                metadata.addUserMetadata("knowledge-base-id", knowledgeBaseId.toString());
                metadata.addUserMetadata("upload-time", LocalDateTime.now().toString());
                
                ossClient.putObject(ossProperties.getBucketName(), objectKey, inputStream, metadata);
                
                String url = getFileUrl(objectKey);
                
                log.info("知识库文档上传成功: knowledgeBaseId={}, size={}", knowledgeBaseId, fileSize);
                
                return new OssFileInfo(objectKey, url, fileSize, contentType);
                
            } catch (Exception e) {
                log.error("知识库文档上传失败: knowledgeBaseId={}, errorType={}",
                         knowledgeBaseId, e.getClass().getSimpleName());
                throw new RuntimeException("知识库文档上传失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public String getFileUrl(String ossPath) {
        return ossProperties.getUrlPrefix() + "/" + ossPath;
    }
    
    @Override
    public Mono<Boolean> deleteFile(String ossPath) {
        return Mono.fromCallable(() -> {
            try {
                ossClient.deleteObject(ossProperties.getBucketName(), ossPath);
                log.info("文件删除成功");
                return true;
            } catch (Exception e) {
                log.error("文件删除失败: errorType={}", e.getClass().getSimpleName());
                return false;
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    @Override
    public Mono<InputStream> getFileInputStream(String ossPath) {
        return Mono.fromCallable(() -> {
            try {
                OSSObject ossObject = ossClient.getObject(ossProperties.getBucketName(), ossPath);
                log.debug("获取文件输入流成功");
                return ossObject.getObjectContent();
            } catch (Exception e) {
                log.error("获取文件输入流失败: errorType={}", e.getClass().getSimpleName());
                throw new RuntimeException("获取文件失败: " + e.getMessage(), e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }
    
    /**
     * 生成通用文件的ObjectKey
     */
    private String generateObjectKey(String fileName) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExtension = getFileExtension(fileName);
        return String.format("files/%s/%s%s", dateStr, uuid, fileExtension);
    }
    
    /**
     * 生成知识库文档的ObjectKey
     */
    private String generateKnowledgeDocumentKey(Long knowledgeBaseId, String fileName) {
        String dateStr = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy/MM/dd"));
        String uuid = UUID.randomUUID().toString().replace("-", "");
        String fileExtension = getFileExtension(fileName);
        return String.format("%skb_%d/%s/%s%s", 
                ossProperties.getKnowledgeBasePrefix(), knowledgeBaseId, dateStr, uuid, fileExtension);
    }
    
    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || !fileName.contains(".")) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf("."));
    }
}
