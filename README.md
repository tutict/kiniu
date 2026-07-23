# 灵枢智能体容器

灵枢智能体容器是一个面向本地桌面工作流的轻量智能体编排项目。它把对话、任务流、智能体配置和会话回放集中到一个统一界面里，用清晰的前端状态和可控的后端接口完成多智能体协作。

## 项目定位

- **对话入口**：用户从主对话界面发起需求，由容器选择合适的智能体和下一步动作。
- **任务流编排**：通过节点、动作、标记和亲和度变化描述可复用的智能体流程。
- **智能体管理**：维护智能体身份、角色、系统提示词、工作区、目标和调度参数。
- **会话回放**：导出会话、查看分支、沙盘推演和 AI 调用路径，便于复盘和调试。
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

## AI 工程课程 3.0

学习中心使用 `kiniu-back/data/learning-catalog.json` 中的静态版本 3 目录，包含 8 个模块、20 个任务。主线覆盖 HTTP/JSON、模型输出契约、Prompt 与 Context、Workflow/Agent、工具、RAG、评测、安全、MCP/A2A、可观测、灰度发布和综合架构。

- 任务由 `prerequisiteTaskIds` 组成显式依赖图；完成全部前置任务后解锁。
- 每项任务包含中文讲义、交付物、确定性检查和 1–3 个官方参考链接。
- `document` 任务在工作区直接编辑，`import` 任务可导入声明的 JSON、Markdown 或文本文件。
- 单文件上限 100 KB，一次提交总量上限 500 KB；导入实验要求保留 `source`、`capturedAt`、`requestId` 或等价来源字段。
- 确定性检查只验证提交工件的结构和完整性，不证明外部 trace 或模型响应的真实性。
- 目录、进度、检查和 Agent 发布只使用本机访问令牌；仅 Mentor Feedback 请求携带模型 provider 配置。

完整验收：

```powershell
cd kiniu-back
mvn test

cd ..\kiniu-front\nuxt-app
npm run build
```

## 命名

“灵枢”取“智能体中枢、路由枢纽、轻量调度核心”之意。完整中文名暂定为：

```text
灵枢智能体容器
```
