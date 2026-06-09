---
name: captcha-handling
description: Detect and hand off CAPTCHA/bot challenges (reCAPTCHA, hCaptcha, Turnstile) to the user.
version: 1.0.0
category: bundled
triggers: captcha,recaptcha,hcaptcha,turnstile,cloudflare,verify
---

# CAPTCHA Handling

CAPTCHAs cannot be solved by automation. The user must complete them in the browser window.

## Detection

1. `browser_detect_captcha` — structured check for reCAPTCHA, hCaptcha, Cloudflare Turnstile, generic blocks
2. `browser_snapshot` also appends `[CAPTCHA]` when a challenge is visible

## Handoff flow

1. **Stop** — do not call `browser_click`, `browser_type`, or `browser_dismiss_overlays` on the challenge
2. **Tell the user** — "Complete the CAPTCHA in the browser window, then say continue"
3. **Wait** — `browser_wait_for_captcha_clear` (polls up to 120s)
4. **Verify** — `browser_snapshot` and confirm normal page content
5. **Resume** the original task

## Providers

| Provider | Signals |
|----------|---------|
| reCAPTCHA | `.g-recaptcha`, `google.com/recaptcha` iframe, "not a robot" |
| hCaptcha | `.h-captcha`, `hcaptcha.com` iframe |
| Turnstile | `.cf-turnstile`, `challenges.cloudflare.com` |
| Cloudflare | "Checking your browser", "Just a moment" |
| Google block | `/sorry/`, unusual traffic |

## See also

Load `references/PLAYBOOK.md` for failure recovery when the agent loops on a blocked page.