---
name: handoff
description: Compact the current conversation into a handoff document for the next agent session. Use when handing off work, ending a long session, or the task mentions "handoff", "交接", "hand over", "continue in next session". References artifacts by path — never copies.
---

# Handoff

Compact the current conversation so a fresh agent can continue without replaying
the session. Reference existing artifacts — never paste their content.

## When to use

- Ending a session with unfinished work
- Task mentions "handoff", "交接", "continue later", "next session"
- Context is too large and you need to preserve current state

## What to include

- **Summary**: goal, progress so far, current branch/open files/last action
- **Blockers**: what's preventing progress, what still needs an answer
- **Decisions**: trade-offs made, alternatives rejected, and why
- **Artifact references**: paths/URLs to specs, plans, diffs, issues — never inline

## What NOT to include

- Full files or large code blocks — reference paths
- Secrets (API keys, passwords, tokens, PII) — redact with `<REDACTED>`
- Content already captured in artifacts — the path is enough
- Session chatter irrelevant to the next agent's work

## Output

- **Language:** using Chinese for summary, encoding is utf-8. 
- **Path:** Save to current working directory
- **file name:** `opencode-handoff-YYYY-MM-DD-HHmm.md`

## Agent workflow

1. Collect paths of existing artifacts (specs, plans, PRs, diffs)
2. Summarize remaining state: goal, progress, blockers, open questions
3. Note key decisions and rejected alternatives
4. Add a **Suggested Skills** section listing skills the next session should load
5. Write to  current working directory, report the full path to the user

The handoff is a signpost, not a replay. Keep it simple, under 300 lines.
