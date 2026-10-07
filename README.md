# 汇聘 · AI 集成的智能招聘系统

> **项目代号**：汇聘（RecruitmentSystem） 
> **课程**：计算机项目综合开发创新实践  
> **当前版本**：v0.7.1（2026-10-06）  
> **文档版本**：v1.0  
> **编制日期**：2026-10-06  

一个面向求职者 / HR / 管理员三方的智能招聘系统，覆盖简历管理、职位发布、投递跟踪、AI 评分、AI 客服、消息中心等完整招聘流程。后端基于 Spring Boot 3.5 + MyBatis-Plus 3.5，前端基于 Vue 3 + Element Plus，AI 集成 OpenAI 兼容协议（MiniMax）。

---

## 1. 项目简介

### 1.1 目标

打造一个以AI集成为特色的招聘平台原型，能够演示从账号注册、简历上传、职位浏览、投递跟踪、HR 推进状态、AI 评分、消息沟通、客服问答到管理员审核的**端到端流程**。

### 1.2 三大角色

| 角色 | 定位 | 核心能力 |
|---|---|---|
| **求职者**（CANDIDATE） | 公开注册 | 简历上传 + AI 解析、职位搜索与投递、AI 评分（被动）、消息咨询 HR、AI 客服 |
| **HR** | 公开注册（提交公司信息） | 发布 / 编辑 / 下线职位、简历收件箱 + AI 评分查看、推进投递状态、消息联系候选人、AI 客服 |
| **管理员**（ADMIN） | 系统预置 | 用户审核、公司审核、字典维护（行业 / 城市 / 技能建议） |

### 1.3 核心特性

- **AI 全链路集成**：简历解析（AI-1）、JD 润色（AI-3）、简历 ↔ JD 评分（AI-2）、智能客服（AI-4）四大必做功能齐备
- **完整状态机**：8 状态投递跟踪（`PENDING_REVIEW → VIEWED_BY_HR → RESUME_PASSED → INTERVIEWING → OFFERED → HIRED` 或 `REJECTED` / `WITHDRAWN`）
- **站内信（消息中心）**：HR ↔ 候选人二元组唯一会话 + SSE 实时推送 + 撤回通知系统消息
- **三端 UI 完整**：候选人 / HR / 管理员首页、详情、表单、列表全实现
- **182 个单元测试**：核心业务 100% 覆盖

---

## 2. 功能概览

### 2.1 求职者端（CANDIDATE）

| 模块 | 主要功能 |
|---|---|
| 注册 / 登录 | 邮箱 + 密码（BCrypt），或邮箱 + 验证码登录 |
| 我的简历 | 上传 PDF / Word（≤20MB）→ AI-1 自动解析 → 表单编辑 → 保存；支持归档 / 删除重传 |
| 职位浏览 | 列表 + 分页 + 行业 / 城市 / 关键词 / 收藏过滤 |
| 职位详情 | JD + 收藏 + **咨询 HR**（v0.7.1 新增）+ 投递 |
| 我的投递 | 列表 + 状态时间线 + 撤回；候选人侧不显示 AI 评分 |
| 消息中心 | 与 HR 一对一聊天 + 系统通知 + 二元组唯一窗口 |
| AI 客服 | 全站悬浮按钮，按使用手册关键词检索 + LLM 流式回答 |
| 个人中心 | 账号安全 / 个人资料 / 求职偏好 / 公司信息 |

### 2.2 HR 端（HR）

| 模块 | 主要功能 |
|---|---|
| 注册 / 登录 | 邮箱 + 密码，同上；额外提交公司信息（待审核） |
| 职位管理 | 创建 / 编辑 / 发布 / 下线 / 删除；AI-3 一键润色 JD |
| 简历收件箱 | 跨职位聚合列表，按 AI 评分 / 投递时间排序；关键词搜索 |
| 投递详情 | 候选人信息 + AI 评分 + 简历快照 + 状态时间线 + 推进状态 + HR 备注 |
| 消息中心 | 与候选人聊天（要求有投递关系）+ 系统通知 |
| AI 客服 | 全站悬浮按钮（ADMIN 不可见） |
| 首页 | 在线职位数 / 收到简历 / 未读消息（真实数据） |
| 个人中心 | 账号安全 / HR 资料 / 我的公司（审核状态） |

