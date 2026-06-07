---
name: google-search
description: Search Google using direct search URLs via browser_search.
version: 2.0.0
---

# Google Search

## Workflow

1. `browser_search(site="google", query="your query")`
2. `browser_wait(2000)`
3. `browser_snapshot()` — verify result headings/snippets appear

## Verify

URL contains `google.com/search?q=`
Snapshot shows result titles matching query

## Never

Type into Google search box manually — use browser_search instead.