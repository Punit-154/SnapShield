# Agentic AI Research — Improvements for Our Pipeline

> **Date**: June 14, 2026  
> **Scope**: Latest agentic AI frameworks → actionable improvements for our KDP/Etsy generation pipeline  
> **Focus**: Production-grade techniques that improve quality, reliability, and self-improvement

---

## Research Landscape Summary

| Framework | Key Innovation | Relevance to Us |
|-----------|---------------|:---:|
| **LangGraph** | Graph-based state machine orchestration with retry loops | ⭐⭐⭐ High |
| **DSPy** | Automatic prompt optimization via compilation, not handwriting | ⭐⭐⭐ High |
| **Google ADK + A2A** | Agent-to-agent protocol, hierarchical agents | ⭐⭐ Medium |
| **OpenAI Agents SDK** | Handoffs, guardrails, sessions | ⭐⭐ Medium |
| **MCP (Model Context Protocol)** | Standardized tool integration | ⭐⭐⭐ High |
| **RouteLLM / MasRouter** | Dynamic model routing for cost/quality | ⭐⭐⭐ High |
| **Self-Harness / Trace2Skill** | Agents that analyze their own traces and self-improve | ⭐⭐⭐ High |

---

## Improvement 1: LangGraph State Machine Orchestration

### What It Is
Replace our simple Python sequential pipeline with a **LangGraph state graph** — a directed graph where each node is a pipeline stage and edges define control flow, including **conditional retries, error recovery, and quality gates**.

### Why It Matters
Our current plan: `Planner → Writer → Checker → SEO` is a simple linear chain. But what happens when:
- The Writer produces text that fails the Checker? (need retry loop)
- Ollama crashes mid-generation? (need error recovery)
- The user wants human-in-the-loop approval? (need checkpoint)

LangGraph handles all of this natively.

### How to Implement

```python
from langgraph.graph import StateGraph, END
from langchain_ollama import ChatOllama

class PipelineState(TypedDict):
    niche: str              # Input niche
    spec: dict              # Planner output
    draft: str              # Writer output
    check_result: dict      # Checker verdict
    retry_count: int        # How many times Writer has retried
    final_listing: str      # Approved listing
    seo_tags: list          # SEO output
    
graph = StateGraph(PipelineState)
graph.add_node("planner", planner_node)
graph.add_node("writer", writer_node)
graph.add_node("checker", checker_node)
graph.add_node("seo", seo_node)

# Conditional edges — the key improvement
graph.add_edge("planner", "writer")
graph.add_edge("writer", "checker")

# If checker FAILS and retries < 3 → loop back to writer with feedback
# If checker PASSES → proceed to SEO
graph.add_conditional_edges("checker", route_after_check, {
    "retry": "writer",   # ← feedback loop!
    "pass": "seo",
    "fail": END,         # give up after 3 retries
})
graph.add_edge("seo", END)
```

### Impact
- **Quality**: Writer gets specific feedback on WHY it failed → targeted fix
- **Reliability**: Automatic retry on Ollama errors or model crashes
- **Observability**: Full execution trace for debugging

### Implementation Cost
- Install: `pip install langgraph langchain-ollama`
- Effort: ~2 hours to convert existing orchestrator
- Models: No change — same 4 models

---

## Improvement 2: DSPy Automatic Prompt Optimization

### What It Is
Instead of hand-writing system prompts for each model, use **DSPy** to automatically find the best prompts through iterative optimization against our evaluation metrics.

### Why It Matters
We currently hand-write prompts like:  
*"Write a product listing for this coloring book. Don't use banned phrases..."*

This is fragile. DSPy treats prompts as **compiled artifacts** — it tests hundreds of prompt variations against a scoring function and picks the best one automatically.

### How to Implement

```python
import dspy

# Define the task signature
class WriteProductListing(dspy.Signature):
    """Write a product listing for a KDP coloring book or Etsy digital download."""
    niche = dspy.InputField(desc="Product niche and target audience")
    spec = dspy.InputField(desc="Product specs: pages, size, style")
    listing = dspy.OutputField(desc="Product listing text, 150-300 words")

# Define a metric function
def listing_quality(example, prediction, trace=None):
    listing = prediction.listing
    score = 0
    
    # Rule checks
    words = len(listing.split())
    if 150 <= words <= 300: score += 1
    if not any(banned in listing for banned in BANNED_PHRASES): score += 1
    
    # Use Vectara HHEM for hallucination check
    if hallucination_score(example.spec, listing) > 0.5: score += 1
    
    return score / 3

# Compile — DSPy finds the optimal prompt
optimizer = dspy.BootstrapFewShot(metric=listing_quality, max_rounds=5)
compiled_writer = optimizer.compile(
    WriteProductListing(),
    trainset=gold_examples,  # Our 16+ gold standard listings
)

# The compiled writer now uses the BEST discovered prompt
# No more manual prompt engineering
```

### Impact
- **Quality**: Prompts optimized against actual metrics, not intuition
- **Maintainability**: When you add new rules, just re-compile
- **Portability**: When you switch models, re-compile → prompts auto-adjust

