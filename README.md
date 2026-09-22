# 灵枢 AI 助手课

灵枢是一个本地学习工作台。没有工程背景的人可以从晚间计划助手的场景判断学起：先读场景和讲义，再做十道判断，不必写代码。通过一课后，可以去对话里按同样的边界试一句今晚的待办。任务流和助手编排仍在设置的进阶里，不挡第一次学习。

## 打开后怎么学

1. 运行一键启动，浏览器会打开学习页。
2. 当前课是「先写清这个助手帮谁、不能干什么」。先记住这一句，再读场景和讲义，一次做一题。
3. 80 分及格后点「进入下一课」，或点「去对话里试试今晚」。对话里会放进这一课写好的那句，助手按这句里的待办和边界回答，而不是再要你重贴一遍。
4. 主线 17 课约 7 小时；选修 4 课不挡主线完成。

## 项目定位

- **学习课**：主线 17 个场景判断，选修 4 课。先搞清帮谁、不能干什么，再谈调用、记忆、工具和上线。
- **对话里试**：每一课有一句写好的今晚练习。第一课仍是最多三条下一步，不改日历；后面的课换成这一课刚学的边界。
- **进阶工具**：任务流、助手目录和会话回放放在设置里的进阶，给要改流程的人用。
- **轻量运行**：控制依赖和本机资源占用，适合与 GraalVM 后端运行方式配合。

## 目录结构

```text
kiniu-back/            后端服务
kiniu-front/nuxt-app/  Nuxt/Vue 前端界面
build/                 构建脚本与打包辅助文件
scripts/               运行时进程校验与端到端测试脚本
```

## 一键启动

Windows 下在项目根目录双击或运行：

```powershell
.\start.bat
```

浏览器会打开学习页。后端默认 `127.0.0.1:8080`，前端 `127.0.0.1:3000`。本机访问令牌会自动生成并写入浏览器这次会话，不用手抄。端口被占用时启动会失败，而不会把别人的进程当成启动成功。

停止：

```powershell
.\stop.bat
```

常用参数：

```powershell
.\start.ps1 -NoBrowser                              # 启动后不打开浏览器
.\start.ps1 -CleanLogs                              # 启动前清理旧日志
.\start.ps1 -BackendPort 18080 -FrontendPort 13000  # 换端口，前后端会一起改
.\stop.ps1 -BackendPort 18080 -FrontendPort 13000   # 自定义端口停止时要带上
```

调试用：

```powershell
.\start.ps1 -SkipInstall          # 跳过前端依赖安装检查
.\start.ps1 -BackendOnly          # 只启动后端
.\start.ps1 -FrontendOnly         # 只启动前端
.\start.ps1 -LocalToken "..."     # 使用指定本机访问令牌
.\start.ps1 -NoLocalToken         # 不生成本机访问令牌
.\start.ps1 -EnableDevtools       # 打开 Nuxt DevTools
.\start.ps1 -RuntimeName demo     # 隔离日志、PID、令牌和前端构建目录
.\stop.ps1 -RuntimeName demo
.\stop.ps1 -ForcePortKill         # 按端口结束监听进程
```

## 开发时怎么跑

普通人用上面的一键启动即可。改代码时：

```bash
cd kiniu-front/nuxt-app
npm install
npm run dev
```

前端默认 `http://localhost:3000`，后端 `http://localhost:8080`。生产构建：`npm run build`。界面不引入额外 UI 库。

学习课走 `/learn/catalog`、`/learn/progress`、`/learn/tasks/{taskId}/check`。对话走 `/agent/next`。设置里的进阶才用到任务流、助手目录和会话导出。

## AI 助手课 4.0

学习中心使用 `kiniu-back/data/learning-catalog.json` 中的静态版本 4 目录，面向没有工程背景的普通人设计。主线 17 个场景判断约 7 小时，80 分及格；把查公告和看日历交给别的助手、拿去用之后怎么盯这四课为选修，约 2 小时，不挡主线完成。版本 3 的 20 个任务 ID 全部保留，历史完成项、最高分、草稿和已发布 Agent 不会因目录升级而重置。

### 课程地图

