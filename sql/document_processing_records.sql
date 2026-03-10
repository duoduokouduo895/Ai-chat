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

 Date: 10/03/2026 21:03:06
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for document_processing_records
-- ----------------------------
DROP TABLE IF EXISTS `document_processing_records`;
CREATE TABLE `document_processing_records`  (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '记录ID',
  `document_id` bigint NOT NULL COMMENT '文档ID',
  `knowledge_base_id` bigint NULL DEFAULT NULL COMMENT '知识库ID',
  `processing_type` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '处理类型(PARSE:解析, CHUNK:分块, EMBED:向量化)',
  `status` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '处理状态(PENDING:待处理, PROCESSING:处理中, COMPLETED:已完成, FAILED:失败)',
  `start_time` datetime NULL DEFAULT NULL COMMENT '开始时间',
  `end_time` datetime NULL DEFAULT NULL COMMENT '结束时间',
  `processing_time_ms` bigint NULL DEFAULT NULL COMMENT '处理耗时(毫秒)',
  `retry_count` int NOT NULL DEFAULT 0 COMMENT '重试次数',
  `error_message` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误信息',
  `error_stack` longtext CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '错误堆栈',
  `processing_params` json NULL COMMENT '处理参数(JSON格式)',
  `processing_result` text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL COMMENT '处理结果',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_document_id`(`document_id` ASC) USING BTREE,
  INDEX `idx_knowledge_base_id`(`knowledge_base_id` ASC) USING BTREE,
  INDEX `idx_processing_type`(`processing_type` ASC) USING BTREE,
  INDEX `idx_status`(`status` ASC) USING BTREE,
  INDEX `idx_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_document_type`(`document_id` ASC, `processing_type` ASC) USING BTREE,
  CONSTRAINT `fk_processing_records_document` FOREIGN KEY (`document_id`) REFERENCES `knowledge_documents` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT,
  CONSTRAINT `fk_processing_records_knowledge_base` FOREIGN KEY (`knowledge_base_id`) REFERENCES `knowledge_bases` (`id`) ON DELETE CASCADE ON UPDATE RESTRICT
) ENGINE = InnoDB AUTO_INCREMENT = 40 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '文档处理记录表' ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of document_processing_records
-- ----------------------------
INSERT INTO `document_processing_records` VALUES (38, 17, NULL, 'PARSE', 'COMPLETED', '2026-02-24 12:27:33', '2026-02-24 12:27:34', 607, 0, NULL, NULL, NULL, 'Content length: 19', '2026-02-24 12:27:33', '2026-02-24 12:27:34');
INSERT INTO `document_processing_records` VALUES (39, 17, NULL, 'CHUNK', 'COMPLETED', '2026-02-24 12:27:34', '2026-02-24 12:27:34', 48, 0, NULL, NULL, NULL, 'Chunks created: 1', '2026-02-24 12:27:34', '2026-02-24 12:27:34');

SET FOREIGN_KEY_CHECKS = 1;
