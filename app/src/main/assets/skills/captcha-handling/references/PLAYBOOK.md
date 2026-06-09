# CAPTCHA Playbook

## Fast path

```
browser_detect_captcha
→ (if userActionRequired) tell user to solve in browser window
→ browser_wait_for_captcha_clear
→ browser_snapshot
→ continue task
```

## Failure recovery

### Agent keeps clicking
- Run `browser_detect_captcha` first
- Do **not** use `browser_dismiss_overlays` — it skips CAPTCHA zones but the agent should pause entirely

### CAPTCHA iframe blank / won't load
- User may need to tap the browser window directly (WebView supports touch)
- Third-party cookies are enabled for challenge iframes
- Reload only if the user asks: `browser_reload` then hand off again

### Stuck after user says "done"
- `browser_wait_for_captcha_clear` with `timeout_ms: 30000`
- If still blocked, `browser_snapshot` and describe what you see to the user

### Google "unusual traffic"
- Cannot bypass — user completes challenge or try again later
- Summarize partial progress and stop looping