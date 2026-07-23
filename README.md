# 灵枢智能体容器

灵枢智能体容器是一个面向本地桌面工作流的轻量智能体编排项目。它把对话、任务流、智能体配置和会话回放集中到一个统一界面里，用清晰的前端状态和可控的后端接口完成多智能体协作。

## 项目定位

- **对话入口**：用户从主对话界面发起需求，由容器选择合适的智能体和下一步动作。
- **任务流编排**：通过节点、动作、标记和亲和度变化描述可复用的智能体流程。
- **智能体管理**：维护智能体身份、角色、系统提示词、工作区、目标和调度参数。
- **会话回放**：导出会话、查看分支、沙盘推演和 AI 调用路径，便于复盘和调试。
- **工程课程**：通过 20 个可验证任务学习模型契约、Agent、RAG、安全、协议互操作和生产发布。
- **轻量运行**：项目优先控制依赖和系统资源占用，适合与 GraalVM 后端运行方式配合。

## 目录结构

```text
kiniu-back/            后端服务
kiniu-front/nuxt-app/  Nuxt/Vue 前端界面
build/                 构建脚本与打包辅助文件
```

## 一键启动

Windows 下可在项目根目录双击或运行：

```powershell
.\start.bat
```

脚本默认启动后端 `127.0.0.1:8080` 和前端 `127.0.0.1:3000`，日志写入 `logs/`，进程号写入 `.run/`。如果前端还没有安装依赖，会先执行 `npm install`。为降低本机资源占用，一键启动默认关闭 Nuxt DevTools。

默认安全行为：

- 启动后端时会自动生成本机访问令牌，并写入 `.run/local-token`。
- 前端设置页的“本机访问令牌”只在当前浏览器会话保存，不写入长期 localStorage。
- 如果目标端口已经被其他进程占用，启动脚本会直接失败，不会把已有监听进程当成启动成功。
- 停止脚本默认只停止 `.run/` 中记录的本项目进程，不会按端口杀掉不明进程。

常用参数：

```powershell
.\start.ps1 -SkipInstall          # 跳过前端依赖安装检查
.\start.ps1 -BackendOnly          # 只启动后端
.\start.ps1 -FrontendOnly         # 只启动前端
.\start.ps1 -NoBrowser            # 启动后不打开浏览器
.\start.ps1 -CleanLogs            # 启动前清理旧日志
.\start.ps1 -BackendPort 18080    # 自定义后端端口
.\start.ps1 -FrontendPort 13000   # 自定义前端端口
.\start.ps1 -LocalToken "..."     # 使用指定本机访问令牌
.\start.ps1 -NoLocalToken         # 不生成本机访问令牌，仅用于明确的本地调试
.\start.ps1 -EnableDevtools       # 需要调试时才开启 Nuxt DevTools
```

停止服务：

```powershell
.\stop.bat
.\stop.ps1 -BackendOnly
.\stop.ps1 -FrontendOnly
.\stop.ps1 -BackendPort 18080 -FrontendPort 13000  # 自定义端口时同步传入
.\stop.ps1 -ForcePortKill                           # 显式按端口结束监听进程
```

## 前端开发

```bash
cd kiniu-front/nuxt-app
npm install
npm run dev
```

默认开发地址：

```text
http://localhost:3000
```

## 前端构建

```bash
cd kiniu-front/nuxt-app
npm run build
```

当前前端使用 Nuxt 4 + Vue 3，不引入额外 UI 组件库，界面以低噪声、低资源占用和工作台式信息层级为主。

## 后端说明

后端位于 `kiniu-back/`。前端默认连接：

```text
http://localhost:8080
```

主要接口由前端设置页中的“容器后端地址”控制，常用路径包括：

- `/agent/next`
- `/agent/story`
- `/agent/agents`
- `/agent/export/{sessionId}`
- `/learn/catalog`
- `/learn/progress`
- `/learn/tasks/{taskId}/check`
- `/learn/tasks/{taskId}/feedback`
- `/learn/tasks/{taskId}/publish-agent`

## AI 工程课程 3.0

学习中心使用 `kiniu-back/data/learning-catalog.json` 中的静态版本 3 目录，面向零基础转行者设计，预计 18–20 小时完成。原有 7 个任务 ID 全部保留，历史完成项、最高分、草稿和已发布 Agent 不会因目录升级而重置。

### 课程地图

