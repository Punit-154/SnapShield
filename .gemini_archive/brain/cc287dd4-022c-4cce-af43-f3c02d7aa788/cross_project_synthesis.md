# Cross-Project Technical Synthesis

> Extracted from 44 files across `d:\portfolio-draft` and `d:\agentos`  
> **Focus**: Only technical patterns applicable to the AI-Business pipeline  
> **Personal info excluded** per user request

---

## What I Found Most Useful

### 1. The 3-Layer Router Pattern (from AgentOS)

**Source**: `d:\agentos\docs\ROUTER.md`, `d:\agentos\docs\ARCHITECTURE.md`

The AgentOS uses a cascading 3-layer classification system:
```
Layer 1: Rules Engine (~0ms, handles ~40% of traffic)
  ↓ no match
Layer 2: Semantic Router (~5-20ms, handles ~55%)  
  ↓ low confidence
Layer 3: LLM Classifier (~100-300ms, handles ~5%)
```

**Direct application to AI-Business**: Our Model Router (Improvement #5 from the agentic research) should use this exact pattern:

```python
# AI-Business Model Router (adapted from AgentOS)
class ListingRouter:
    def route(self, spec):
        # Layer 1: Rules (~0ms) — keyword heuristics
        if spec["product_type"] == "kdp" and spec["pages"] <= 30:
            return "fast_model"  # Simple coloring book
        if spec["niche"] in self.known_simple_niches:
            return "fast_model"
        
        # Layer 2: Semantic (~5ms) — embedding similarity to past jobs
        similarity = self.embed_and_compare(spec["niche"])
        if similarity > 0.85:
            return self.historical_best_model(spec["niche"])
        
        # Layer 3: LLM (~100ms) — ask tiny model to classify complexity
        return self.llm_classify_complexity(spec)
```

**Key insight**: 95% of traffic never hits the expensive LLM layer. This saves VRAM load time.

---

### 2. Privacy-First Architecture

**Source**: `d:\agentos\docs\ROUTER.md` lines 30-35

AgentOS has a **post-classification privacy override**:
```python
sensitive_patterns = ["password", "secret", "token", "api_key", "private_key", ".env"]
# If found → forces Ollama (local) regardless of cloud routing
```

**Direct application**: Our pipeline should NEVER send customer listing data to cloud APIs. All listing generation stays local. But we could add a privacy check for:
- Customer-provided product details
- Niche research data containing competitor information
- Any scraped marketplace data

---

### 3. SQLite Decision Logging

**Source**: `d:\agentos\docs\ROUTER.md` lines 99-121

AgentOS logs every routing decision to SQLite with queryable fields:
```sql
SELECT task_type, classified_by, AVG(latency_ms), COUNT(*)
FROM routing_log GROUP BY task_type, classified_by;
```

**Direct application**: Our pipeline should log every listing generation run to SQLite:
```sql
CREATE TABLE pipeline_log (
    id INTEGER PRIMARY KEY,
    timestamp TEXT,
    niche TEXT,
    product_type TEXT,        -- kdp/etsy
    writer_model TEXT,        -- which model was used
    writer_latency_ms REAL,
    checker_pass BOOLEAN,
    checker_score REAL,
    retry_count INTEGER,
    seo_model TEXT,
    total_latency_ms REAL,
    word_count INTEGER,
    banned_phrase_count INTEGER,
    human_rating REAL         -- filled in later
);
```

This feeds directly into the **self-improving trace analysis** (Improvement #3 from agentic research).

---

### 4. Semantic Caching (Cosine Similarity 0.90)

**Source**: `d:\portfolio-draft\docs\skills\model-routing.md`, `d:\portfolio-draft\docs\personal\agentic_research_insights.md`

Before routing a new task, check if a semantically similar task was recently processed:
- Embed the new niche description
- Compare against cache of recent generations
- If cosine similarity > 0.90, reuse the previous model routing decision + prompt template

**Direct application**: If someone asks for "Owls Coloring Book" and we already generated "Bird Coloring Book", we can:
1. Reuse the same Writer model selection
2. Reuse a similar prompt template
3. Skip the Planner stage entirely (just swap the niche keyword)

---

### 5. Config Layering Pattern

**Source**: `d:\agentos\docs\ARCHITECTURE.md` lines 147-157

```
defaults.yaml (shipped, read-only)
    ↓ merged with
config.yaml (user edits)
    ↓ env vars resolved
.env (secrets)
    ↓
Final runtime config
```

**Direct application**: Our AI-Business pipeline should use this:
```
D:\AI-Business\config\defaults.yaml      ← shipped defaults (models, thresholds)
D:\AI-Business\config\config.yaml        ← user overrides (custom niches, schedule)
D:\AI-Business\config\.env               ← API keys (HuggingFace, etc.)
```

This makes it trivial for other agents to reconfigure without touching code.

---

### 6. Skills-as-Modules → MCP Servers

**Source**: `d:\agentos\docs\SKILLS.md`, `d:\portfolio-draft\docs\skills\hermes-skills.md`

AgentOS has 10 self-contained skills, each in its own directory with:
```
skill-name/
├── __init__.py
├── skill.yaml        ← metadata, triggers, description
├── handler.py        ← main logic
└── prompts/          ← prompt templates
```

**Direct application**: Each stage of our pipeline should be a self-contained skill:
```
D:\AI-Business\skills\
├── planner/
│   ├── skill.yaml
│   ├── handler.py
│   └── prompts/system_prompt.txt
├── writer/
│   ├── skill.yaml
│   ├── handler.py
│   └── prompts/
├── checker/
│   └── ...
└── seo/
    └── ...
```

Each skill can then be wrapped as an MCP server for universal tool access.

---

### 7. Idempotent Installation

**Source**: `d:\agentos\docs\ARCHITECTURE.md`, `d:\portfolio-draft\docs\skills\wsl-automation.md`

AgentOS uses marker files + `--dry-run` and `--uninstall` flags. Every install step is:
1. Check if already done (marker file exists)
2. If not, do the work
3. Create marker file
4. If `--uninstall`, reverse the work and delete marker

**Direct application**: Our `setup.py` for the AI-Business platform should be idempotent:
```python
def ensure_model(model_name):
    marker = Path(f"D:/AI-Business/.markers/{model_name}.pulled")
    if marker.exists():
        return  # Already done
    os.system(f"ollama pull {model_name}")
    marker.parent.mkdir(exist_ok=True)
    marker.touch()
```

---

### 8. n8n Workflow Automation

**Source**: `d:\portfolio-draft\docs\skills\n8n-workflows.md`

n8n is already set up in the AgentOS stack at port 5678. It has workflow templates for automated tasks.

**Direct application**: We could use n8n to:
- Schedule daily batch listing generation
- Auto-upload generated PDFs to a staging folder
- Send notifications when a batch completes
- Chain: Niche Research → Image Gen → PDF Assembly → Listing Gen → Package

---

## Technical Patterns to Reuse (Summary)

| # | Pattern | Source | Apply To |
|---|---------|--------|----------|
| 1 | 3-layer cascading router | AgentOS ROUTER.md | Model routing for Writer stage |
| 2 | Privacy override (force-local) | AgentOS ROUTER.md | Never send listing data to cloud |
| 3 | SQLite decision logging | AgentOS ROUTER.md | Pipeline trace logging |
| 4 | Semantic caching (cos > 0.90) | AgentOS + agentic research | Skip redundant pipeline stages |
| 5 | Config layering (defaults → user → env) | AgentOS ARCHITECTURE.md | Pipeline configuration |
| 6 | Skills-as-modules | AgentOS SKILLS.md | Each pipeline stage as self-contained skill |
| 7 | Idempotent install with markers | AgentOS install scripts | Model pulling, dep installation |
| 8 | n8n workflow automation | AgentOS n8n workflows | Batch scheduling, notifications |

## Constraints to Respect

- **All data on D: drive** — never C:
- **Local-first** — no cloud storage of generated content or training data
- **12GB VRAM budget** — models must fit, sequential loading via Ollama
- **Windows primary** — PowerShell for scripts, WSL2 available but not required
- **Agent-maintainable** — all docs readable by AI agents, comprehensive handoffs

## Gaps Noticed

1. **No n8n integration yet** — AgentOS has it at port 5678, could schedule batch generation
2. **No SQLite logging yet** — need to add for self-improving traces
3. **No config layering** — currently hardcoded values in Python scripts
4. **No semantic cache** — every niche routes through full pipeline even if similar to a recent job

---

## Dataset Status Update

```
Downloaded to D:\AI-Business\training\raw\:
  advertisegen.jsonl:            114,599 records
  ai_human_text.jsonl:           100,000 records
  defactify_ai_human.jsonl:       51,247 records
  amazon_search_benchmark.jsonl:  20,000 records
  synthetic_search_queries.jsonl: 10,000 records
  marketing_prompts.jsonl:         4,642 records
  product_ads_gpt4.jsonl:             90 records
  ─────────────────────────────────────────
  TOTAL:                         300,578 records
```

Still needed (gated/failed):
- Amazon Product Descriptions (421K) — requires HuggingFace account access
- Shopify catalogue — broken parquet files on their end