### 2.3 管理员端（ADMIN）

| 模块 | 主要功能 |
|---|---|
| 用户管理 | 用户列表（分角色）/ 启用 / 禁用 / 重置密码 / 改角色 |
| 公司审核 | 待审核公司列表 / 通过 / 拒绝（必填理由，可联动禁用 HR） |
| 字典维护 | 行业（一级 + 二级）/ 城市 / 技能建议池的 CRUD |

---

## 3. 技术栈

### 3.1 后端

| 层 | 技术 | 版本 | 备注 |
|---|---|---|---|
| 语言 | Java | 17 LTS | 与本机一致 |
| 框架 | Spring Boot | 3.5.14 | 本机 .m2 缓存最新 3.x patch |
| 持久层 | MyBatis-Plus | 3.5.7 | spring-boot3-starter，禁字符串拼接 SQL |
| 安全 | spring-security-crypto | 跟随 Boot | 仅 BCrypt 密码哈希，不引完整 Spring Security |
| 数据库驱动 | mysql-connector-j | 跟随 Boot | MySQL 8.0 |
| JWT | jjwt | 0.12.6 | HMAC-SHA，密钥 ≥32 字节 |
| 邮件 | spring-boot-starter-mail | 跟随 Boot | 验证码登录默认打日志，可切换 SMTP |
| 构建 | Maven | 3.9.8 | 本机已装 |

### 3.2 前端

| 层 | 技术 | 版本 |
|---|---|---|
| 框架 | Vue | 3.5.x |
| 构建 | Vite | 8.x |
| 状态管理 | Pinia | 4.x |
| 路由 | Vue Router | 5.x |
| UI 组件 | Element Plus | 2.14.x |
| HTTP | Axios | 1.x |
| 语言 | JavaScript（默认） | 不引 TypeScript |

### 3.3 数据库 / 基础设施

- MySQL 8.0.46（InnoDB / utf8mb4 / utf8mb4_0900_ai_ci）
- 本地文件系统（简历 / 消息附件存储到 `./uploads/{type}/{yyyy-MM}/{uuid}.{ext}`）
- AI 集成：通义千问（默认 `qwen-plus`）或 MiniMax（`MiniMax-M3`）— OpenAI Chat Completions 兼容协议，配置切换
- 简历附件文本抽取：Apache PDFBox + POI（Java 实现），可选用 Python 软依赖（`tools/parse_resume.py`）走 pdfplumber + python-docx

---

## 4. 项目结构

