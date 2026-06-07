---
name: autobrowse
description: Core browser automation playbook. Search tasks use browser_search, not typing.
version: 2.0.0
---

# Autobrowse Skill

## Priority order for actions

1. **Search** → `browser_search` (never type in search boxes)
2. **Navigate** → `browser_navigate` then `browser_wait`
3. **Interact** → `browser_snapshot` first, then `browser_click` with @eN refs
4. **Verify** → `browser_snapshot` again
5. **Stop** when goal achieved — do not over-loop

## Search tasks (most common failure)

Load `skill_view` for `youtube-search` or `site-search`.
Pattern: search → wait → snapshot → done (3-5 tools max).

## Multi-step tasks

`todo_write` first. Max 8-12 tools for medium tasks.

## Pitfalls

- YouTube/Google search boxes break with browser_type — use browser_search
- Snapshot before page loads = empty results — always browser_wait after navigate
- 15 iterations on a 3-step task = wrong approach, rethink strategy