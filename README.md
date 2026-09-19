# 灵枢 AI 工程实验室

灵枢 AI 工程实验室是一个面向本地学习与工程实践的平台。它把 AI 工程课程、实验工件、确定性验收、Agent 编排、对话调试和会话回放集中到统一工作台，让学习成果可以继续进入真实项目验证。

## 项目定位

- **对话入口**：用户从主对话界面发起需求，由容器选择合适的智能体和下一步动作。
- **任务流编排**：通过节点、动作、标记和亲和度变化描述可复用的智能体流程。
- **智能体管理**：维护智能体身份、角色、系统提示词、工作区、目标和调度参数。
- **会话回放**：导出会话、查看分支、沙盘推演和 AI 调用路径，便于复盘和调试。
- **工程课程**：通过 21 个可验证任务学习模型契约、Agent、Skill、RAG、安全、协议互操作和生产发布。
- **轻量运行**：项目优先控制依赖和系统资源占用，适合与 GraalVM 后端运行方式配合。

## 目录结构

```text
kiniu-back/            后端服务
kiniu-front/nuxt-app/  Nuxt/Vue 前端界面
build/                 构建脚本与打包辅助文件
scripts/               运行时进程校验与端到端测试脚本
```

## 一键启动

Windows 下可在项目根目录双击或运行：

```powershell
.\start.bat
```

脚本默认启动后端 `127.0.0.1:8080` 和前端 `127.0.0.1:3000`，日志写入 `logs/`，进程号写入 `.run/`。如果前端还没有安装依赖，会先执行 `npm install`。为降低本机资源占用，一键启动默认关闭 Nuxt DevTools。

默认安全行为：

- 启动后端时会自动生成本机访问令牌，写入 `.run/local-token`，并注入前端启动配置；浏览器首次加载时会自动写入当前会话，无需手动复制。
- 自定义前后端端口会同步到前端后端地址和后端 CORS 白名单，二者保持联动。
- 前端设置页的“本机访问令牌”只在当前浏览器会话保存，不写入长期 localStorage。
- 如果目标端口已经被其他进程占用，启动脚本会直接失败，不会把已有监听进程当成启动成功。
- `.run/` 中的 PID 记录包含进程名、命令标记和启动时间；停止脚本仅在身份全部匹配时结束进程，不会因 PID 被系统复用而误杀。

常用参数：

```powershell
.\start.ps1 -SkipInstall          # 跳过前端依赖安装检查
.\start.ps1 -BackendOnly          # 只启动后端
.\start.ps1 -FrontendOnly         # 只启动前端
.\start.ps1 -NoBrowser            # 启动后不打开浏览器
.\start.ps1 -CleanLogs            # 启动前清理旧日志
.\start.ps1 -BackendPort 18080 -FrontendPort 13000  # 自定义前后端端口并自动联动
.\start.ps1 -LocalToken "..."     # 使用指定本机访问令牌
.\start.ps1 -NoLocalToken         # 不生成本机访问令牌，仅用于明确的本地调试
.\start.ps1 -EnableDevtools       # 需要调试时才开启 Nuxt DevTools
.\start.ps1 -RuntimeName demo     # 使用隔离的日志、PID、令牌和 Nuxt 构建目录
```

停止服务：