### Implementation Cost
- Install: `pip install dspy`
- Effort: ~3 hours to set up signatures + metrics for all 4 stages
- Requires: Our gold standard dataset (already have 16 examples)

---

## Improvement 3: Self-Improving Trace Analysis

### What It Is
Log every pipeline run's execution trace (inputs, outputs, scores, failures). Periodically analyze traces to:
1. **Mine weaknesses**: Find recurring failure patterns
2. **Generate training data**: Successful runs become new gold examples
3. **Optimize prompts**: Feed failure patterns back to DSPy

### Why It Matters
This is the **Trace2Skill** pattern from ICLR 2026 research. Instead of manually reviewing output quality, the system learns from its own production runs.

### How to Implement

```python
# After every pipeline run, log the full trace
trace = {
    "timestamp": datetime.now().isoformat(),
    "niche": "owls",
    "spec": {...},
    "writer_output": "...",
    "checker_verdict": {"pass": True, "score": 0.92},
    "seo_output": ["owl coloring book", "bird coloring pages", ...],
    "human_rating": None,  # Filled in later if user reviews
}

# Periodic analysis (daily or weekly)
def analyze_traces(traces):
    failures = [t for t in traces if not t["checker_verdict"]["pass"]]
    
    # Pattern detection
    banned_phrase_failures = [t for t in failures if "banned_phrase" in t["checker_verdict"]["reasons"]]
    word_count_failures = [t for t in failures if "word_count" in t["checker_verdict"]["reasons"]]
    
    # Auto-generate training data from successes
    successes = [t for t in traces if t["checker_verdict"]["score"] > 0.9]
    for s in successes:
        add_to_gold_dataset(s["spec"], s["writer_output"])
    
    # Report
    return {
        "total_runs": len(traces),
        "pass_rate": len(successes) / len(traces),
        "top_failure_modes": [...],
        "new_training_examples_generated": len(successes),
    }
```

### Impact
- **Continuous improvement**: System gets better with every run
- **Auto-scaling training data**: Gold dataset grows automatically
- **Failure prevention**: Patterns caught before they repeat

---

## Improvement 4: MCP Tool Protocol Integration

### What It Is
Wrap our Python tools (PDF assembler, ComfyUI bridge, Ollama bridge, file system) as **MCP servers** so any agent framework can use them through a standard protocol.

### Why It Matters
Currently our tools are tightly coupled Python function calls. With MCP:
- Any agent (LangGraph, CrewAI, even other AI assistants) can use our tools
- Tools are discoverable — new agents auto-detect available capabilities
- We can add new tools without modifying the orchestrator

### How to Implement

```python
from mcp.server.fastmcp import FastMCP

mcp = FastMCP("AI-Business-Tools")

@mcp.tool()
def generate_coloring_page(prompt: str, style: str = "bold_and_easy") -> str:
    """Generate a coloring book page using ComfyUI.
    Returns the file path to the generated image."""
    # Calls ComfyUI API
    ...

@mcp.tool()
def write_product_listing(niche: str, pages: int, audience: str) -> str:
    """Generate a product listing using the Writer model pipeline.
    Returns the complete listing text."""
    # Calls Ollama
    ...

@mcp.tool()  
def assemble_pdf(images_dir: str, output_path: str, page_count: int = 50) -> str:
    """Assemble coloring pages into a KDP-ready PDF.
    Returns the output PDF path."""
    ...

@mcp.tool()
def check_listing_quality(listing: str, spec: dict) -> dict:
    """Run quality checks on a listing. Returns pass/fail with details."""
    ...
```

### Impact
- **Interoperability**: Any future AI agent can use our tools
- **Modularity**: Tools are independent of the orchestrator
- **Future-proof**: When A2A protocol matures, our tools are ready

---

## Improvement 5: Model Routing (RouteLLM Pattern)

### What It Is
Instead of always using the same model for each stage, dynamically route based on task complexity. Simple niches → use smaller, faster model. Complex niches → use larger model.

### Why It Matters
Not every listing needs a 12B model. "Dog coloring book" is straightforward. "Steampunk Victorian clockwork mechanisms adult coloring book" needs more creative power.

### How to Implement

```python
class ModelRouter:
    def __init__(self):
        self.fast_model = "qwen3:4b"       # Simple tasks
        self.strong_model = "gemma4:12b"     # Complex tasks
    
    def route(self, spec: dict) -> str:
        complexity = self.estimate_complexity(spec)
        
        if complexity < 0.5:
            return self.fast_model    # 2x faster, saves VRAM
        else:
            return self.strong_model  # Better quality
    
    def estimate_complexity(self, spec: dict) -> float:
        score = 0.0
        
        # More niche = more complex
        niche_words = len(spec["niche"].split())
        if niche_words > 5: score += 0.3
        
        # Specialized audience = more complex
        if spec["audience"] not in ["adults", "kids", "seniors"]: score += 0.2
        
        # Long description needed = more complex
        if spec.get("min_words", 150) > 250: score += 0.2
        
        # Etsy (more creative) vs KDP (more formulaic)
        if spec["platform"] == "etsy": score += 0.15
        
        return min(score, 1.0)
```

