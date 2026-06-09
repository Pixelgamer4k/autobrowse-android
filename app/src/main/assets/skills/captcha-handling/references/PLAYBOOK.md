# CAPTCHA Playbook

## Fast path (authorized site)

```
browser_detect_captcha
→ browser_solve_captcha
→ browser_snapshot
→ continue
```

## Settings checklist

1. CAPTCHA Solver → **Enabled**
2. Provider: CapSolver or 2Captcha
3. API key set
4. Authorized domains includes current site hostname

## Failure recovery

### solver not configured
Open Settings, add API key + allowlist, retry `browser_solve_captcha`.

### host not authorized
Add domain to allowlist — automated solving is blocked by design.

### sitekey missing
`browser_wait(2000)` → `browser_detect_captcha` again. Widget may still be loading.

### token injected but page stuck
`browser_snapshot` → check if form submit needed → `browser_click` on submit button (not CAPTCHA widget).

### Repeated triggers
Enable residential proxy in Settings; reduce automation speed with `browser_wait` between actions.