---
name: autobrowse
description: Self-improving browser automation for Android. Use for multi-step web tasks, form filling, research, and data extraction.
version: 1.0.0
---

# Autobrowse Skill

Hermes-inspired browser automation optimized for the Android hybrid browser.

## When to use

- Multi-step browsing tasks (search, navigate, extract, summarize)
- Form filling and interactive pages
- Research across multiple tabs
- Tasks that benefit from snapshot refs (`@e0`, `@e1`, …)

## Workflow

1. **Plan** — Use `todo_write` for tasks with 3+ steps.
2. **Snapshot** — Call `browser_snapshot` with `interactive=true` to get refs before clicking.
3. **Act** — Prefer ref-based `browser_click` / `browser_type`; use `browser_click_xy` for canvas/custom UIs.
4. **Verify** — Re-snapshot after navigation or form submit.
5. **Vision** — Use `browser_vision` when page layout is unclear or CAPTCHA-like.
6. **Tabs** — Use `browser_tab_open` / `browser_tab_switch` for parallel research; `delegate_task` for independent subtasks.
7. **Learn** — Call `reflect` after difficult tasks; use `skill_manage` to save reusable workflows.

## Pitfalls

- CSS selectors break on SPAs — always prefer snapshot refs.
- Scroll before clicking off-screen elements.
- Call `browser_snapshot` before `extract_data` when page context is stale.
- Local models: use `browser_vision` text output; cloud models get screenshot images.

## Verification

- URL changed as expected
- Target text/data visible in next snapshot
- No error dialogs (`browser_console` to check)