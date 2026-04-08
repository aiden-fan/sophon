# Sophon（Local Agent Framework）

> 个人本地智能 Agent 框架：多模型、多模态、Tool/Skill、RAG、MCP、多 Agent 协作（A2A）。

## Quick Start

```bash
git clone https://github.com/aiden-fan/sophon.git && cd sophon
mvn clean install

export SOPHON_HOME="$HOME/.sophon"
export SOPHON_TOKEN="your-secret-token"
export DASHSCOPE_API_KEY="your-dashscope-api-key"
# 按需：OPENAI_API_KEY、ANTHROPIC_API_KEY
mkdir -p ~/.sophon/{config,sessions,plugins,data/{cache,database,logs}}

java -jar server/target/sophon-server-1.0.0.jar
# 另开终端：
java -jar client/cli/target/sophon-cli-1.0.0.jar
```

更完整的启动方式、CLI 交互与停止服务见 **[docs/使用方式.md](docs/使用方式.md)**。

## 文档导航

**完整技术文档**在 **[docs/](docs/)** 目录。面向 **AI 编码助手** 的索引见仓库根目录 **[llms.txt](llms.txt)**（符合 [llms.txt](https://llmstxt.org/) 约定）。


|                                            |                      |
| ------------------------------------------ | -------------------- |
| **[docs/文档索引.md](docs/文档索引.md)**           | **文档索引**（推荐阅读顺序）     |
| [docs/项目概述与架构能力清单.md](docs/项目概述与架构能力清单.md) | 项目概述与架构能力清单          |
| [docs/架构设计.md](docs/架构设计.md)               | 架构设计、组件边界、会话模型、编排与横切 |
| [docs/模块划分.md](docs/模块划分.md)               | 仓库模块划分               |
| [docs/技术栈与核心特性.md](docs/技术栈与核心特性.md)       | 技术栈与核心特性             |
| [docs/安全配置与数据持久化.md](docs/安全配置与数据持久化.md)   | 安全、配置、密钥、数据持久化       |
| [docs/工程实践.md](docs/工程实践.md)               | 契约与测试                |
| [docs/使用方式.md](docs/使用方式.md)               | 使用方式（CLI / 服务端）      |
| [docs/HTTP接口参考.md](docs/HTTP接口参考.md)       | HTTP REST API 示例     |
| [docs/上下文与A2A与插件.md](docs/上下文与A2A与插件.md)   | 上下文、A2A、插件           |
| [docs/开发计划与路线图.md](docs/开发计划与路线图.md)       | 统一开发路线（按序号顺序推进） |
| [docs/常见问题.md](docs/常见问题.md)               | 常见问题                 |
| [docs/贡献指南.md](docs/贡献指南.md)               | 贡献指南                 |


## 许可证

[MIT License](LICENSE)

## 联系方式

- Issues: [https://github.com/aiden-fan/sophon/issues](https://github.com/aiden-fan/sophon/issues)  
- Discussions: [https://github.com/aiden-fan/sophon/discussions](https://github.com/aiden-fan/sophon/discussions)

如需调整版权信息，可编辑 [LICENSE](LICENSE)。