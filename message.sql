/*
 Navicat Premium Data Transfer

 Source Server         : 华为
 Source Server Type    : MySQL
 Source Server Version : 80030 (8.0.30)
 Source Host           : 113.45.11.64:3306
 Source Schema         : ai_agent_db

 Target Server Type    : MySQL
 Target Server Version : 80030 (8.0.30)
 File Encoding         : 65001

 Date: 18/08/2025 03:27:14
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for message
-- ----------------------------
DROP TABLE IF EXISTS `message`;
CREATE TABLE `message`  (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `sender` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `timestamp` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `model` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_message_conversation_id`(`conversation_id` ASC) USING BTREE,
  INDEX `idx_message_timestamp`(`timestamp` DESC) USING BTREE,
  INDEX `idx_message_sender`(`sender` ASC) USING BTREE,
  INDEX `idx_message_model`(`model` ASC) USING BTREE,
  INDEX `idx_message_conversation_timestamp`(`conversation_id` ASC, `timestamp` DESC) USING BTREE,
  INDEX `idx_message_conversation_sender`(`conversation_id` ASC, `sender` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 451 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of message
-- ----------------------------

SET FOREIGN_KEY_CHECKS = 1;
