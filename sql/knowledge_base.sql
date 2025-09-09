-- 知识库表
CREATE TABLE `knowledge_bases` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '知识库ID',
  `name` varchar(100) NOT NULL COMMENT '知识库名称',
  `description` text COMMENT '知识库描述',
  `creator_id` bigint NOT NULL COMMENT '创建者用户ID',
  `is_public` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否公开(0:私有, 1:公开)',
  `status` varchar(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '知识库状态(ACTIVE:活跃, ARCHIVED:归档, DELETED:已删除)',
  `document_count` int NOT NULL DEFAULT '0' COMMENT '文档数量',
  `vector_dimension` int COMMENT '向量维度',
  `embedding_model` varchar(50) COMMENT '使用的向量化模型',
  `index_name` varchar(100) COMMENT 'Elasticsearch索引名称',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_creator_id` (`creator_id`),
  KEY `idx_status` (`status`),
  KEY `idx_is_public` (`is_public`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库表';

-- 知识库文档表
CREATE TABLE `knowledge_documents` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `title` varchar(200) NOT NULL COMMENT '文档标题',
  `file_name` varchar(255) NOT NULL COMMENT '原始文件名',
  `file_type` varchar(20) NOT NULL COMMENT '文件类型',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `oss_path` varchar(500) NOT NULL COMMENT 'OSS存储路径',
  `oss_url` varchar(500) NOT NULL COMMENT 'OSS访问URL',
  `parse_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '解析状态(PENDING:待解析, PARSING:解析中, PARSED:已解析, FAILED:解析失败)',
  `content` longtext COMMENT '解析后的文本内容',
  `character_count` int DEFAULT '0' COMMENT '文档字符数',
  `chunk_count` int DEFAULT '0' COMMENT '文档分块数量',
  `is_embedded` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已向量化',
  `embedding_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态(PENDING:待向量化, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)',
  `metadata` json COMMENT '文档元数据(JSON格式)',
  `uploader_id` bigint NOT NULL COMMENT '上传者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_parse_status` (`parse_status`),
  KEY `idx_embedding_status` (`embedding_status`),
  KEY `idx_created_at` (`created_at`),
  CONSTRAINT `fk_documents_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档表';

-- 文档块表
CREATE TABLE `knowledge_document_chunks` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档块ID',
  `document_id` bigint NOT NULL COMMENT '所属文档ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `chunk_index` int NOT NULL COMMENT '文档块序号',
  `content` text NOT NULL COMMENT '文档块内容',
  `character_count` int NOT NULL DEFAULT '0' COMMENT '内容字符数',
  `metadata` json COMMENT '文档块元数据(JSON格式)',
  `embedding_id` varchar(100) COMMENT '向量ID(在Elasticsearch中的ID)',
  `is_embedded` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否已向量化',
  `embedding_status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态(PENDING:待处理, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)',
  `embedding_error` text COMMENT '向量化错误信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_chunk_index` (`chunk_index`),
  KEY `idx_embedding_status` (`embedding_status`),
  KEY `idx_embedding_id` (`embedding_id`),
  CONSTRAINT `fk_chunks_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_chunks_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档块表';

-- 添加一些索引优化查询性能
CREATE INDEX `idx_documents_type_status` ON `knowledge_documents` (`file_type`, `parse_status`);
CREATE INDEX `idx_chunks_embedded` ON `knowledge_document_chunks` (`is_embedded`, `embedding_status`);
CREATE INDEX `idx_knowledge_bases_public_status` ON `knowledge_bases` (`is_public`, `status`);

-- 文档处理记录表
CREATE TABLE `document_processing_records` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `knowledge_base_id` bigint COMMENT '知识库ID',
  `processing_type` varchar(20) NOT NULL COMMENT '处理类型(PARSE:解析, CHUNK:分块, EMBED:向量化)',
  `status` varchar(20) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态(PENDING:待处理, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)',
  `start_time` datetime COMMENT '开始时间',
  `end_time` datetime COMMENT '结束时间',
  `processing_time_ms` bigint COMMENT '处理耗时(毫秒)',
  `retry_count` int NOT NULL DEFAULT '0' COMMENT '重试次数',
  `error_message` text COMMENT '错误信息',
  `error_stack` longtext COMMENT '错误堆栈',
  `processing_params` json COMMENT '处理参数(JSON格式)',
  `processing_result` text COMMENT '处理结果',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_document_id` (`document_id`),
  KEY `idx_knowledge_base_id` (`knowledge_base_id`),
  KEY `idx_processing_type` (`processing_type`),
  KEY `idx_status` (`status`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_document_type` (`document_id`, `processing_type`),
  CONSTRAINT `fk_processing_records_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE,
  CONSTRAINT `fk_processing_records_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档处理记录表';