### Impact
- **Speed**: 50% of listings generated 2-3x faster
- **VRAM**: Smaller model frees VRAM for other tasks
- **Quality preserved**: Complex listings still get the best model

---

## Improvement 6: Production Guardrails

### What It Is
Input/output validation hooks that run in parallel with every pipeline stage. Inspired by the OpenAI Agents SDK guardrail pattern.

### How to Implement

```python
class InputGuardrail:
    """Validates input before processing."""
    def check(self, spec: dict) -> tuple[bool, str]:
        # Reject obviously bad inputs
        if not spec.get("niche"):
            return False, "No niche specified"
        if spec.get("pages", 0) < 10:
            return False, "Too few pages"
        if spec.get("pages", 0) > 200:
            return False, "Too many pages (max 200)"
        return True, "OK"

class OutputGuardrail:
    """Validates output before returning."""
    
    BANNED_PATTERNS = [
        r"(?i)as an? (AI|language model|assistant)",
        r"(?i)I (cannot|can't|am unable)",
        r"(?i)(delve|tapestry|realm|embark|journey|elevate)",
        r"(?i)(?:un)?lock\s+(?:your|the|a)\s+\w+\s+potential",
    ]
    
    def check(self, listing: str) -> tuple[bool, list[str]]:
        violations = []
        for pattern in self.BANNED_PATTERNS:
            if re.search(pattern, listing):
                violations.append(f"Banned pattern: {pattern}")
        
        words = len(listing.split())
        if words < 100: violations.append(f"Too short: {words} words")
        if words > 500: violations.append(f"Too long: {words} words")
        
        return len(violations) == 0, violations
```

---

## Priority Ranking: What to Build First

| # | Improvement | Impact | Effort | Priority |
|---|-----------|:---:|:---:|:---:|
| 1 | **LangGraph orchestration** | Retry loops, error recovery | ~2 hrs | 🔴 Critical |
| 2 | **Production guardrails** | Prevents bad output reaching production | ~1 hr | 🔴 Critical |
| 3 | **Self-improving traces** | Auto-growing training data + failure detection | ~2 hrs | 🟡 High |
| 4 | **Model routing** | Speed + VRAM optimization | ~1 hr | 🟡 High |
| 5 | **DSPy prompt optimization** | Removes manual prompt engineering | ~3 hrs | 🟡 High |
| 6 | **MCP tool protocol** | Future-proof interoperability | ~2 hrs | 🟢 Medium |

### Recommended Build Order

```
Day 1:  LangGraph orchestrator + Guardrails        (pipeline works, retries, safe output)
Day 2:  Self-improving traces + Model routing       (learns from runs, optimizes speed)
Day 3:  DSPy prompt compilation                     (auto-optimize prompts)
Day 4:  MCP tool protocol                           (clean tool interfaces)
```

---

## Architecture After All Improvements

```
                    ┌─────────────────────────────────┐
                    │          USER REQUEST            │
                    └──────────────┬──────────────────┘
                                   │
                    ┌──────────────▼──────────────────┐
                    │      INPUT GUARDRAILS            │
                    │  (validate niche, pages, format)  │
                    └──────────────┬──────────────────┘
                                   │
           ┌───────────────────────▼────────────────────────┐
           │              LANGGRAPH STATE MACHINE            │
           │                                                 │
           │  ┌─────────┐    ┌─────────┐    ┌─────────┐    │
           │  │ PLANNER  │───▶│ WRITER  │───▶│ CHECKER │    │
           │  │ qwen3.6  │    │ MODEL   │    │ hybrid  │    │
           │  │          │    │ ROUTER  │    │         │    │
           │  └─────────┘    │ ┌──────┐│    └────┬────┘    │
           │                  │ │fast  ││         │         │
           │                  │ │strong││    ┌────▼────┐    │
           │                  │ └──────┘│    │ PASS?   │    │
           │                  └─────────┘    │ retry<3 │    │
           │                      ▲          └────┬────┘    │
           │                      │               │         │
           │                      └──RETRY LOOP───┘         │
           │                               │                │
           │                          ┌────▼────┐           │
           │                          │   SEO   │           │
           │                          │ qwen3.6 │           │
           │                          └────┬────┘           │
           └───────────────────────────────┼────────────────┘
                                           │
                    ┌──────────────────────▼──────────────────┐
                    │          OUTPUT GUARDRAILS               │
                    │  (banned phrases, word count, format)    │
                    └──────────────────────┬──────────────────┘
                                           │
                    ┌──────────────────────▼──────────────────┐
                    │         TRACE LOGGER                     │
                    │  (log everything for self-improvement)   │
                    └──────────────────────┬──────────────────┘
                                           │
                    ┌──────────────────────▼──────────────────┐
                    │          FINAL OUTPUT                    │
                    │  (listing + SEO tags + quality score)    │
                    └─────────────────────────────────────────┘
```

> [!IMPORTANT]
> All of these improvements work with **local models via Ollama**. No cloud APIs required. LangGraph + DSPy + MCP all support `ChatOllama` as a backend.

Approve this research and improvement plan?