```
RecruitmentSystem/
├── backend/                              # Spring Boot 工程
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/example/recruitmentsystem/
│       │   │   ├── RecruitmentSystemApplication.java
│       │   │   ├── common/               # 注解 / 上下文 / 异常 / 处理器
│       │   │   ├── config/               # JWT / LLM / Web / Password
│       │   │   ├── controller/           # 14 个 REST 控制器
│       │   │   │   ├── admin/            # AdminUser / AdminCompany / DictAdmin
│       │   │   │   ├── ai/               # AiChat (SSE 流式)
│       │   │   │   ├── application/      # Application / HrApplication
│       │   │   │   ├── auth/             # 注册 / 登录 / 验证码
│       │   │   │   ├── job/              # 职位 CRUD + 收藏 + AI-3 润色
│       │   │   │   ├── message/          # Conversation / Message / Sse / Attachment
│       │   │   │   ├── profile/          # 个人资料 / 偏好 / 公司
│       │   │   │   └── resume/           # 简历上传 / 编辑 / 删除
│       │   │   ├── dto/                  # 25+ DTO
│       │   │   ├── entity/               # 20 张表对应的实体
│       │   │   ├── llm/                  # LLM 调用层（client / prompt / service / dto）
│       │   │   ├── mapper/               # 17 个 MyBatis-Plus Mapper
│       │   │   └── service/              # 业务接口 + impl
│       │   └── resources/
│       │       ├── application.yml       # 主配置（提交）
│       │       └── ai/manual.md          # AI-4 客服检索手册
│       └── test/                         # 182 个 JUnit 5 测试
├── frontend/                             # Vue 3 工程
│   ├── package.json
│   ├── vite.config.js                    # /api → http://localhost:8080 代理
│   └── src/
│       ├── api/                          # axios 封装 + 业务模块 API
│       ├── components/                   # 通用组件（如 ChatWidget）
│       ├── layouts/AppLayout.vue         # 顶栏 + nav + 返回按钮 + AI 客服挂载
│       ├── router/index.js               # 路由表（含 meta.roles 权限）
│       ├── stores/                       # Pinia：auth / resume / job / application / aiChat / message
│       └── views/                        # 25 个 .vue 页面
│           ├── auth/                     # 登录 / 注册
│           ├── candidate/                # 候选人端（Home / Browse / Detail / MyApplications / ResumeEdit）
│           ├── hr/                       # HR 端（Home / JobManage / ApplicationInbox / ApplicationDetail）
│           ├── admin/                    # 管理员端（Home / UserAudit / CompanyAudit）
│           └── common/                   # 通用（Profile / Chat / ChatListItem / NotFound / Forbidden / JobListCard）
├── docs/                                 # 项目文档
│   ├── AGENTS.md                         # AI 协作规范
│   ├── 技术约束.md                       # 唯一权威技术栈
│   ├── 需求分析/                         # 功能性 / 非功能性需求
│   ├── 系统设计/                         # 功能模块设计 + 详细设计（§1-§8）+ WBS + 用例图
│   ├── DataBase/
│   │   ├── schema.sql                    # 20 张表 DDL（**权威**）
│   │   ├── 数据库设计.md
│   │   └── migration-2026-10-06-add-ai-chat-message.sql  # 增量迁移
│   ├── dev-logs/                         # 每日开发日志（AGENTS.md §6 强制要求）
│   ├── 使用手册.md                       # 30+ 条用户视角 FAQ
│   └── 阶段性文档/                        # 阶段性模板
├── tools/                                # Python 软依赖（简历解析）
│   ├── parse_resume.py
│   └── requirements.txt
├── AGENTS.md                             # AI 协作规范（必读）
└── README.md                             # 本文件
```

---

## 5. 快速开始

### 5.1 环境要求

| 软件 | 版本 | 备注 |
|---|---|---|
| JDK | 17+ | 推荐 17.0.8 |
| Maven | 3.9+ | 推荐 3.9.8 |
| Node.js | 18+ | 推荐 24.x（含 npm） |
| MySQL | 8.0+ | 推荐 8.0.46，本机服务名 `MySQL80`（手动启动） |

### 5.2 启动步骤

#### 步骤 1：初始化数据库

```bash
# 启动 MySQL 服务（PowerShell 管理员）
net start MySQL80

# 创建数据库
mysql -uroot -p -e "CREATE DATABASE IF NOT EXISTS recruitment_system CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;"

# 导入 schema（含 20 张表）
mysql -uroot -p recruitment_system < docs/DataBase/schema.sql
```

#### 步骤 2：启动后端

```bash
cd backend

# 复制本地配置（含数据库密码 / LLM Key 等敏感信息）
# application-local.yml 应加入 .gitignore
# 示例内容：
# spring:
#   datasource:
#     url: jdbc:mysql://localhost:3306/recruitment_system?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
#     username: root
#     password: <your_password>
# logging:
#   level:
#     com.example.recruitmentsystem: debug
```

```bash
mvn spring-boot:run
# 后端默认启动在 http://localhost:8080
```

#### 步骤 3：启动前端

```bash
cd frontend
npm install
npm run dev
# 前端默认启动在 http://localhost:5173
# Vite 已配置 /api → http://localhost:8080 代理
```

打开浏览器访问 `http://localhost:5173` → 自动跳 `/login` → 注册账号 → 开始体验。

#### 步骤 4：（可选）AI 集成配置

在 `application-local.yml` 或环境变量中设置（任选一种 OpenAI 兼容协议提供方）：

