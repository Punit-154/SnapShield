# Agent Decision Log & Thought Processes

## Metadata
- name: agent-decision-log
- description: Record of key decisions, reasoning, mistakes, and lessons learned during SMSentry development. Read this to avoid repeating mistakes.

## Thought Process: Security Audit Approach

### How I audited the app
1. **Dispatched 2 research subagents in parallel** — one for security audit, one for product improvements
2. **Categorized findings by severity** — CRITICAL/HIGH/MEDIUM/LOW with CWE IDs
3. **Grouped fixes into phases** — Security first, then features, then polish
4. **Fixed in priority order** — HIGH severity vulns before new features

### Key insight
Always fix ALL instances of a pattern, not just the first one found. When I fixed `contentDescription = null`, I searched across ALL UI files and fixed all 17 instances in one pass.

## Thought Process: Parallel Execution

### Strategy for large tasks
1. **Identify independent work streams** — security fixes, accessibility, pagination can all happen in parallel
2. **Use subagents for long-running tasks** — build verification, security scanning, code changes
3. **Handle rate limits gracefully** — when a subagent hits 429, take over the work yourself
4. **Don't wait for one thing to finish** before starting the next independent task

### Rate limit handling
When subagent `perf_fixer` hit a 429:
- I verified its partial work was committed
- I took over the remaining tasks myself
- I didn't waste time retrying the same model

## Mistakes Made & Lessons Learned

### Mistake 1: DeepCheckSession.kt Multi-Edit Disaster
**What happened**: Tried to edit the prompt-building section of DeepCheckSession.kt with multiple partial edits. Each edit had slight inaccuracies in the target content (CRLF mismatches), and the tool applied "best effort" fixes that broke the code structure. After 3 bad edits, 51 critical lines were deleted.

**Root cause**: The file uses CRLF line endings, but I was specifying LF in target content. The tool's "best effort" matching deleted adjacent code.

**Fix**: `git checkout -- DeepCheckSession.kt` to restore, then applied ONE clean edit.

**Lesson**: 
- For files with complex nesting (try/catch inside buildString inside coroutine), ALWAYS verify the file state before and after edits
- If an edit goes wrong, immediately `git checkout` to restore rather than trying to fix the broken state with more edits
- Use `view_file` to read the EXACT line content including whitespace before editing

### Mistake 2: SQLCipher Wrong Library Name
**What happened**: Used `net.zetetic:android-database-sqlcipher:4.6.1` which doesn't exist. The library was renamed to `net.zetetic:sqlcipher-android` and the API changed (`SupportFactory` → `SupportOpenHelperFactory`, `net.sqlcipher.database` → `net.zetetic.database.sqlcipher`).

**Lesson**: Always `search_web` for the current library name/version before adding dependencies. Libraries get renamed.

### Mistake 3: @aar Suffix Stripping Classes
**What happened**: Added `@aar` to the SQLCipher dependency per a web search suggestion. This stripped the Java classes from the compile classpath, causing "Unresolved reference" errors.

**Fix**: Removed `@aar` suffix.

**Lesson**: Don't add `@aar` unless you know the library bundles all classes in the AAR. Modern libraries with proper POM metadata don't need it.

### Mistake 4: LazyColumn Scope Insertion
**What happened**: Inserted global search results code outside the `LazyColumn` scope. The `item {}` and `items {}` DSL calls only work inside `LazyListScope`, so they got compiler errors.

**Root cause**: The closing brace `}` of the LazyColumn was ambiguous — there were multiple nested `}` on adjacent lines.

**Fix**: Carefully identified which `}` closed the LazyColumn vs the `itemsIndexed` block.

**Lesson**: When inserting into deeply nested Compose code, always view 20+ lines of context to understand the brace structure. Include surrounding unique code in the target content, not just `}`.

## Patterns That Worked Well

### 1. Build → Deploy → Commit → Push Pipeline
```powershell
# One command to do everything
& adb install -r app.apk; git add -A; git commit -m "msg"; git push origin main 2>&1
```
This saves 3 tool calls per cycle.

### 2. Filtered Build Output
```powershell
... 2>&1 | Select-String -Pattern '^e:|BUILD|error:|Unresolved|FAILED' | Select-Object -First 20
```
Shows only errors, not the 100+ lines of task progress.

### 3. Subagent for Research, Self for Code
Research subagents are great for scanning codebases and finding issues. But code changes should be done by the main agent (or a dedicated coder subagent with write tools) to maintain context.

### 4. Verify Zero Remaining Issues
After fixing a class of issues (e.g., null contentDescriptions), always verify with grep:
```powershell
grep -rn "contentDescription = null" app/src/main/java/
```
Zero results = done.

## Decision Framework for Future Agents

### When to use subagents
- ✅ Long-running research (security audits, codebase scanning)
- ✅ Independent code changes in separate files
- ✅ Build/deploy while continuing other work
- ❌ Sequential edits to the same file (context loss)
- ❌ Tasks requiring the current conversation's full context

### When to plan vs just do it
- **Plan**: Architectural changes, new features, multi-file refactors
- **Just do it**: Bug fixes, accessibility fixes, dependency updates, string extractions

### When to ask the user vs decide yourself
- **Ask**: Breaking changes, significant design decisions, anything that changes UX
- **Decide**: Implementation details, code patterns, build configuration, dependency versions
