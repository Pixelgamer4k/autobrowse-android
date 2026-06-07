---
name: job-search
description: Specialized playbook for job search tasks using browser_search and snapshot verification.
version: 1.0.0
category: bundled
triggers: job
---

# job search

## Execution pattern
1. browser_search with the correct site parameter
2. browser_wait(2000)
3. browser_snapshot — verify results in pageText
4. Interact via @eN refs only if needed
5. STOP when goal achieved

## See also
Load training corpus gold trajectories for this domain in the agent prompt.