```yaml
llm:
  api-url: <your_llm_api_url>      # OpenAI 兼容协议端点（向你的 LLM 提供方索取）
  api-key: <your_api_key>           # 需替换为你的实际 API Key
  model: <your_model_name>          # 提供方支持的模型标识（如 qwen-plus / gpt-4 等）
```

或环境变量：

```bash
export LLM_API_URL=<your_llm_api_url>
export LLM_API_KEY=<your_api_key>
export LLM_MODEL=<your_model_name>
```

> 任何兼容 OpenAI Chat Completions 协议的服务（通义千问、MiniMax、OpenAI 等）均可通过修改这三个字段接入，代码层不绑死提供方。

---

## 6. 配置说明

### 6.1 后端 application.yml

```yaml
server:
  port: 8080

spring:
  application:
    name: recruitment-system
  profiles:
    active: local  # 加载 application-local.yml
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB

jwt:
  secret: <env: JWT_SECRET>  # ≥32 字符
  expire-hours: 24

llm:
  api-url: <env: LLM_API_URL>
  api-key: <env: LLM_API_KEY>
  model: qwen-plus
  timeout-seconds: 30
  chat-model: <env: LLM_CHAT_MODEL>  # AI-4 客服独立模型
  chat-max-tokens: 600

app:
  upload:
    dir: <env: UPLOAD_DIR>  # 默认 ./uploads
```

### 6.2 环境变量清单

| 变量 | 必填 | 说明 |
|---|---|---|
| `DB_HOST` / `DB_PORT` / `DB_USER` / `DB_PASSWORD` | 是 | 数据库连接 |
| `JWT_SECRET` | 生产 | ≥32 字符强随机；dev 默认 dev 值 |
| `LLM_API_URL` | 视需求 | AI 集成；未配则 LLM 调用报"配置不完整" |
| `LLM_API_KEY` | 视需求 | 同上 |
| `LLM_MODEL` | 否 | 默认 `qwen-plus` |
| `LLM_CHAT_MODEL` | 否 | AI-4 客服独立模型，默认同 `LLM_MODEL` |
| `UPLOAD_DIR` | 否 | 附件根目录，默认 `./uploads` |
| `SERVER_PORT` | 否 | 后端端口，默认 8080 |

### 6.3 前端 vite.config.js

```javascript
server: {
  port: 5173,
  proxy: {
    '/api': { target: 'http://localhost:8080', changeOrigin: true }
  }
}
```

---

## 7. 数据库

### 7.1 表清单（20 张）

| # | 表名 | 用途 | 模块 |
|---|---|---|---|
| 1 | `users` | 用户账号（CANDIDATE / HR / ADMIN） | §1 用户 |
| 2 | `candidate_profile` | 候选人扩展资料 | §1 |
| 3 | `company` | 公司信息 + 审核状态 | §1 / §7 |
| 4 | `dict_industry` | 行业字典（一级 + 二级） | §7 |
| 5 | `dict_city` | 城市字典 | §7 |
| 6 | `dict_skill_suggestion` | 技能建议池 | §7 |
| 7 | `email_code` | 邮箱验证码 | §1 |
| 8 | `resume` | 简历主表（含 AI 解析 JSON） | §2 |
| 9 | `resume_attachment` | 简历附件 | §2 |
| 10 | `job` | 职位 | §3 |
| 11 | `application` | 投递（状态机 + AI 评分） | §4 |
| 12 | `application_status_history` | 状态变更历史 | §4 |
| 13 | `application_note` | HR 内部备注 | §4 |
| 14 | `favorite_job` | 候选人收藏职位 | §3 |
| 15 | `conversation` | 消息会话（二元组唯一） | §5 |
| 16 | `message` | 消息 | §5 |
| 17 | `message_attachment` | 消息附件 | §5 |
| 18 | `ai_call_log` | AI 调用日志 | §6 |
| 18b | `ai_chat_message` | AI-4 客服历史 | §6 |
| 19 | `audit_log` | 管理员操作审计 | §7 |

### 7.2 关键设计

