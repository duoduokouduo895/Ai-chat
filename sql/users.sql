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

 Date: 09/03/2026 22:14:33
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for users
-- ----------------------------
DROP TABLE IF EXISTS `users`;
CREATE TABLE `users`  (
  `user_id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `password` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
  `email` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
  `created_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP,
  `last_active_at` timestamp NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最后活跃时间',
  `is_guest` tinyint(1) NULL DEFAULT 0 COMMENT '是否为游客账号',
  PRIMARY KEY (`user_id`) USING BTREE,
  UNIQUE INDEX `uk_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `uk_email`(`email` ASC) USING BTREE,
  UNIQUE INDEX `idx_users_username`(`username` ASC) USING BTREE,
  UNIQUE INDEX `idx_users_email`(`email` ASC) USING BTREE,
  INDEX `idx_users_created_at`(`created_at` ASC) USING BTREE,
  INDEX `idx_users_guest_last_active_at`(`is_guest` ASC, `last_active_at` ASC) USING BTREE
) ENGINE = InnoDB AUTO_INCREMENT = 38 CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci ROW_FORMAT = DYNAMIC;

-- ----------------------------
-- Records of users
-- ----------------------------
INSERT INTO `users` VALUES (20, 'Dduo', '$2a$10$O7e.7ozLzgNxGwAGL6XPse7tUmyJePuz8NhZaGskdM0M3pd6B4fwS', '123456@qq.com', '2025-09-08 23:45:48', '2026-03-09 14:11:56', 0);
INSERT INTO `users` VALUES (22, 'Duog_68767864', '$2a$10$t8eGOryvvHjvPyuScL.Is.eGXT6sbgEqSWCVPTrQakYlHFfWMEUO6', NULL, '2026-03-08 23:58:59', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (23, 'Duog_4b877599', '$2a$10$DGr8i0lerZ0E9ijhE.VY/u4uyheiAqcEAJ/oZGGV/2fwyJZfB7.4G', NULL, '2026-03-09 00:09:09', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (24, 'Duog_66819cdd', '$2a$10$NxBrk5JTWQ9EL.xIhloA9uHKhOxOEafN35y4/hrj5wU2depb.NAN.', NULL, '2026-03-09 00:10:58', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (25, 'Duog_b451d0a6', '$2a$10$Ml6imy6vGN4zkw2jmGUd.OdDOPK5BbAmcqRVpSIZYi7xxkDYLh6HS', NULL, '2026-03-09 00:17:04', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (26, 'Duog_5e5ed54c', '$2a$10$d67IZkke3M2rpN1GDXxb..tRnOYfK2oAsEdjatYQKNmtUvVrxu0N2', NULL, '2026-03-09 00:17:57', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (27, 'Duog_a55c67bf', '$2a$10$a4uVMzHwXSy/Z211uhMpA.h4Tihr2..edUrATroSolLqtNFdyBoJy', NULL, '2026-03-09 00:18:59', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (28, 'Duog_b5e1fc96', '$2a$10$sbKa.PgDQ.xlAWBlrxLDEOZf0z5QD2OJTCyLHVqRx7SibNJaKj3ku', NULL, '2026-03-09 00:23:47', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (29, 'Duog_06fdfa4b', '$2a$10$zOn3Yzyt5unPey3r.xdFq.RG23gq0cjGEVcNbaDXucENUvfDoHGFm', NULL, '2026-03-09 00:29:05', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (30, 'Duog_e1c88bea', '$2a$10$xIcLd6qfHs3pVBO2gAqqJOdsG7xlkam.KX2Md/PSxmCAG6ZN8xNf.', NULL, '2026-03-09 13:00:16', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (31, 'Duog_d0f7c81d', '$2a$10$QCG9kDc153kY6svfZzERgOetjEGPYhi8/Q.0vs6u1J4c/t5wsYt/y', NULL, '2026-03-09 13:01:50', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (32, 'Duog_1d23d8af', '$2a$10$F7Z3Lqh3x2qjgFK91E90z.JAH/oUfgMfcmQPTDkTJIsgMWi753gJ6', NULL, '2026-03-09 13:03:37', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (33, 'Duog_f3e7ed77', '$2a$10$nDkEc6aJwIUSilwm7dAjOOhC66XA.iytTUddvuRy/ZHAxBA8F5Kt6', NULL, '2026-03-09 13:50:01', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (34, 'Duog_2ad39985', '$2a$10$9AEngLCmS3G1yIVM9InwDe2IJ958nEgv6u8YN5Mv6tlSFW1UHpMBS', NULL, '2026-03-09 13:52:49', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (35, 'Duog_6a345ac8', '$2a$10$xCTw.3TIU0KQR5C/OOb5.uG8wnRCa2iFykppKQ/bBJ11sU3UjAJye', NULL, '2026-03-09 13:58:09', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (36, 'Duog_b07ea002', '$2a$10$DVJJAsN9Z3b7nlu1NbNKjuf6fYhbKV.nj.wIEvaWcylO8oEty110i', NULL, '2026-03-09 13:58:58', '2026-03-09 14:11:56', 1);
INSERT INTO `users` VALUES (37, 'Duog_13a4b5ab', '$2a$10$uR4MiPoxeUz3GYLQmremzOExZsYEj46qs2wFHb/ARyfSOe/5c2SKq', NULL, '2026-03-09 13:59:38', '2026-03-09 14:11:56', 1);

SET FOREIGN_KEY_CHECKS = 1;
