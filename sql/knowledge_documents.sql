/*
 Navicat Premium Data Transfer

 Source Server         : 腾讯云2
 Source Server Type    : MySQL
 Source Server Version : 80030 (8.0.30)
 Source Host           : 111.230.243.93:3306
 Source Schema         : ai_agent_db

 Target Server Type    : MySQL
 Target Server Version : 80030 (8.0.30)
 File Encoding         : 65001

 Date: 10/03/2026 21:03:22
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for knowledge_documents
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_documents`;
CREATE TABLE `knowledge_documents`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `title` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `file_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名',
  `file_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件类型',
  `file_size` bigint NOT NULL COMMENT '文件大小(字节)',
  `oss_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OSS存储路径',
  `oss_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'OSS访问URL',
  `parse_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '解析状态(PENDING:待解析, PARSING:解析中, PARSED:已解析, FAILED:解析失败)',
  `content` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '解析后的文本内容',
  `character_count` int NULL DEFAULT 0 COMMENT '文档字符数',
  `chunk_count` int NULL DEFAULT 0 COMMENT '文档分块数量',
  `is_embedded` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已向量化',
  `embedding_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态(PENDING:待向量化, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)',
  `metadata` json NULL COMMENT '文档元数据(JSON格式)',
  `uploader_id` bigint NOT NULL COMMENT '上传者用户ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_knowledge_base_id`(`knowledge_base_id` ASC) USING BTREE,
  INDEX `idx_uploader_id`(`uploader_id` ASC) USING BTREE,
  INDEX `idx_parse_status`(`parse_status` ASC) USING BTREE,
  INDEX `idx_embedding_status`(`embedding_status` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_documents_type_status`(`file_type` ASC, `parse_status` ASC) USING BTREE,
  CONSTRAINT `fk_documents_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 18 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '知识库文档表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of knowledge_documents
-- ----------------------------
INSERT INTO `knowledge_documents` VALUES (17, 4, '知识库测试.txt', '知识库测试.txt', 'txt', 54, 'knowledge-base/kb_4/2026/02/24/2589fd81a44f4019b8b811033f3b359e.txt', 'https://cc-ceshi-1.oss-cn-guangzhou.aliyuncs.com/knowledge-base/kb_4/2026/02/24/2589fd81a44f4019b8b811033f3b359e.txt', 'COMPLETED', '小猪乔智的秘密武器是：蓝色的泡泡长枪\n', 19, 1, 1, 'COMPLETED', NULL, 1, '2026-02-24 12:27:33', '2026-02-24 12:27:35');

SET FOREIGN_KEY_CHECKS = 1;
