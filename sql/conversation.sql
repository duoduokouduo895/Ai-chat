/*
 Navicat Premium Data Transfer

 Source Server         : 华为
 Source Server Type    : MySQL
 Source Server Version : 80030 (8.0.30)
 Source Host           : 111.230.243.93:3306
 Source Schema         : ai_agent_db

 Target Server Type    : MySQL
 Target Server Version : 80030 (8.0.30)
 File Encoding         : 65001

 Date: 18/08/2025 03:27:04
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for conversation
-- ----------------------------
DROP TABLE IF EXISTS `conversation`;
CREATE TABLE `conversation`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `title` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `user_id` bigint NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_conversation_updated_at`(`updated_at` DESC) USING BTREE,
  INDEX `idx_conversation_created_at`(`created_at` DESC) USING BTREE,
  INDEX `idx_conversation_user_id`(`user_id` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 24 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of conversation
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
