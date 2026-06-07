# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.


# Domain Playbook: search

        ## Overview
        Site key: general
        Primary tools: browser_search, browser_wait, browser_snapshot

        ## Step-by-step execution
        1. Parse user intent and extract search query or target URL.
        2. Use browser_search with site="general" when applicable — NEVER type in search boxes.
        3. browser_wait(2000) for JS rendering.
        4. browser_snapshot and verify pageText contains expected keywords.
        5. If interaction needed, use @eN refs from snapshot only.
        6. STOP when verification passes — do not loop unnecessarily.

        ## Success criteria
        - URL contains search parameters OR snapshot shows result titles.
        - Task completes in ≤5 tool calls for simple search tasks.

        ## Common failures on search
        - Typing into React/contenteditable search boxes (use browser_search).
        - Snapshot before load completes (always wait first).
        - Clicking without fresh snapshot after navigation.

        ## Extended examples
        - Example task 1: search 'machine learning tutorials' on general
- Example task 2: search 'best pizza recipe' on general
- Example task 3: search 'kotlin coroutines guide' on general
- Example task 4: search 'android webview automation' on general
- Example task 5: search 'climate change 2026' on general
- Example task 6: search 'open source llm models' on general
- Example task 7: search 'travel to tokyo' on general
- Example task 8: search 'python data science' on general
- Example task 9: search 'home workout plan' on general
- Example task 10: search 'electric cars review' on general
- Example task 11: search 'javascript frameworks' on general
- Example task 12: search 'meditation benefits' on general

        ## Research depth notes
        For multi-page search workflows, use todo_write to plan steps.
        Combine web_fetch for article bodies with browser_search for discovery.
        Use run_parallel_tasks when comparing multiple search listings or sources.
