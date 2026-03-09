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

 Date: 09/03/2026 22:13:50
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
) ENGINE = InnoDB AUTO_INCREMENT = 252 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of conversation
-- ----------------------------
INSERT INTO `conversation` VALUES (234, 'New Conversation', '2026-03-09 07:53:55', '2026-03-09 07:53:56', 21);
INSERT INTO `conversation` VALUES (235, 'New Conversation', '2026-03-09 07:54:39', '2026-03-09 07:54:42', 21);
INSERT INTO `conversation` VALUES (236, 'New Conversation', '2026-03-09 07:59:03', '2026-03-09 07:59:39', 22);
INSERT INTO `conversation` VALUES (237, 'New Conversation', '2026-03-09 08:09:15', '2026-03-09 08:09:59', 23);
INSERT INTO `conversation` VALUES (238, 'New Conversation', '2026-03-09 08:11:16', '2026-03-09 08:11:21', 24);
INSERT INTO `conversation` VALUES (240, 'New Conversation', '2026-03-09 08:17:43', '2026-03-09 08:17:49', 25);
INSERT INTO `conversation` VALUES (241, 'New Conversation', '2026-03-09 08:26:44', '2026-03-09 08:26:50', 28);
INSERT INTO `conversation` VALUES (242, 'New Conversation', '2026-03-09 08:36:44', '2026-03-09 08:36:46', 20);
INSERT INTO `conversation` VALUES (243, 'New Conversation', '2026-03-09 21:00:24', '2026-03-09 21:00:25', 30);
INSERT INTO `conversation` VALUES (244, 'New Conversation', '2026-03-09 21:03:02', '2026-03-09 21:03:27', 31);
INSERT INTO `conversation` VALUES (245, 'New Conversation', '2026-03-09 21:03:41', '2026-03-09 21:03:44', 32);
INSERT INTO `conversation` VALUES (247, 'New Conversation', '2026-03-09 21:43:32', '2026-03-09 21:43:36', 20);
INSERT INTO `conversation` VALUES (248, 'New Conversation', '2026-03-09 21:50:15', '2026-03-09 21:50:20', 33);
INSERT INTO `conversation` VALUES (250, 'New Conversation', '2026-03-09 21:59:08', '2026-03-09 21:59:34', 36);
INSERT INTO `conversation` VALUES (251, 'New Conversation', '2026-03-09 21:59:44', '2026-03-09 22:00:03', 37);

SET FOREIGN_KEY_CHECKS = 1;