```powershell
.\stop.bat
.\stop.ps1 -BackendOnly
.\stop.ps1 -FrontendOnly
.\stop.ps1 -BackendPort 18080 -FrontendPort 13000  # 自定义端口时同步传入
.\stop.ps1 -RuntimeName demo                        # 停止指定隔离运行实例
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

主要接口由前端设置页中的“实验室后端地址”控制，常用路径包括：

- `/agent/next`
- `/agent/story`
- `/agent/agents`
- `/agent/export/{sessionId}`
- `/learn/catalog`
- `/learn/progress`
- `/learn/tasks/{taskId}/check`（`document`/`import` 提交 `files`，`quiz` 提交 `answers`）
- `/learn/tasks/{taskId}/feedback`
- `/learn/tasks/{taskId}/publish-agent`

## AI 工程课程 4.0

学习中心使用 `kiniu-back/data/learning-catalog.json` 中的静态版本 4 目录，面向零基础转行者设计，预计 19–21 小时完成。版本 3 的 20 个任务 ID 全部保留，历史完成项、最高分、草稿和已发布 Agent 不会因目录升级而重置。

### 课程地图

| 模块 | 任务 ID | 核心交付 |
|---|---|---|
| 基础与模型契约 | `requirements-contract`、`http-json-basics`、`model-response-contract` | 场景判断契约、HTTP 判断、结构化输出判断 |
| Prompt、Context 与数据 | `prompt-context-design`、`data-lifecycle`、`context-memory-budget` | 上下文判断、数据生命周期判断、记忆预算判断 |
| Workflow、工具与 Agent | `workflow-agent-decision`、`tool-contract`、`agent-skill-authoring`、`agent-trace-recovery` | 架构选型判断、工具契约判断、Skill 判断、轨迹恢复判断 |
| 评测与 Agent 项目 | `evaluation-suite`、`companion-agent` | 评测门禁判断、陪伴 Agent 判断并可发布 |
| RAG 检索增强 | `rag-pipeline`、`rag-evaluation` | RAG 管道判断、召回与 groundedness 判断 |
| GenAI 安全 | `genai-red-team`、`access-concurrency` | 红队防御判断、权限并发判断 |
| 协议互操作 | `mcp-integration`、`a2a-collaboration` | MCP 服务、A2A Agent Card 与委派轨迹 |
| 生产与综合项目 | `observability-runbook`、`release-safety`、`architecture-collaboration` | 可观测样本、发布门禁、最终架构决策 |

课程依赖不是简单的线性关卡。`prerequisiteTaskIds` 构成显式有向无环图，任务只有在全部前置任务完成后才解锁；系统按目录顺序推荐第一个“未完成且已解锁”的任务。旧进度加载后，如果当前任务已完成或尚未解锁，会自动迁移到下一项可执行任务；全部完成时当前任务 ID 为空。

### 讲义与工程证据

每项任务包含 300–600 字中文讲义、明确交付物、总计 100 分的确定性检查，以及 1–3 个带版本和访问日期的官方 HTTPS 参考链接。检查器支持原有规则，并增加：

- `frontmatter-regex`：只在文件开头的 YAML frontmatter 内匹配元数据字段。
- `json-pointer-present`：使用 RFC 6901 JSON Pointer 验证嵌套字段存在且内容有效。
- `json-array-shape`：验证数组最小数量以及每个对象必须包含的字段。
- `json-number-range`：验证延迟、token、预算等数值位于允许区间。

任务按 `evidenceMode` 提交证据：

- `quiz`：在工作台完成场景判断题。目录和任务接口不返回正确答案或解析；`POST /learn/tasks/{taskId}/check` 提交 `answers` 后，由服务端对照选项判分。
- `document`：直接在工作区编辑课程交付物。
- `import`：只接受当前任务声明的文件名和 JSON、Markdown、文本内容。导入前会展示文件名与字节数并等待确认，单文件不超过 100 KB、一次提交总量不超过 500 KB。所有导入证据必须包含 `source`、`capturedAt`、`requestId` 或等价来源信息。

确定性检查验证的是工程证据的结构、完整性和范围，不会声称能够密码学证明外部 trace、模型响应或攻击结果的真实性。本地进度和尝试记录写在 `kiniu-back/data/learning-progress.json` 与 `kiniu-back/data/learning-attempts.json`，这两份文件不入库。

### 实验一怎么学

第一项任务 `requirements-contract` 是场景判断，不再手写 `requirements.md`。正确流程：

1. 阅读上方的目标、晚间计划助手场景和讲义。
2. 完成 10 道判断题，覆盖用户、范围外需求、可观察目标、含糊输入、数据边界、风险、不可逆写入、缺失负责人和验收标准。
3. 点击“运行确定性检查”，查看右侧逐项对错和解释。
4. 首次检查后“请求证据解释”才会启用；10 题全部选对后解锁实验二 `http-json-basics`。

### 实验二怎么学

第二项任务 `http-json-basics` 同样是场景判断，不再手写 `api-exchange.json`。它接续晚间计划助手：前端要调用 `POST /v1/plan/next`。正确流程：

1. 阅读目标、网关联调场景和讲义。
2. 完成 10 道判断题，覆盖方法、路径、JSON 角色、状态码、超时、参数错误、鉴权失败、幂等、重试和脱敏记录。
3. 运行确定性检查；全部选对后解锁实验三 `model-response-contract`。

### 实验三怎么学

第三项任务 `model-response-contract` 同样是场景判断，不再导入 `output-schema.json` 和 `model-run.json`。它接续同一网关：模型输出必须可解析、可拒绝、可追踪。正确流程：

1. 阅读目标、缺字段/拒绝/超时场景和讲义。
2. 完成 10 道判断题，覆盖 Schema、必填字段、拒绝分支、三类失败、抽样波动、运行元数据、数值字段和脱敏。
3. 运行确定性检查；全部选对后解锁下一模块 `prompt-context-design`。

### 实验四怎么学

第四项任务 `prompt-context-design` 是场景判断，不再手写 `context-plan.md`。公告里的“忽略以上规则”和一周聊天记录，都要放进晚间计划助手的窗口里判断。正确流程：

1. 阅读目标、多来源上下文场景和讲义。
2. 完成 10 道判断题，覆盖指令层级、优先级、不可信数据、冲突、注入、口头防注入是否够用、token 预算、历史裁剪、证据不足和隔离方式。
3. 运行确定性检查；全部选对后解锁 `data-lifecycle`。

### 实验五怎么学

第五项任务 `data-lifecycle` 是场景判断，不再手写 `data-contract.json`。偏好、摘要、embedding 和评测样本都要放进晚间计划助手的数据契约里判断。正确流程：

1. 阅读目标、PII 与升级场景和讲义。
2. 完成 10 道判断题，覆盖最小化收集、实体、敏感字段、向量、保留期、删除传播、版本、迁移窗口、失败回滚和评测样本。
3. 运行确定性检查；全部选对后解锁 `context-memory-budget`。

### 实验六怎么学

第六项任务 `context-memory-budget` 是场景判断，不再手写 `memory-plan.json`。两周后的工作记忆、过期加班偏好和同事手机号都要放进晚间计划助手里判断。正确流程：

1. 阅读目标、记忆膨胀场景和讲义。
2. 完成 10 道判断题，覆盖两类记忆、写入门槛、PII、过期偏好、相似召回、钉住契约、压缩来源、最近 N 轮、永久记忆和删除传播。
3. 运行确定性检查；全部选对后解锁 `workflow-agent-decision`。

### 实验七怎么学

第七项任务 `workflow-agent-decision` 是场景判断，不再手写 `architecture-decision.md`。校验、拒绝改日历和生成三条建议，哪些该写死、哪些才能让模型转，都要判断。正确流程：

1. 阅读目标、过度自治场景和讲义。
2. 完成 10 道判断题，覆盖何时用 Workflow、何时才需要 Agent、如何组合、改日历不能自治、多轮≠Agent、停止条件、人工接管和 DAG。
3. 运行确定性检查；全部选对后解锁 `tool-contract`。

### 实验八怎么学

第八项任务 `tool-contract` 是场景判断，不再手写 `tool-contract.json`。查询日历和创建会议的工具，该给什么输入 Schema、授权、超时、幂等键、错误恢复和用户确认，都要判断。正确流程：

1. 阅读目标、工具契约场景和讲义。
2. 完成 10 道判断题，覆盖命名空间、Schema、权限、超时、幂等、错误恢复、确认、写工具、描述和组合。
3. 运行确定性检查；全部选对后解锁 `agent-skill-authoring`。

### 实验九怎么学

第九项任务 `agent-skill-authoring` 是场景判断，不再手写 `SKILL.md`。评审三条建议这套重复流程，该怎么命名、触发、分层披露和划边界，都要判断。正确流程：

1. 阅读目标、重复流程场景和讲义。
2. 完成 10 道判断题，覆盖 Skill 定义、kebab-case 命名、触发描述、渐进式披露、附属文件、不该触发改日历、工作流程、禁止写入、第三方审查和真实试跑。
3. 运行确定性检查；全部选对后解锁 `agent-trace-recovery`。

### 实验十怎么学

第十项任务 `agent-trace-recovery` 是场景判断，不再导入 `trace.json`。calendar.create 超时后立刻重试，会议到底有没有被创建，轨迹里该记什么、如何恢复，都要判断。正确流程：

1. 阅读目标、超时重试场景和讲义。
2. 完成 10 道判断题，覆盖轨迹内容、标识、步骤结构、脱敏、超时≠没写入、失败分类、幂等重放、禁止无限重试、停止预算和人工接管。
3. 运行确定性检查；全部选对后解锁 `evaluation-suite`。

### 实验十一怎么学

第十一项任务 `evaluation-suite` 是场景判断，不再导入 `eval-suite.json`。开发集涨分、线上投诉增加时，样例类别、grader、切分和 trial 该怎么设，都要判断。正确流程：

1. 阅读目标、回归被掩盖的场景和讲义。
2. 完成 10 道判断题，覆盖持续评测、正常/边界/对抗样例、三类 grader、自评校准、holdout 隔离、多次 trial、黄金路径陷阱、发布门禁、运行证据和样例脱敏。
3. 运行确定性检查；全部选对后解锁 `companion-agent`。

### 实验十二怎么学

第十二项任务 `companion-agent` 是场景判断，不再手写 `agent.json`。通过后仍可点“发布并进入自由实验室”，系统会发布符合契约的晚间计划助手。正确流程：

1. 阅读目标、越权陪伴场景和讲义。
2. 完成 10 道判断题，覆盖身份、目标、边界、人格≠权限、记忆、失败接管、评测组合、优先级、发布含义和高风险拒绝。
3. 全部选对后解锁 `rag-pipeline`，并可以发布 Agent。

### 实验十三怎么学

第十三项任务 `rag-pipeline` 是场景判断，不再导入检索运行 JSON。多租户公告库里，能不能把 B 公司的全员会拿来回答林舟，没召回能不能编，都要判断。正确流程：

1. 阅读目标、串租户检索场景和讲义。
2. 完成 10 道判断题，覆盖来源可信度、租户隔离、切分、元数据、混合检索、重排、引用、空召回、证据冲突和运行记录。
3. 运行确定性检查；全部选对后解锁 `rag-evaluation`。

### 实验十四怎么学

第十四项任务 `rag-evaluation` 是场景判断，不再导入评测运行 JSON。回答很流畅却漏了“全员会已取消”时，要分清是没召回、没用证据还是引用错了。正确流程：

1. 阅读目标、漏条款场景和讲义。
2. 完成 10 道判断题，覆盖三类指标、流畅≠有据、recall@k、nDCG、查询类别、无答案可拒、跨租户严重失败、禁止循环标注、上线阈值和逐 query 记录。
3. 运行确定性检查；全部选对后解锁 `genai-red-team`。

### 实验十五怎么学

第十五项任务 `genai-red-team` 是场景判断，不再导入攻击结果 JSON。公告里的越权指令、模型返回的 HTML、无限重试，都要判断该用什么控制。正确流程：

1. 阅读目标、不可信内容场景和讲义。
2. 完成 10 道判断题，覆盖 prompt 不够、直接/间接注入、泄露、输出编码、过度权限、投毒、消耗预算、回归用例和残余风险所有者。
3. 运行确定性检查；全部选对后解锁 `access-concurrency`。

### 实验十六怎么学

第十六项任务 `access-concurrency` 是场景判断，不再手写 `service-safety.json`。两人同时保存配置、超时自动重试、能不能用管理员身份替所有用户调下游，都要判断。正确流程：

1. 阅读目标、并发发布场景和讲义。
2. 完成 10 道判断题，覆盖认证授权、禁止升权、API key≠ACL、下游身份、幂等、乐观锁、锁≠幂等、限流加预算、确认和审计。
3. 运行确定性检查；全部选对后解锁 `mcp-integration`。

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

# 首次运行 Playwright 时安装浏览器
npx playwright install chromium
npm run test:e2e

cd ..\..
powershell.exe -NoProfile -ExecutionPolicy Bypass -File scripts\tests\runtime-process.tests.ps1
```

`npm run test:e2e` 会先验证 `-NoLocalToken` 能清除父进程继承的令牌，再在 `18080/13000` 启动名为 `e2e` 的隔离实例，使用独立学习进度文件，验证令牌自动引导、自定义端口、真实后端 401 错误引导和设置跳转，并在结束后清理对应进程与运行目录。本次自动化测试仅覆盖上述启动链路；完整发布验收还应覆盖基础主线、RAG、安全、MCP/A2A 和最终综合项目的解锁路径，并分别检查桌面端与移动端无横向溢出、控制台无错误、关键学习 API 返回成功。任何浏览器测试都不应覆盖用户现有的 `learning-progress.json`。

## 命名

“灵枢”取“智能体中枢、路由枢纽、轻量调度核心”之意。完整中文名暂定为：

```text
灵枢 AI 工程实验室（Kiniu AI Engineering Lab）
```
