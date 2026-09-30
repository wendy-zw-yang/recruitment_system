content = '''# 用例图

> 项目：AI 集成的智能招聘系统
> 课程：计算机项目综合开发创新实践
> 文档版本：v0.1
> 编制日期：2026-09-26
> 编制依据：docs/需求分析/功能性需求分析.md v1.1、docs/WBS/WBS.md v0.1
> 图表工具：PlantUML（VSCode / IDEA 插件 / plantuml.com 在线渲染）

---

## 修订记录

| 版本 | 日期 | 变更说明 |
|---|---|---|
| v0.1 | 2026-09-26 | 初稿：需求用例图（总览）—— 22 个核心用例 / 4 个 actor |

---

## 说明

- 使用 PlantUML 描述用例图，每个图表为独立的 plantuml 代码块，可单独渲染
- 图表层级：
  - **总览级**：跨 3 角色 + AI 服务的全部用例（本节）
  - **角色级**：候选人 / HR / 管理员 各一张详细用例图
  - **专题级**：消息中心 / 投递状态机 / AI 集成 / 撤回流程 等

---

## 1. 需求用例图（总览级）

> 4 个 actor（3 角色 + AI 服务）× 22 个核心用例。展示系统的完整用例集合与初步角色划分。

`plantuml
@startuml
left to right direction
title AI 集成的智能招聘系统 - 需求用例图（总览）

actor \"求职者\" as C
actor \"HR\" as H
actor \"管理员\" as A
actor \"AI 服务 (LLM)\" as AI

rectangle \"AI 集成的智能招聘系统\" {
    usecase \"注册/登录\" as UC_Reg
    usecase \"维护简历\" as UC_Resume
    usecase \"浏览/搜索职位\" as UC_BrowseJob
    usecase \"收藏职位\" as UC_FavJob
    usecase \"投递职位\" as UC_Apply
    usecase \"撤回投递\" as UC_Withdraw
    usecase \"我的（投递/收藏）\" as UC_MyC
    usecase \"账号设置\" as UC_Account
    usecase \"消息中心\" as UC_Msg
    usecase \"AI 智能客服\" as UC_AIChat

    usecase \"公司信息维护\" as UC_Company
    usecase \"发布/管理职位\" as UC_ManageJob
    usecase \"查看投递列表\" as UC_AppList
    usecase \"推进投递状态\" as UC_Status
    usecase \"HR 备注\" as UC_HRNote
    usecase \"候选人搜索\" as UC_CandSearch

    usecase \"用户管理\" as UC_UserMgmt
    usecase \"职位审核\" as UC_JobAudit
    usecase \"公司审核\" as UC_CompanyAudit
    usecase \"简历审核\" as UC_ResumeAudit
    usecase \"字典维护\" as UC_Dict
}

C --> UC_Reg
C --> UC_Resume
C --> UC_BrowseJob
C --> UC_FavJob
C --> UC_Apply
C --> UC_Withdraw
C --> UC_MyC
C --> UC_Account
C --> UC_Msg
C --> UC_AIChat

H --> UC_Reg
H --> UC_Company
H --> UC_ManageJob
H --> UC_AppList
H --> UC_Status
H --> UC_HRNote
H --> UC_CandSearch
H --> UC_Account
H --> UC_Msg

A --> UC_UserMgmt
A --> UC_JobAudit
A --> UC_CompanyAudit
A --> UC_ResumeAudit
A --> UC_Dict

AI --> UC_Resume : AI-1 简历解析
AI --> UC_Apply : AI-2 匹配评分
AI --> UC_ManageJob : AI-3 JD 生成
AI --> UC_AIChat : AI-4 智能客服

@enduml
`

---

## 等待审阅

- [ ] 用户确认总览图的 actor 划分 / 用例命名 / 关联关系
- [ ] 通过后展开后续图表（角色级 + 专题级），预计 v0.2 完成

> 本图仅含总览级 1 张，作为先行审阅样本；其余图表按 v0.2 + 陆续加入。
'''

path = r'D:\workspace\J2EE\RecruitmentSystem\docs\设计\用例图.md'
with open(path, 'w', encoding='utf-8') as f:
    f.write(content)
print('OK:', path)
print('size:', len(content), 'chars')
