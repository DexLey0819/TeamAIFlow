# TeamFlowAI 智能项目小组协作与 AI 项目管理系统

本项目是一个智能项目小组协作与 AI 项目管理系统，提供完整的前后端分离架构。

## 项目结构

```text
TeamFlowAI/
├── database/teamflow_ai.sql
├── teamflow-ai-backend/
└── teamflow-ai-frontend/
```

## 技术栈

后端：Java 17、Spring Boot 3、Spring Security、JWT、MyBatis-Plus、MySQL、Knife4j。

前端：Vue 3、Vite、Element Plus、Pinia、Vue Router、Axios、ECharts。

## 准备环境

- Java 17
- Maven 3.8+
- Node.js 18+
- MySQL 8+

## 本地验证

首次运行前，请先安装前端依赖：

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm ci
```

在项目根目录运行：

```bash
cd /Users/dexley/Documents/TeamFlowAI
bash scripts/verify.sh
```

该命令会执行：

- 前端静态功能检查：加入项目重定向、成员/申请权限边界、个人信息页、项目工作区与章节编辑器。
- 前端生产构建：`npm run build`。
- 后端测试：如果本机安装了 Maven，会执行 `mvn test`；如果没有 Maven，会提示跳过后端测试。

也可以分别运行：

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm run check
npm run build

cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend
mvn test
```

后端本地测试需要 Java 17 和 Maven 3.8+。CI 会在标准 runner 上安装 Java 17 并运行后端测试。

## CI

GitHub Actions 工作流位于：

```text
.github/workflows/ci.yml
```

CI 会在 push 到 main/master、pull request 和手动触发时运行：

- Node.js 20：`npm ci`、`npm run check`、`npm run build`。
- Java 17：`mvn test`。

## 使用 IntelliJ IDEA 导入与运行

建议直接使用 **IntelliJ IDEA** 打开项目根目录 `TeamFlowAI`，以便同时查看和开发前端与后端代码：

### 1. 导入整个项目
1. 启动 IntelliJ IDEA，在欢迎界面点击 **Open** (打开)。
2. 选择当前项目的根目录 `TeamFlowAI`，点击确定。
3. 如果系统弹出提示，请选择 **Trust Project** (信任该项目)。

### 2. 配置后端 Maven 模块
1. 项目加载后，在右侧工具栏中点击 **Maven** 侧边栏（如未显示，可点击 `View -> Tool Windows -> Maven` 调出）。
2. 点击 Maven 工具栏左上角的 **`+` (Add Maven Projects)** 按钮。
3. 选择子目录 `teamflow-ai-backend/pom.xml` 文件，点击确定。
4. IDEA 将自动识别并下载后端 Maven 依赖包，请等待导入进度完成。

### 3. 配置 JDK 17
1. 点击菜单栏 `File -> Project Structure...` (快捷键为 `Cmd + ;` 或 `Ctrl + Alt + Shift + S`)。
2. 在 **Project** 标签页中，确认 **SDK** 配置为 **Java 17**。如果尚未安装，点击 `Add SDK -> Download JDK...` 安装。
3. 点击右下角 **Apply** 保存。

### 4. 运行后端服务
1. 在项目目录树中展开：`teamflow-ai-backend -> src -> main -> java -> com.example.teamflow`。
2. 双击打开主引导类 [TeamflowAiApplication.java](file:///Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend/src/main/java/com/example/teamflow/TeamflowAiApplication.java)。
3. 点击 `main` 方法左侧的 **绿色三角播放按钮 (Run)**，选择 **Run 'TeamflowAiApplication'** 即可启动后端服务。

### 5. 启动前端服务
1. 点击 IDEA 底部工具栏的 **Terminal** 终端标签页。
2. 依次运行以下命令安装依赖并运行前端开发环境：
   ```bash
   cd teamflow-ai-frontend
   npm install
   npm run dev
   ```

## 初始化数据库

```bash
mysql -uroot -p < /Users/dexley/Documents/TeamFlowAI/database/teamflow_ai.sql
```

如果你的 MySQL 密码不是 `123456`，请修改：

```text
/Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend/src/main/resources/application.yml
```

或使用环境变量：

```bash
export MYSQL_USERNAME=root
export MYSQL_PASSWORD=你的密码
```

## 启动后端

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-backend
mvn spring-boot:run
```

后端默认端口：

```text
http://127.0.0.1:8080
```

Knife4j 接口文档：

```text
http://127.0.0.1:8080/doc.html
```

## 启动前端

```bash
cd /Users/dexley/Documents/TeamFlowAI/teamflow-ai-frontend
npm install
npm run dev
```

前端默认地址：

```text
http://127.0.0.1:5173
```

## 测试账号

初始密码均为：

```text
123456
```

| 用户名 | 角色 | 说明 |
| --- | --- | --- |
| pm | USER | 项目经理 |
| pd | USER | 产品经理 |
| frontend | USER | 前端成员 |
| backend | USER | 后端成员 |
| qa | USER | 测试成员 |
| newmember | USER | 待审核申请人 |
| applicant | USER | 新申请演示账号 |
| admin | ADMIN | 管理员 |

## AI 配置

默认不配置 API Key 时，系统会使用本地演示生成逻辑，保证课程答辩可以离线演示。

如需接入真实大模型：

```bash
export AI_API_KEY=你的API_KEY
export AI_API_URL=https://api.deepseek.com/chat/completions
export AI_MODEL=deepseek-chat
```

## 推荐演示流程

1. 使用 `pm / 123456` 登录。
2. 进入项目列表，打开示例项目。
3. 查看成员、任务完成率和项目概览。
4. 进入任务看板，流转任务状态。
5. 提交一条进度记录。
6. 上传一个项目文档。
7. 进入 AI 分析页，生成周报、风险分析和总结报告。

## 加入项目组演示流程

1. 打开加入链接 `http://127.0.0.1:5173/join/TF-DEMO-001`。
2. 点击登录，使用 `applicant / 123456` 登录并回到加入页。
3. 选择可申请角色，提交加入申请。
4. 使用 `pm / 123456` 登录，进入示例项目的“成员与申请”页面审核通过。

说明：`newmember / 123456` 在初始化数据中已经有一条待审核申请，适合演示项目经理审核；`applicant / 123456` 是未加入、未申请过项目的干净账号，适合演示从加入链接提交新申请。

## 常见问题

后端连接数据库失败：

- 确认 MySQL 已启动。
- 确认已导入 `database/teamflow_ai.sql`。
- 确认 `application.yml` 或环境变量里的用户名密码正确。

前端请求失败：

- 确认后端已运行在 `8080`。
- 确认前端 Vite 代理配置没有被修改。

AI 生成不调用真实接口：

- 确认 `AI_API_KEY` 已设置。
- 未设置时系统会自动使用本地演示生成，这是预期行为。
