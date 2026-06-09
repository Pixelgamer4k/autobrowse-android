---
name: captcha-handling
description: Prevent and auto-solve CAPTCHAs on authorized sites via stealth + CapSolver/2Captcha.
version: 2.0.0
category: bundled
triggers: captcha,recaptcha,hcaptcha,turnstile,cloudflare,verify
---

# CAPTCHA Handling (authorized sites only)

## Prevention (before CAPTCHA triggers)

1. Android Chrome fingerprint + stealth injection (default in WebView)
2. Third-party cookies enabled for challenge iframes
3. Natural touch via browser window (user can tap); agent uses @eN refs not blind coordinates
4. Optional residential proxy URL in Settings → CAPTCHA Solver

## Detection

`browser_detect_captcha` — types, sitekeys, blocking status

## Solve (authorized domains only)

Configure in **Settings → CAPTCHA Solver**:
- Enable solver
- CapSolver or 2Captcha API key
- **Authorized domains** allowlist (comma-separated, e.g. `staging.myapp.com,amazon.com`)

```
browser_detect_captcha
→ browser_solve_captcha
→ browser_snapshot (verify)
→ continue task
```

## Restrictions

- **Never** solve on domains not in the user's allowlist
- No bypass on sites the user has not explicitly authorized
- Fallback: `browser_wait_for_captcha_clear` if solver disabled

## Supported types

| Type | Injection |
|------|-----------|
| reCAPTCHA v2 | `#g-recaptcha-response` + data-callback |
| hCaptcha | `h-captcha-response` + callback |
| Turnstile | `cf-turnstile-response` + callback |