- **主键**：`BIGINT AUTO_INCREMENT`
- **时间**：`DATETIME` + `DEFAULT CURRENT_TIMESTAMP` + `ON UPDATE CURRENT_TIMESTAMP`
- **软删**：每张业务表 `is_deleted TINYINT DEFAULT 0` + MyBatis-Plus `@TableLogic`
- **字符集**：`utf8mb4` / `utf8mb4_0900_ai_ci`
- **引擎**：InnoDB
- **外键**：所有 FK 显式声明（命名 `fk_<table>_<ref>`）
- **唯一键**：`uk_<table>_<col>`；普通索引 `idx_<table>_<col>`

### 7.3 增量迁移

- `docs/DataBase/schema.sql` 权威（`DROP TABLE IF EXISTS` + `CREATE TABLE`，首次初始化使用）
- `docs/DataBase/migration-2026-10-06-add-ai-chat-message.sql` 增量（`CREATE TABLE IF NOT EXISTS`，幂等）
- 新增表时优先增量迁移，不影响已有数据

---

## 8. API 概览

> 完整接口见各 `docs/系统设计/详细设计/*.md`。统一响应包装：`{ code: 200, message: "success", data: ... }`

### 8.1 公开 / 通用

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 求职者注册 |
| POST | `/api/auth/register-hr` | HR 注册（含公司信息） |
| POST | `/api/auth/login` | 密码登录 |
| POST | `/api/auth/send-login-code` | 发送邮箱验证码 |
| POST | `/api/auth/code-login` | 验证码登录 |
| GET / PUT | `/api/profile/me` | 当前用户资料 |
| PUT | `/api/profile/me/password` | 修改密码 |
| GET / PUT | `/api/profile/candidate/preference` | 求职偏好（仅 CANDIDATE） |
| GET / PUT | `/api/profile/company/me` | 公司信息（仅 HR） |
| GET | `/api/jobs` | 职位列表（公开 + 筛选 + 分页） |
| GET | `/api/jobs/{id}` | 职位详情 |

### 8.2 候选人侧

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/resumes/current` | 当前 ACTIVE 简历 |
| POST | `/api/resumes/upload` | 上传 PDF / Word + AI-1 解析 |
| PUT | `/api/resumes/{id}` | 编辑（不触发 AI） |
| POST | `/api/resumes/{id}/archive` | 归档 |
| DELETE | `/api/resumes/{id}` | 软删 + 删附件 |
| GET | `/api/resumes/attachment/{id}/download` | 简历附件下载（HR/候选人） |
| POST | `/api/applications` | 投递（触发 AI-2 异步评分） |
| GET | `/api/applications/mine` | 我的投递列表 |
| GET | `/api/applications/{id}` | 投递详情 |
| POST | `/api/applications/{id}/withdraw` | 撤回（触发系统消息） |
| POST | `/api/jobs/{id}/favorite` | 收藏 / 取消 |

> 消息中心端点见 §8.4（候选人 / HR 共用）。AI-4 智能客服见 §8.5。

### 8.3 HR 侧

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/jobs` | 创建职位（草稿） |
| PUT | `/api/jobs/{id}` | 编辑 |
| POST | `/api/jobs/{id}/publish` / `/offline` / `/delete` | 上线 / 下线 / 删除 |
| GET | `/api/jobs/mine` | HR 自己的职位 |
| POST | `/api/jobs/polish` | AI-3 一键润色 JD |
| POST | `/api/applications/hr/list` | 收件箱 |
| GET | `/api/applications/hr/{id}` | HR 视角详情 |
| POST | `/api/applications/hr/{id}/status` | 推进状态 |
| GET / POST / PUT / DELETE | `/api/applications/hr/{id}/notes` | HR 备注 CRUD |
| GET | `/api/applications/hr/{id}/resume-snapshot` | 投递时简历快照 |

> 消息中心端点见 §8.4（候选人 / HR 共用）。

