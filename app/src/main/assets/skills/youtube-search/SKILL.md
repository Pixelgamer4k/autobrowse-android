---
name: youtube-search
description: Search YouTube reliably. ALWAYS use browser_search — never type in YouTube's search box.
version: 2.0.0
---

# YouTube Search (CRITICAL)

YouTube's search box is **contenteditable** — `browser_type` and `browser_fill` **FAIL** on Android WebView.

## Correct workflow (3 steps)

```
1. browser_search(site="youtube", query="mr beast")
2. browser_wait(milliseconds=2500)
3. browser_snapshot()
```

## Verify success

- URL contains `search_query=` or `results`
- Snapshot text includes video titles, channel names, or "MrBeast" / query terms
- If results visible → **STOP** and tell the user. Do NOT keep clicking.

## Wrong approach (never do this)

- browser_navigate("youtube.com") then browser_type in search box ❌
- Clicking random @e refs hoping to find search ❌
- More than 5 tool calls for a simple search ❌

## Examples

| User says | Do this |
|-----------|---------|
| "search mr beast on youtube" | browser_search(site="youtube", query="mr beast") |
| "open youtube and find pewdiepie" | browser_search(site="youtube", query="pewdiepie") |
| "youtube mr beast latest video" | browser_search(site="youtube", query="mr beast latest") |

## Fallback

If no results in snapshot after search, browser_wait(3000) and browser_snapshot again once.
If still empty, try browser_search with simplified query (fewer words).