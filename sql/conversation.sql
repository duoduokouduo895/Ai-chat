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

 Date: 10/03/2026 21:03:01
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
  INDEX `idx_conversation_user_id`(`user_id` ASC) USING BTREE,
  INDEX `idx_conversation_user_created`(`user_id` ASC, `created_at` ASC) USING BTREE,
  INDEX `idx_conversation_user_updated`(`user_id` ASC, `updated_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 255 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of conversation
-- ----------------------------
INSERT INTO `conversation` VALUES (234, 'New Conversation', '2026-03-09 07:53:55', '2026-03-09 07:53:56', 21);
INSERT INTO `conversation` VALUES (235, 'New Conversation', '2026-03-09 07:54:39', '2026-03-09 07:54:42', 21);
INSERT INTO `conversation` VALUES (252, 'New Conversation', '2026-03-10 00:25:35', '2026-03-10 00:25:37', 20);

SET FOREIGN_KEY_CHECKS = 1;
