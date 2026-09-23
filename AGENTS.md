# AGENTS.md — AI 协作规范

> 本文件约束**所有**读写本项目目录的 AI Agent（Codex、Cursor、Claude Code、Aider、opencode 等）。请在开始任何文件操作前先通读本文。

## 1. 日志存放

- 每日开发日志统一写入 `docs/dev-logs/YYYY-MM-DD.md`
- **禁止**在项目根或子目录创建 `.codex/`、`.logs/` 等工具专属目录存放日志
- 旧位置如已有 `.codex/*.md` 日志，须迁移至 `docs/dev-logs/` 后再删除 `.codex/`

## 2. 日志命名

- 文件名：`YYYY-MM-DD.md`（年-月-日）
- 单日文件**仅可追加**，不可覆盖既有内容
- 跨日工作开新一日文件，旧文件不再修改

## 3. 日志内容结构

- 标题：`# Codex 日志 · YYYY-MM-DD（周X）`
- 元数据：项目名 / 当前阶段 / 今日要点
- 推荐章节：今日要点 / 文件变动 / 构思进度 / 待办 / 沙箱与权限

## 4. 署名规范（强制）

- 每次写入或修改日志，**必须在文末追加一行**：
  `> 最后编辑：<AI 名称> @ <ISO 时间>`
- AI 名称使用模型标识（如 `MiniMax-M3`、`GPT-4`、`Claude-3.5-Sonnet`）
- 不覆盖历史署名，追加即可；多人/多次协作可追溯

## 5. 其他注意

- 项目根的 `.codex/` 目录若存在，应先迁移其日志到 `docs/dev-logs/` 再删除
- Codex 沙箱异常时，可检查 `C:\Users\wendy\.codex\.sandbox\setup_error.json`
- 修改本规范（AGENTS.md）需明确说明改动理由，并在 commit message 中体现

---

> 最后编辑：MiniMax-M3 @ 2026-09-23T14:30+08:00