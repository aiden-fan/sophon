# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

Sophon Novel — 一个用于创作网络小说的 Java 项目。

## 技术栈

- **Java 17+**
- **Maven 3.8+**
- 项目根目录: `/Users/starcloud/data/project/sophon`

## 常用命令

### 构建与运行

```bash
# 编译
mvn compile

# 运行（开发模式，需要 Maven）
mvn exec:java

# 打包（生成包含所有依赖的 fat jar）
mvn package

# 运行 jar 包
java -jar target/sophon-novel-0.1.0-SNAPSHOT.jar

# 指定 API key 运行
DASHSCOPE_API_KEY=your-key java -jar target/sophon-novel-0.1.0-SNAPSHOT.jar
```

### 代码质量

```bash
# 检查代码风格
mvn checkstyle:check

# 清理构建产物
mvn clean
```

## 开发约定

- 使用中文注释和命名，贴合小说创作场景
- 新功能先在 `feature/novel` 分支开发，完成后合并到 `main`
- 提交信息使用 `feat:` / `fix:` / `refactor:` 前缀
