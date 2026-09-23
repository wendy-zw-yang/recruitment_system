# Codex 桌面端 Windows 沙箱 helper 故障报告

## 环境
- 平台：Windows（具体版本未确认）
- 应用：Codex 桌面端（openai/codex）
- 用户场景：本地项目开发，需要执行 shell 命令和写入文件

## 故障现象

所有需要启动子进程的工具**全部失败**，错误相同：

**`exec_command`（PowerShell / cmd / bash）**
```
CreateProcess failed: Rejected("Failed to create unified exec process: 
helper_unknown_error: setup refresh had errors")
```

**`mcp__node_repl__js`（Node 内核）**
```
trusted Node process exited unexpectedly; kernel reset, rerun your request

node_repl diagnostics: {"kernel_pid":<PID>,"kernel_status":"exited(code=1)",
"kernel_stderr_tail":"windows sandbox failed: helper_unknown_error: setup 
refresh had errors","reason":"stdout_eof","stream_error":null}
```

底层错误一致：`windows sandbox failed: helper_unknown_error: setup refresh had errors`

## 已尝试的修复手段（均失败）

1. 重启 Codex 桌面应用 → 失败
2. 任务管理器结束所有 `codex*` 进程 → 失败
3. 重新打开 Codex 加载项目 → 失败
4. 确认 Codex 已更新到最新版本 → 失败
5. **重启 Windows 系统**（完整 OS 重启）→ 失败
6. 尝试不同 shell（PowerShell / cmd / bash）→ 全部报同样错误
7. 尝试 `require_escalated` 提权 → 触发自动审批系统内部错误（`unknown model 'codex-auto-review'`），命令未执行
8. 重置 node_repl 内核（`js_reset`）→ 内核仍然无法启动

## 关键特征

- **跨层级持续**：横跨 Codex 进程重启、Codex 全杀、Windows OS 重启三个层级仍未恢复，说明问题不在会话级缓存
- **通道统一**：PowerShell、cmd、bash、Node 内核所有路径都报同一个底层错误，说明是 Codex 的 Windows 沙箱 helper 这个共享组件故障
- **错误信号**：`helper_unknown_error: setup refresh had errors` 中的 "setup refresh" 暗示是 helper 初始化阶段就失败了，不是运行时崩溃
- **无法绕过**：所有文件写入工具（`exec_command`、`node_repl`）都依赖这个 helper，没有可用的旁路

## 临时折中方案

通过对话口述文件 markdown 内容，用户手动复制粘贴到 VSCode / 记事本保存。能继续推进工作但失去了自动化能力（写日志、改代码、跑测试都不行）。

## 怀疑方向（待诊断）

1. **Windows 安全软件拦截**：360 / 火绒 / 卡巴斯基 / Windows Defender 的"受控文件夹访问"可能拦截 Codex 创建子进程
2. **Codex 安装损坏**：helper 的 dll / 资源文件损坏，重装可能修复
3. **Windows 权限问题**：helper 需要某些系统权限才能启动（如 SeAssignPrimaryTokenPrivilege 等）
4. **Codex 版本 bug**：当前版本在 Windows 上的 helper 存在已知问题，需要升级 / 降级
5. **公司 EDR / DLP 策略**：企业环境下的终端安全软件会主动拦截 sandbox 类工具

## 我作为 AI 能做什么 / 不能做什么

- ✅ 给出诊断方向
- ✅ 通过对话口述文件内容
- ❌ 无法读取 Codex 日志（`%APPDATA%\Codex\logs\`）—— shell 全挂，读不到
- ❌ 无法执行任何 `Get-*`、`Get-ChildItem`、`Test-Path` 等只读命令
- ❌ 无法写入任何文件

## 给诊断 AI 的关键问题

1. `helper_unknown_error: setup refresh had errors` 这个具体错误字符串，在 Codex 源码 / issue tracker 里能搜到吗？有没有已知修复？
2. Codex 在 Windows 上是否依赖某个特定的 Windows 功能（如 Windows Sandbox、Hyper-V、WSL2）？如果依赖项被禁用，helper 会报这个错吗？
3. Codex 是否有命令行启动参数可以绕过 sandbox helper（例如 `--no-sandbox`、`--legacy-exec` 之类）？
4. 如果完全无解，有没有"以纯聊天模式使用 Codex"的方法 —— 即只保留对话能力，关闭所有工具调用？

---

复制过去应该能让另一个 AI 完整理解情况。