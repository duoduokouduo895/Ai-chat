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

 Date: 09/03/2026 22:14:20
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
  INDEX `idx_message_conversation_sender`(`conversation_id` ASC, `sender` ASC) USING BTREE,
  INDEX `idx_message_conversation_timestamp_asc`(`conversation_id` ASC, `timestamp` ASC) USING BTREE,
  INDEX `idx_message_sender_timestamp`(`sender` ASC, `timestamp` ASC) USING BTREE,
  INDEX `idx_message_model_timestamp`(`model` ASC, `timestamp` ASC) USING BTREE,
  INDEX `idx_message_conv_sender_time`(`conversation_id` ASC, `sender` ASC, `timestamp` ASC) USING BTREE,
  FULLTEXT INDEX `idx_message_content_fulltext`(`content`)
) ENGINE = InnoDB AUTO_INCREMENT = 1434 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of message
-- ----------------------------
INSERT INTO `message` VALUES (1374, 234, '早上好~', 'user', '2026-03-09 07:53:55', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1375, 234, '早上好！☀️ 希望你今天有个美好的开始，充满活力和好心情！有什么我可以帮你的吗？', 'ai', '2026-03-09 07:53:56', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1376, 235, '早上好~', 'user', '2026-03-09 07:54:40', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1377, 235, '早上好！☀️ 希望你今天有个美好的开始，充满活力和好心情！有什么我可以帮你的吗？', 'ai', '2026-03-09 07:54:42', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1378, 236, '早上好~', 'user', '2026-03-09 07:59:03', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1379, 236, '早上好！☀️ 希望你今天有个美好的开始，心情愉快，充满活力！有什么我可以帮你的吗？', 'ai', '2026-03-09 07:59:05', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1380, 236, '好~', 'user', '2026-03-09 07:59:33', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1381, 236, '好的！😊\n\n如果你有任何问题、想法，或者只是想聊聊天，随时告诉我哦～ 我在这里陪你～', 'ai', '2026-03-09 07:59:35', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1382, 236, '好哒~', 'user', '2026-03-09 07:59:37', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1383, 236, '好哒~ 😊  \n今天有什么想聊的，或者需要我帮忙的吗？无论是学习、工作、生活小技巧，还是想放松一下听个笑话、讲个故事，都可以告诉我哦～', 'ai', '2026-03-09 07:59:39', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1384, 237, '早上好~', 'user', '2026-03-09 08:09:15', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1385, 237, '早上好！☀️ 希望你今天有个美好的开始，心情愉快，充满活力！有什么我可以帮你的吗？', 'ai', '2026-03-09 08:09:17', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1386, 237, '好', 'user', '2026-03-09 08:09:55', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1387, 237, '好的！😊\n\n如果你有任何问题、需要帮助，或者只是想聊聊天，随时告诉我哦！今天有什么计划吗？', 'ai', '2026-03-09 08:09:56', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1388, 237, '好', 'user', '2026-03-09 08:09:57', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1389, 237, '好的！😊  \n如果你有任何问题、需要帮助，或者想聊点什么，随时告诉我～  \n今天有什么特别想做的事情吗？', 'ai', '2026-03-09 08:09:59', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1390, 238, '好', 'user', '2026-03-09 08:11:16', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1391, 238, '你好！很高兴能和你交流。请问有什么可以帮助你的吗？😊', 'ai', '2026-03-09 08:11:17', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1392, 238, '好', 'user', '2026-03-09 08:11:18', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1393, 238, '你好！很高兴能和你交流。请问有什么可以帮助你的吗？😊', 'ai', '2026-03-09 08:11:19', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1394, 238, '好', 'user', '2026-03-09 08:11:20', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1395, 238, '你好！很高兴能和你交流。请问有什么可以帮助你的吗？😊', 'ai', '2026-03-09 08:11:21', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1398, 240, '好', 'user', '2026-03-09 08:17:43', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1399, 240, '你好！很高兴能和你交流。\n\n有什么我可以帮助你的吗？无论是回答问题、提供信息，还是只是想聊聊天，我都在这里。😊', 'ai', '2026-03-09 08:17:45', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1400, 240, '好', 'user', '2026-03-09 08:17:45', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1401, 240, '好的！😊\n\n有什么我可以帮你的吗？随时告诉我哦～', 'ai', '2026-03-09 08:17:47', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1402, 240, '好', 'user', '2026-03-09 08:17:47', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1403, 240, '你好！很高兴见到你 😊\n\n有什么我可以帮你的吗？无论是回答问题、提供信息，还是只是想聊聊天，我都在这里。', 'ai', '2026-03-09 08:17:49', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1404, 241, '早上好', 'user', '2026-03-09 08:26:44', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1405, 241, '早上好！☀️ 祝你今天有个愉快的一天！有什么我可以帮你的吗？', 'ai', '2026-03-09 08:26:46', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1406, 241, '好', 'user', '2026-03-09 08:26:47', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1407, 241, '好的！很高兴能帮到你 😊\n\n如果你有任何问题、想聊聊天，或者需要我帮忙查资料、写点东西、分析问题，随时告诉我！今天有什么特别想做的吗？', 'ai', '2026-03-09 08:26:49', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1408, 242, '小猪乔智的秘密武器是什么：', 'user', '2026-03-09 08:36:44', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1409, 242, '小猪乔智的秘密武器是蓝色的泡泡长枪。', 'ai', '2026-03-09 08:36:46', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1410, 243, '晚上好~', 'user', '2026-03-09 21:00:24', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1411, 243, '晚上好！😊 今天过得怎么样？有什么我可以帮到你的吗？', 'ai', '2026-03-09 21:00:25', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1412, 244, '晚上好~', 'user', '2026-03-09 21:03:02', 'deepseek-ai/DeepSeek-V3');
INSERT INTO `message` VALUES (1413, 244, '晚上好~', 'user', '2026-03-09 21:03:27', 'Tongyi-Zhiwen/QwenLong-L1-32B');
INSERT INTO `message` VALUES (1414, 245, '晚上好', 'user', '2026-03-09 21:03:41', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1415, 245, '晚上好！😊 今天过得怎么样？有什么我可以帮到你的吗？', 'ai', '2026-03-09 21:03:44', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1419, 247, '晚上好', 'user', '2026-03-09 21:43:32', 'Tongyi-Zhiwen/QwenLong-L1-32B');
INSERT INTO `message` VALUES (1420, 247, '晚上好', 'user', '2026-03-09 21:43:36', 'deepseek-ai/DeepSeek-V3');
INSERT INTO `message` VALUES (1421, 248, '晚上好', 'user', '2026-03-09 21:50:15', 'Qwen/Qwen3-Coder-30B-A3B-Instruct');
INSERT INTO `message` VALUES (1422, 248, '晚上好！😊\n\n有什么我可以帮助您的吗？或者您想聊聊什么话题呢？', 'ai', '2026-03-09 21:50:16', 'Qwen/Qwen3-Coder-30B-A3B-Instruct');
INSERT INTO `message` VALUES (1423, 248, '晚上好', 'user', '2026-03-09 21:50:20', 'Pro/deepseek-ai/DeepSeek-R1-Distill-Qwen-7B');
INSERT INTO `message` VALUES (1426, 250, '晚上好~', 'user', '2026-03-09 21:59:08', 'Pro/deepseek-ai/DeepSeek-R1');
INSERT INTO `message` VALUES (1427, 250, '晚上好呀～🌙 今天过得怎么样？是刚下班/下课，还是已经窝在沙发里放松啦？需要聊聊趣事、安利好物，或者纯粹想听句晚安？我随时在这儿哦～ （递上虚拟奶茶🍵✨）', 'ai', '2026-03-09 21:59:25', 'Pro/deepseek-ai/DeepSeek-R1');
INSERT INTO `message` VALUES (1428, 250, '晚上好', 'user', '2026-03-09 21:59:32', 'Qwen/Qwen3-Coder-30B-A3B-Instruct');
INSERT INTO `message` VALUES (1429, 250, '晚上好！🌙\n\n很高兴又遇到你～今天是忙碌的一天还是悠闲的一天呢？无论怎样，希望这个夜晚能给你带来一些温暖和放松。\n\n有什么特别想聊的话题吗？或者只是想要一个安静的陪伴？我在这里倾听你的一切～✨', 'ai', '2026-03-09 21:59:34', 'Qwen/Qwen3-Coder-30B-A3B-Instruct');
INSERT INTO `message` VALUES (1430, 251, '晚上好', 'user', '2026-03-09 21:59:44', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1431, 251, '晚上好！很高兴见到你！😊 今天过得怎么样？有什么我可以帮你的吗？', 'ai', '2026-03-09 21:59:45', 'zai-org/GLM-4.5-Air');
INSERT INTO `message` VALUES (1432, 251, '晚上好', 'user', '2026-03-09 21:59:48', 'Pro/deepseek-ai/DeepSeek-R1');
INSERT INTO `message` VALUES (1433, 251, '晚上好呀！✨ 今天过得如何？是刚结束忙碌的工作，还是正在享受悠闲的夜晚呢？有什么想聊的或者需要帮忙的，随时告诉我哦～ 😊', 'ai', '2026-03-09 22:00:03', 'Pro/deepseek-ai/DeepSeek-R1');

SET FOREIGN_KEY_CHECKS = 1;
