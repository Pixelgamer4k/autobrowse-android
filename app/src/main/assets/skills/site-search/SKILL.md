---
name: site-search
description: Universal site search playbook — direct URL search beats typing every time.
version: 2.0.0
---

# Site Search Master Playbook

## Golden rule

**browser_search > browser_type** for ALL search tasks on major sites.

## Supported sites

| Site | browser_search site param |
|------|---------------------------|
| YouTube | `youtube` |
| Google | `google` |
| Bing | `bing` |
| DuckDuckGo | `duckduckgo` |
| Reddit | `reddit` |
| Amazon | `amazon` |
| Twitter/X | `twitter` |

## Standard 3-step pattern

```
browser_search(site="SITE", query="QUERY")
browser_wait(2000)
browser_snapshot()
```

## Stop conditions

- Results visible in snapshot → task done, summarize for user
- URL shows search parameters → likely succeeded
- Do NOT iterate 10+ times on simple searches

## Training examples

**"open youtube and search mr beast"**
→ browser_search(site="youtube", query="mr beast") → wait → snapshot → done

**"find python tutorials on google"**
→ browser_search(site="google", query="python tutorials") → wait → snapshot → done

**"search cats on reddit"**
→ browser_search(site="reddit", query="cats") → wait → snapshot → done