| 模块 | 任务 ID | 核心交付 |
|---|---|---|
| 基础与模型契约 | `requirements-contract`、`http-json-basics`、`model-response-contract` | 需求契约、HTTP 交换、结构化模型响应 |
| Prompt、Context 与数据 | `prompt-context-design`、`data-lifecycle`、`context-memory-budget` | Context 方案、数据生命周期、记忆预算 |
| Workflow、工具与 Agent | `workflow-agent-decision`、`tool-contract`、`agent-trace-recovery` | 架构选型、工具契约、Agent 轨迹与恢复 |
| 评测与 Agent 项目 | `evaluation-suite`、`companion-agent` | 持续评测集、完整 Agent 项目 |
| RAG 检索增强 | `rag-pipeline`、`rag-evaluation` | RAG 设计、召回与 groundedness 证据 |
| GenAI 安全 | `genai-red-team`、`access-concurrency` | 威胁模型、攻击结果、服务安全契约 |
| 协议互操作 | `mcp-integration`、`a2a-collaboration` | MCP 服务、A2A Agent Card 与委派轨迹 |
| 生产与综合项目 | `observability-runbook`、`release-safety`、`architecture-collaboration` | 可观测样本、发布门禁、最终架构决策 |

课程依赖不是简单的线性关卡。`prerequisiteTaskIds` 构成显式有向无环图，任务只有在全部前置任务完成后才解锁；系统按目录顺序推荐第一个“未完成且已解锁”的任务。旧进度加载后，如果当前任务已完成或尚未解锁，会自动迁移到下一项可执行任务；全部完成时当前任务 ID 为空。

### 讲义与工程证据

每项任务包含 300–600 字中文讲义、明确交付物、总计 100 分的确定性检查，以及 1–3 个带版本和访问日期的官方 HTTPS 参考链接。检查器支持原有规则，并增加：

- `json-pointer-present`：使用 RFC 6901 JSON Pointer 验证嵌套字段存在且内容有效。
- `json-array-shape`：验证数组最小数量以及每个对象必须包含的字段。
- `json-number-range`：验证延迟、token、预算等数值位于允许区间。

`document` 任务直接在工作区编辑；`import` 任务只接受当前任务声明的文件名和 JSON、Markdown、文本内容。导入前会展示文件名与字节数并等待确认，单文件不超过 100 KB、一次提交总量不超过 500 KB。所有导入证据必须包含 `source`、`capturedAt`、`requestId` 或等价来源信息。

确定性检查验证的是工程证据的结构、完整性和范围，不会声称能够密码学证明外部 trace、模型响应或攻击结果的真实性。

### 请求安全边界

| 请求 | 允许发送的配置 |
|---|---|
| 目录、进度、确定性检查、Agent 发布 | 仅 `X-Local-Token` |
| Mentor Feedback | `X-Local-Token`，以及用户配置的 provider URL、API key、model |

只要请求包含本机令牌或 provider 密钥，前端就要求后端地址为 loopback。Mentor 只使用确定性检查结果、任务目标和用户问题生成反馈，不会把原始完整提交文件拼入模型请求。

### 官方工程参考

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)、[Function Calling](https://developers.openai.com/api/docs/guides/function-calling)、[Retrieval](https://developers.openai.com/api/docs/guides/retrieval)、[Evaluation best practices](https://developers.openai.com/api/docs/guides/evaluation-best-practices)
- [Anthropic: Building effective agents](https://www.anthropic.com/engineering/building-effective-agents)、[Context engineering](https://www.anthropic.com/engineering/effective-context-engineering-for-ai-agents)、[Agent evals](https://www.anthropic.com/engineering/demystifying-evals-for-ai-agents)
- [MCP Tools](https://modelcontextprotocol.io/specification/2025-11-25/server/tools)、[MCP Authorization](https://modelcontextprotocol.io/specification/2025-11-25/basic/authorization)、[A2A Specification](https://a2a-protocol.org/latest/specification/)
- [OWASP LLM Top 10](https://genai.owasp.org/llm-top-10/)、[NIST AI 600-1](https://www.nist.gov/publications/artificial-intelligence-risk-management-framework-generative-artificial-intelligence)、[OpenTelemetry GenAI](https://github.com/open-telemetry/semantic-conventions-genai)

### 验收

后端、请求头隔离和前端生产构建：

```powershell
cd kiniu-back
mvn test

cd ..\kiniu-front\nuxt-app
npm run test:unit
npm run build
```

浏览器验收至少覆盖基础主线、RAG、安全、MCP/A2A 和最终综合项目的解锁路径，并分别检查桌面端与移动端无横向溢出、控制台无错误、关键学习 API 返回成功。浏览器测试使用临时进度时，不应覆盖用户现有的 `learning-progress.json`。

## 命名

“灵枢”取“智能体中枢、路由枢纽、轻量调度核心”之意。完整中文名暂定为：

```text
灵枢智能体容器
```
