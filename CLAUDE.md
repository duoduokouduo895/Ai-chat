# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a full-stack web application with a Java Spring Boot backend and a Vue.js frontend.

### Backend

The backend is a reactive Spring Boot application built with Maven. It uses:
- Spring WebFlux for non-blocking web requests.
- Spring Data R2DBC for reactive database access.
- MySQL as the primary database.
- Redis for caching.
- RabbitMQ for messaging.
- Elasticsearch for search functionality.
- Spring Security for authentication and authorization.

### Frontend

The frontend is a single-page application built with Vue.js and Vite.

## Common Commands

## 项目中所有的注释都使用中文

### Backend (from the root directory)

- **Install dependencies and build the project:**
  ```bash
  mvn clean install
  ```
- **Run the application:**
  ```bash
  mvn spring-boot:run
  ```

### Frontend (from the `frontend` directory)

- **Install dependencies:**
  ```bash
  npm install
  ```
- **Run the development server:**
  ```bash
  npm run dev
  ```
- **Build for production:**
  ```bash
  npm run build
  ```

- 你提供的代码的注释都需要使用中文注释
- DEFAULT_GROUP文件夹是我上传nacos的配置问件，我的项目是根据前端页面实现后端代码，所以你尽量要根据前端来设计后端代码，避免bug
- 使用中文与我沟通
- 你不需要执行后端命令 只需要通知我 需要执行就行
- 一次性只能调用一个工具！ 我宁可你慢点响应
- ###文件路径处理
-Windows环境下路径分隔符可能导致文件读取问题
-如果读取文件失败，路径中存在特殊字符\f(formfeed,ASCII12,\014)，将路径中的反斜杠\替换为正斜杠/再读取文件