### 8.4 消息中心（候选人 / HR 共用）

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/conversations` | 创建 / 获取会话（按 peerId） |
| GET | `/api/conversations/mine` | 我的会话列表 |
| GET | `/api/conversations/{id}` | 进入会话（自动已读） |
| GET | `/api/conversations/unread-stats` | 未读数 |
| POST | `/api/messages` | 发消息（文本 / 附件） |
| POST | `/api/messages/conversation/{id}/list` | 消息分页 |
| POST | `/api/messages/attachment/upload` | 附件上传（落盘） |
| GET | `/api/messages/attachment/{id}/download` | 附件下载 |
| GET | `/api/sse/subscribe` | SSE 长连接（新消息 / 已读回执） |

### 8.5 管理员 / AI

| Method | Path | 说明 |
|---|---|---|
| POST | `/api/admin/users/list` | 用户列表 |
| POST | `/api/admin/users/{id}/{enable,disable,reset-password}` | 启用 / 禁用 / 重置密码 |
| PUT | `/api/admin/users/{id}/role` | 修改角色 |
| POST | `/api/admin/companies/list` | 公司列表 |
| POST | `/api/admin/companies/{id}/verify` | 通过 |
| POST | `/api/admin/companies/{id}/reject` | 拒绝（必填理由） |
| POST / PUT / DELETE | `/api/admin/dict/{industries,cities,skill-suggestions}` | 字典 CRUD |
| GET | `/api/ai/chat/stream` | AI-4 智能客服（SSE 流式，仅 CANDIDATE / HR） |
| GET / DELETE | `/api/ai/chat/history` | AI-4 历史 / 清空 |

---

## 9. 测试

### 9.1 后端（JUnit 5 + Mockito）

```bash
cd backend
mvn test                          # 全部 182 个测试
mvn test -Dtest=MessageServiceTest # 单个测试类
mvn test -Dtest='MessageServiceTest#sendMessage_hrSends_persistsAndPushesBothSides' # 单个用例
```

**当前状态**：182 / 182 通过

| 测试类 | 用例数 | 覆盖范围 |
|---|---|---|
| `AiChatServiceTest` | 12 | AI-4 客服流路径 + ADMIN 拒绝 + LLM 失败降级 + saveMessage 失败兜底 |
| `ApplicationServiceTest` | 17 | 投递 + 撤回 + 状态推进 + HR 备注 + AI-2 失败降级 |
| `ConversationServiceTest` | 11 | 二元组唯一 + 参与者鉴权 + 自动已读 + 推送 |
| `MessageServiceTest` | 8 | 发送 + 系统消息 + 附件绑定 |
| `AttachmentServiceTest` | 6 | 上传 + 下载 + 鉴权 + bindToMessage |
| `SseEmitterManagerTest` | 4 | register / pushToUser / 自动 unregister |
| `AuthServiceTest` | 6 | 注册 / 登录 / 验证码 |
| `JobServiceTest` | 24 | 职位 CRUD + AI-3 润色 + 收藏 |
| `ResumeServiceTest` | 10 | 上传 / 解析 / 删除 / 归档 |
| `ProfileServiceTest` | 12 | 个人资料 / 偏好 / 公司信息 |
| `AdminUserServiceTest` | 12 | 用户审核 + 角色管理 |
| `AdminCompanyServiceTest` | 8 | 公司审核 + 联动禁用 |
| `DictServiceTest` | 7 | 字典 CRUD |
| `LlmScoreServiceTest` | 5 | AI-2 评分 |
| `LlmResumeParseServiceTest` | 8 | AI-1 简历解析 |
| `LlmJdServiceTest` | 4 | AI-3 JD 润色 |
| `LlmChatServiceTest` | 13 | AI-4 关键词检索 + topK + buildContext |
| `LlmClientTest` | 3 | 流式 LLM 调用 |
| `LlmClientJsonExtractTest` | 11 | JSON 抽取 |
| `ApplicationTests` | 1 | Spring 上下文加载 |

### 9.2 前端（Vite build）

```bash
cd frontend
npm run build    # 0 新增警告（chunk size 警告是已有的）
```

---

## 10. 文档索引

| 类别 | 路径 | 用途 |
|---|---|---|
| **AI 协作规范** | [`AGENTS.md`](./AGENTS.md) | **必读**（含不启动后端集成测试 / dev-log 单日仅追加 / 不自动 commit 等规则） |
| **技术约束** | [`docs/技术约束.md`](./docs/技术约束.md) | 唯一权威技术栈与 DDL 约定 |
| **需求分析** | [`docs/需求分析/`](./docs/需求分析) | 功能性 + 非功能性需求 |
| **系统设计** | [`docs/系统设计/`](./docs/系统设计) | 功能模块设计（master）+ 各模块详细设计 + WBS + 用例图 |
| **数据库** | [`docs/DataBase/schema.sql`](./docs/DataBase/schema.sql) | 20 张表 DDL（权威） |
| **开发日志** | [`docs/dev-logs/`](./docs/dev-logs) | 每日工作日志（按 AGENTS.md §6 强制） |
| **使用手册** | [`docs/使用手册.md`](./docs/使用手册.md) | 30+ 条用户视角 FAQ |
| **AI handoff** | `opencode-handoff-*.md` | AI 会话交接文档（多份，按时间倒序） |

### 关键模块设计文档

| 模块 | 文档 |
|---|---|
| §1 用户与认证 | [`docs/系统设计/详细设计/用户与认证.md`](./docs/系统设计/详细设计/用户与认证.md) |
| §2 简历 | [`docs/系统设计/详细设计/简历.md`](./docs/系统设计/详细设计/简历.md) |
| §3 职位与公司 | [`docs/系统设计/详细设计/职位与公司.md`](./docs/系统设计/详细设计/职位与公司.md) |
| §4 投递 | [`docs/系统设计/详细设计/投递.md`](./docs/系统设计/详细设计/投递.md) |
| §5 消息中心 | [`docs/系统设计/详细设计/消息中心.md`](./docs/系统设计/详细设计/消息中心.md) |
| §6 AI 集成 | [`docs/系统设计/详细设计/AI集成.md`](./docs/系统设计/详细设计/AI集成.md) |
| §7 管理员 | [`docs/系统设计/详细设计/管理员.md`](./docs/系统设计/详细设计/管理员.md) |
| §8 基础设施 | [`docs/系统设计/详细设计/基础设施.md`](./docs/系统设计/详细设计/基础设施.md) |

---

## 11. 开发规范

### 11.1 AI 协作（参见 [`AGENTS.md`](./AGENTS.md)）

- 写代码前必读相关模块的 `docs/` 设计文档
- 写代码后追加 `docs/dev-logs/YYYY-MM-DD.md`，文末署名 `<AI 名称> @ <ISO 时间>`
- 单日 dev-log 文件**仅追加**不覆盖，历史日志不动
- 不自动 `git commit` / `push`（需用户明示）
- 不主动启动整个 Spring Boot 应用做集成测试（按 AGENTS.md v1.2）
- 不在源代码写 "TODO" / "FIXME" / "AI generated" 等水印

### 11.2 后端

- SQL 优先 ORM（MyBatis-Plus Lambda），复杂查询可用 `@Select` 注解 + 参数化绑定
- Controller → Service → Mapper 分层，禁止 Controller 直调 Mapper
- 跨模块 service 调用避免循环依赖；确需共享时抽取公共 service
- 多表写入 / 状态变更用 `@Transactional`；单表简单写入可省
- 异常统一用 `BusinessException` 包装，禁止吞异常

### 11.3 前端

- Vue 3 Composition API + `<script setup>`，禁止 Options API
- Pinia 状态管理，按模块拆分 store
- API 封装在 `src/api/`，axios 拦截器已统一 Result 解包
- 路由表 `src/router/index.js` 含 `meta.roles` 鉴权
- 颜色 / 间距走 CSS 变量（`--primary` / `--text` / `--line` 等），禁止硬编码

### 11.4 数据库

- 每张业务表加 `is_deleted` 软删字段 + `@TableLogic`
- 索引命名：`uk_` / `idx_` / `fk_`
- 所有字段写 `COMMENT`

---

> **最后编辑**：Opencode @ 2026-10-06T17:45:00+08:00