| 模块 | 任务 ID | 普通人打开会看到 |
|---|---|---|
| 先说清楚，再看点了生成之后 | `requirements-contract`、`http-json-basics`、`model-response-contract` | 帮谁、点了生成之后发生了什么、回来的内容怎样才算能用 |
| 它看到什么、记住什么、留下什么 | `prompt-context-design`、`data-lifecycle`、`context-memory-budget` | 该听谁的、个人数据能留多久、什么必须忘掉 |
| 固定流程、工具和自己做决定 | `workflow-agent-decision`、`tool-contract`、`agent-skill-authoring`、`agent-trace-recovery` | 何时用固定流程、连点两次会不会做两遍、超时了怎么查 |
| 怎么测，怎样才算能每天用 | `evaluation-suite`、`companion-agent` | 避免练题会了、真用的时候不会；每天能用之前钉死叫什么和不能做什么 |
| 找到的公告能不能用 | `rag-pipeline`、`rag-evaluation` | 能不能用别人公司的公告；话说得顺却漏了关键条款 |
| 挡住越权吩咐和乱来 | `genai-red-team`、`access-concurrency` | 越权吩咐和乱给的链接怎么挡；能不能替所有人去看 |
| 接上公告、日历和别的助手 | `mcp-integration`、`a2a-collaboration`（选修） | 公告该当资料还是工具；交给别的助手，发出去算不算完成 |
| 拿去用之后怎么盯、怎么退（选修） | `observability-runbook`、`release-safety`、`architecture-collaboration` | 变慢怎么追、怎样小范围试、图能不能代替说好的边界 |

课程依赖不是简单的线性关卡。`prerequisiteTaskIds` 构成显式有向无环图，任务只有在全部前置任务完成后才解锁。系统按目录顺序优先推荐未完成且已解锁的主线任务；主线完成后才推荐选修。旧进度加载后，如果当前任务已完成或尚未解锁，会自动迁移到下一项可执行任务；全部完成时当前任务 ID 为空。

### 讲义与判断

每课有 300–600 字中文讲义、一句可复述的边界、十道场景判断（80 分及格），以及 1–3 个带版本和访问日期的官方 HTTPS 参考。当前 21 课全部是 `quiz`：在工作台一次做一题，目录接口不返回正确答案。提交后由服务端判分，错题解释出现在题下。这验证的是选项是否符合约定，不会声称证明外部模型响应或攻击结果的真实性。

本地进度写在 `kiniu-back/data/learning-progress.json` 与 `kiniu-back/data/learning-attempts.json`，不入库。检查器仍支持 `document` / `import`，便于以后扩展手写或导入证据。

### 每课怎么学

21 课流程相同：

1. 先看「这一课记住」，再读场景和讲义。
2. 一次做一题，做完十道后点「提交判断」；错了看题下解释。
3. 80 分及格后，这句话留在「你已经能判断」里。可以进入下一课，或去对话里试试今晚。
4. 主线完成后才出现选修。`companion-agent` 通过后仍可发布助手，去对话里试。

### 请求安全边界

| 请求 | 允许发送的配置 |
|---|---|
| 目录、进度、提交判断、助手发布 | 仅 `X-Local-Token` |
| Mentor Feedback | `X-Local-Token`，以及用户配置的 provider URL、API key、model |

只要请求包含本机令牌或 provider 密钥，前端就要求后端地址为 loopback。导师反馈只使用判分结果、任务目标和用户问题，不会把原始完整提交拼进模型请求。

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

`npm run test:e2e` 会先验证 `-NoLocalToken` 能清除父进程继承的令牌，再在 `18080/13000` 启动名为 `e2e` 的隔离实例，使用独立学习进度文件。除启动、令牌和 401 引导外，还会验证第一课错题解释、去对话里试今晚、窄屏当前课在目录前，以及主线 17 课加选修 4 课的解锁。任何浏览器测试都不应覆盖用户现有的 `learning-progress.json`。

## 命名

“灵枢”取“智能体中枢、路由枢纽、轻量调度核心”之意。完整中文名暂定为：

```text
灵枢 AI 助手课（Kiniu）
```
