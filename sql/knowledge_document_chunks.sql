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

 Date: 10/03/2026 21:03:17
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for knowledge_document_chunks
-- ----------------------------
DROP TABLE IF EXISTS `knowledge_document_chunks`;
CREATE TABLE `knowledge_document_chunks`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档块ID',
  `document_id` bigint NOT NULL COMMENT '所属文档ID',
  `knowledge_base_id` bigint NOT NULL COMMENT '所属知识库ID',
  `chunk_index` int NOT NULL COMMENT '文档块序号',
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档块内容',
  `character_count` int NOT NULL DEFAULT 0 COMMENT '内容字符数',
  `metadata` json NULL COMMENT '文档块元数据(JSON格式)',
  `embedding_id` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT NULL COMMENT '向量ID(在Elasticsearch中的ID)',
  `is_embedded` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否已向量化',
  `embedding_status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '向量化状态(PENDING:待处理, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)',
  `embedding_error` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '向量化错误信息',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_document_id`(`document_id` ASC) USING BTREE,
  INDEX `idx_knowledge_base_id`(`knowledge_base_id` ASC) USING BTREE,
  INDEX `idx_chunk_index`(`chunk_index` ASC) USING BTREE,
  INDEX `idx_embedding_status`(`embedding_status` ASC) USING BTREE,
  INDEX `idx_embedding_id`(`embedding_id` ASC) USING BTREE,
  INDEX `idx_chunks_embedded`(`is_embedded` ASC, `embedding_status` ASC) USING BTREE,
  CONSTRAINT `fk_chunks_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_chunks_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 45 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文档块表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of knowledge_document_chunks
-- ----------------------------
INSERT INTO `knowledge_document_chunks` VALUES (44, 17, 4, 0, '小猪乔智的秘密武器是：蓝色的泡泡长枪', 18, '{\"index\": \"0\"}', 'b56d349f-e18e-4f3f-81bf-9d09b9ed21ba', 1, 'COMPLETED', NULL, '2026-02-24 12:27:34', '2026-02-24 12:27:35');

SET FOREIGN_KEY_CHECKS = 1;
