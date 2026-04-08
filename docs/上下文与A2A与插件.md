# 上下文管理、A2A 与插件

[← 文档索引](文档索引.md) · [仓库首页](../README.md)

---



## 上下文管理

### 压缩策略
1. **None**: 不压缩
2. **Merge**: 合并连续相同角色的消息
3. **LLM**: 使用 LLM 压缩历史对话

### 裁剪策略
1. **Recent**: 保留最近的 N 条消息
2. **Relevance**: 基于相关性评分保留消息

### 配置参数
- `max_tokens`: 最大 Token 数
- `compression_strategy`: 压缩策略
- `compression_threshold`: 触发压缩的阈值
- `retention_strategy`: 裁剪策略
- `keep_system`: 是否保留系统消息
- `keep_tools`: 是否保留工具调用历史

## A2A 协作系统

A2A 仅承担 **Agent 间协作编排与消息传递**；**工具与外部资源**由 **Engine / Tool / MCP** 层提供（参见 [架构设计](架构设计.md) 中的「组件职责与边界」）。协作任务中需要调用工具时，由各参与 Agent 在自身推理路径中发起，而非由 A2A 协议替代 MCP。文末 **A2A 最佳实践** 汇总设计原则与模式选型。

### A2A 消息格式
```java
public class A2AMessage {
    private String id;
    private String type;              // request, response, stream, notification
    private AgentInfo from;
    private AgentInfo to;
    private String protocol;
    private A2APayload payload;
    private long timestamp;
}

public class A2APayload {
    private String action;            // chat, tool, skill,协作任务
    private Map<String, Object> data;
    private Map<String, Object> context;
}
```

### 协作模式

#### 1. 主从模式 (Master-Slave)
```yaml
coordination:
  mode: "master"
  master_agent: "coordinator_agent"
  slave_agents:
    - "worker_agent_1"
    - "worker_agent_2"
  routing_strategy: "round_robin"  # round_robin, least_busy, random
```

**特点**:
- 一个主 Agent 控制多个子 Agent
- 主 Agent 负责任务分配和结果聚合
- 子 Agent 只执行分配的任务

**适用场景**: 并行任务处理、批量操作

#### 2. 对等模式 (Peer-to-Peer)
```yaml
coordination:
  mode: "peer"
  agents:
    - id: "agent_1"
      role: "peer"
    - id: "agent_2"
      role: "peer"
    - id: "agent_3"
      role: "peer"
  consensus_strategy: "majority"  # majority, unanimous, weighted
```

**特点**:
- 所有 Agent 地位平等
- Agent 之间直接通信协作
- 共识决策

**适用场景**: 多角度分析、集体决策

#### 3. 任务流模式 (Task Flow)
```yaml
coordination:
  mode: "taskflow"
  workflow:
    - id: "step_1"
      agent: "code_analyzer"
      input: "${input}"
      output_to: "step_2"
    - id: "step_2"
      agent: "code_reviewer"
      input: "${step_1.output}"
      output_to: "step_3"
    - id: "step_3"
      agent: "test_generator"
      input: "${step_2.output}"
      output_to: "final"
  error_handling: "continue"  # continue, stop, retry
```

**特点**:
- Agent 按 DAG 流程顺序执行
- 支持条件分支和并行执行
- 上一级 Agent 的输出作为下一级的输入

**适用场景**: 复杂工作流、多步骤处理

#### 4. 投票模式 (Voting)
```yaml
coordination:
  mode: "voting"
  agents:
    - id: "agent_1"
      weight: 1
    - id: "agent_2"
      weight: 1
    - id: "agent_3"
      weight: 2
  voting_strategy: "weighted"  # simple, weighted
  threshold: 0.6
```

**特点**:
- 多 Agent 处理相同任务
- 根据权重或简单多数投票
- 达到阈值才通过

**适用场景**: 交叉验证、质量控制

### A2A 使用示例

#### 示例 1: 代码分析工作流
```bash
# 创建代码分析工作流
sophon> a2a create \
  --name "代码分析工作流" \
  --agents coding_agent,review_agent,test_agent \
  --mode taskflow \
  --workflow code_analysis

# 定义工作流步骤
workflow:
  - step: "分析代码"
    agent: "coding_agent"
    action: "analyze_code"
  - step: "审查代码"
    agent: "review_agent"
    action: "review_code"
    depends_on: "分析代码"
  - step: "生成测试"
    agent: "test_agent"
    action: "generate_tests"
    depends_on: "审查代码"

# 执行工作流
sophon> a2a task \
  --session a2a_001 \
  --task "分析这段 Java 类" \
  --input "code_snippet=Calculator.java"
```

#### 示例 2: 多角度问题分析
```bash
# 创建对等协作会话
sophon> a2a create \
  --name "多角度分析" \
  --agents analyzer_1,analyzer_2,analyzer_3 \
  --mode peer \
  --consensus majority

# 发送分析任务
sophon> a2a task \
  --session a2a_002 \
  --task "分析这个架构设计的优缺点"

# 查看协作结果
sophon> a2a result --session a2a_002
```

#### 示例 3: 批量任务处理
```bash
# 创建主从协作会话
sophon> a2a create \
  --name "批量处理" \
  --agents coordinator,worker_1,worker_2,worker_3 \
  --mode master \
  --master coordinator \
  --workers worker_1,worker_2,worker_3

# 批量处理多个文件
sophon> a2a batch \
  --session a2a_003 \
  --files file1.java,file2.java,file3.java \
  --task "分析代码复杂度"
```

### A2A 最佳实践

#### Agent 设计原则
1. **单一职责**: 每个 Agent 专注于特定领域
2. **独立性强**: Agent 之间松耦合，避免强依赖
3. **接口清晰**: 明确定义 Agent 的输入输出接口
4. **容错能力**: 每个 Agent 都应该有错误处理机制

#### 协作模式选择
- **主从模式**: 适合需要中央协调的批量任务
- **对等模式**: 适合需要多方协商的决策场景
- **任务流模式**: 适合有明确步骤顺序的工作流
- **投票模式**: 适合需要质量控制和交叉验证的场景

#### 性能优化建议
1. **并行执行**: 在任务流模式中，尽可能让无依赖的步骤并行执行
2. **缓存机制**: 对相同请求进行缓存，减少重复计算
3. **负载均衡**: 合理分配 Agent 任务，避免单个 Agent 过载
4. **超时控制**: 设置合理的超时时间，避免阻塞

#### 安全注意事项
1. **权限控制**: Agent 之间应该有访问权限控制
2. **数据隔离**: 不同 Agent 处理的数据应该隔离
3. **审计日志**: 记录 Agent 之间的所有通信
4. **资源限制**: 限制每个 Agent 的资源使用

## 插件系统

### 插件接口
```java
public interface Plugin {
    String getId();
    String getName();
    String getVersion();
    void onLoad(PluginContext context);
    void onUnload(PluginContext context);
    List<Tool> getTools();
    List<Skill> getSkills();
}
```

### 插件加载
```java
// 服务端自动加载 plugins/ 目录下的插件
PluginManager manager = new PluginManager();
manager.loadPluginsFromDirectory("~/.sophon/plugins/");

// 通过 API 加载插件
POST /api/v1/plugins/load
Content-Type: multipart/form-data
file: @my-plugin.jar
```

### 插件隔离
- 独立的 ClassLoader
- 资源限制 (CPU/内存/超时)
- 沙箱执行环境
