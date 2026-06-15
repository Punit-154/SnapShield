# Model Strategy: Multi-Model Pipeline Architecture

> **Date**: June 14, 2026  
> **Hardware**: RTX 5070 (12GB VRAM), 64GB RAM  
> **Runtime**: Ollama (v0.30.8, auto-loads/unloads models)

---

## Part 1: The Latest Model Landscape (as of June 14, 2026)

### What's Actually Available for Local Use

| Model | Released | Sizes (Ollama) | Best At | Fits 12GB? |
|-------|----------|----------------|---------|:---:|
| **Gemma 4** (Google) | Apr–Jun 2026 | E2B, E4B, **12B**, 26B MoE, 31B | Natural prose, multimodal, QAT-optimized | ✅ 12B fits |
| **Qwen 3.6** (Alibaba) | Apr 2026 | **35B-A3B** MoE, 27B dense | Best narrative consistency, agentic coding | ✅ 35B-A3B (only 3B active!) |
| **Qwen 3.5** (Alibaba) | Mar 2026 | 0.8B, 2B, 4B, **9B**, 27B | Strong all-rounder, good instruction following | ✅ 9B fits |
| **Phi-4-reasoning** (MS) | Mar 2026 | 15B (vision), 14B | Best reasoning for its size, strict instruction following | ⚠️ Tight at Q4 |
| **Qwen 3** (Alibaba) | 2025 | 4B, **8B**, 14B, 30B | Thinking mode, good structure | ✅ 8B fits |
| **Gemma 3** (Google) | 2025 | 4B, **12B**, 27B | Proven "lively" text, long context | ✅ 12B fits |
| **Llama 4 Scout** (Meta) | Apr 2025 | 109B-17B MoE | Long context, reasoning | ❌ Too large |
| **Qwen 3.7** (Alibaba) | May 2026 | Cloud-only | Flagship, best quality | ❌ Not open-weight |

### Key Finding: Qwen 3.6 35B-A3B is the Hidden Gem

**Qwen 3.6 35B-A3B** is a 35B parameter MoE model that only activates **3B parameters** per token. This means:
- It has the knowledge of a 35B model
- It runs at the speed/VRAM cost of a 3B model
- It fits easily in 12GB VRAM with room to spare
- It's the latest open-weight Qwen available locally

---

## Part 2: Multi-Model Pipeline Design

### The Problem with Single-Model Approaches

One model trying to do everything leads to:
- ❌ Prompt bloat (huge system prompts listing all rules)
- ❌ Conflicting objectives (be creative BUT follow strict rules)
- ❌ Context pollution (SEO instructions leak into creative writing)
- ❌ Single point of failure (bad model = bad everything)

### The Solution: 4-Stage Pipeline

```
┌─────────────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR (Python)                         │
│                  Manages flow + context                          │
├─────────┬──────────────┬──────────────┬────────────────────────┤
│ Stage 1 │   Stage 2    │   Stage 3    │       Stage 4          │
│ PLANNER │   WRITER     │   CHECKER    │       SEO              │
│         │              │              │                        │
│ qwen3.6 │  gemma4:12b  │  qwen3.5:9b  │   qwen3.6:35b-a3b    │
│ :35b-a3b│              │              │                        │
│ (3B act)│  (12B)       │  (9B)        │   (3B active)          │
│         │              │              │                        │
│ "What   │ "Write the   │ "Check this  │  "Generate 7 SEO      │
│ niche?  │  listing in  │  listing for │   keywords and 13     │
│ What    │  this tone   │  banned words│   Etsy tags for       │
│ format?"│  and style"  │  hallucinated│   this listing"       │
│         │              │  details,    │                        │
│         │              │  word count" │                        │
└─────────┴──────────────┴──────────────┴────────────────────────┘
```

### Stage Details

#### Stage 1: PLANNER — `qwen3.6:35b-a3b` (3B active, ~2GB VRAM)
**Role**: Niche analysis, prompt construction, routing  
**Why this model**: Fast, cheap, great at structured reasoning. Only 3B active params means it loads instantly.  
**Input**: User's niche request or daily schedule  
**Output**: Structured JSON with niche details, target audience, page count, style  
**Context size**: Tiny — just the niche database + request

```
Planner receives: "Generate a KDP coloring book about owls"
Planner outputs:  {
  "product_type": "kdp",
  "title": "Wise Owls Coloring Book",
  "subtitle": "Detailed Owl Illustrations for Adults",
  "pages": 50,
  "style": "Bold and Easy",
  "audience": "Adults and seniors",
  "niche_keywords": ["owls", "birds of prey", "nocturnal birds", "wildlife"]
}
```

#### Stage 2: WRITER — `gemma4:12b` (12B, ~8GB VRAM)
**Role**: Write the actual product listing copy  
**Why this model**: Research shows Gemma 4 produces the most natural, human-sounding marketing prose. It was released June 2026 with QAT optimization for local hardware.  
**Input**: Structured spec from Planner  
**Output**: Raw listing text (description only, no SEO)  
**Context size**: Small — just the spec + writing guidelines + 3 example listings (from training data)  
**Key**: The Writer's prompt is ONLY about writing quality. No SEO rules, no marketplace compliance — those are handled by other models.

#### Stage 3: CHECKER — `qwen3.5:9b` (9B, ~6GB VRAM)
**Role**: Quality gate — validates output against hard rules  
**Why this model**: Qwen 3.5 has strong instruction following and structured output. Perfect for binary pass/fail checks.  
**Input**: Raw listing from Writer + the original spec  
**Output**: Pass/Fail verdict with specific violations  
**Checks**:
- Word count within range? (actually counts the words)
- Any banned AI phrases? (regex + model judgment)
- Any hallucinated details not in the spec?
- Any fake reviews or testimonials?
- Tone sounds human? (subjective judgment)

```
If FAIL → return to Stage 2 with specific fix instructions (max 2 retries)
If PASS → proceed to Stage 4
```

#### Stage 4: SEO OPTIMIZER — `qwen3.6:35b-a3b` (3B active, ~2GB VRAM)
**Role**: Generate keywords, tags, and metadata  
**Why this model**: SEO is a structured, analytical task — perfect for the fast MoE model. Keeps SEO concerns completely separate from creative writing.  
**Input**: Approved listing text + niche + marketplace type  
**Output**: 7 KDP keywords OR 13 Etsy tags + optimized title  
**Key**: The Writer never sees SEO instructions, so it never keyword-stuffs. SEO is applied as a separate layer.

### VRAM Flow (Sequential Loading)

Ollama automatically manages model loading/unloading. Only one model is loaded at a time:

```
Time →  ▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬
VRAM:   [Planner 2GB]  [Writer 8GB]  [Checker 6GB] [SEO 2GB]
Time:     ~2 sec          ~8 sec        ~5 sec       ~2 sec
                                                  Total: ~17 sec
```

No model exceeds 12GB. The largest (Gemma 4 12B at ~8GB) leaves 4GB for KV cache.

### Why This Is Better

| Issue | Single Model | Multi-Model Pipeline |
|-------|:---:|:---:|
| Banned phrase usage | Hard to suppress | Writer model never sees "ban" rules — Checker catches post-hoc |
| Hallucination | System prompt gets ignored | Checker independently verifies against spec |
| Word count | Model can't count own tokens | Checker counts words with actual Python `len()` |
| SEO quality | Keyword stuffing | SEO is a separate pass — writing is never compromised |
| Context bloat | 2000+ token system prompt | Each model gets 200-400 tokens of focused instructions |
| Speed | 1 large model, slow | Mix of fast tiny + medium models |
| VRAM | Stuck with one model size | Loads optimal size per task |

---

## Part 3: Models to Pull

Based on this architecture, we need exactly 3 models:

```bash
ollama pull qwen3.6:35b-a3b    # Planner + SEO (MoE, 3B active)
ollama pull gemma4:12b          # Writer (best natural prose)
ollama pull qwen3.5:9b          # Checker (strict instruction following)
```

### Alternative: If Gemma 4 proves unstable

```bash
ollama pull gemma3:12b          # Writer fallback (proven, stable)
```

---

## Part 4: Training Data Strategy

For fine-tuning (Phase 7), we're building a gold-standard dataset:

| Source | Method | Count | Status |
|--------|--------|:---:|:---:|
| **Claude-generated examples** | I write perfect listings myself | 15 | ✅ Done (batch 1) |
| **Claude-generated examples** | More niches, edge cases | 35+ | 🔄 In progress |
| **Real top-seller scraping** | Collect from Amazon/Etsy | 50+ | ⏳ Planned |
| **Synthetic negative examples** | Bad listings with violations marked | 20+ | ⏳ Planned |

**Total target**: 100+ high-quality instruction/output pairs  
**Training method**: QLoRA via Unsloth on RTX 5070  
**Base model for fine-tune**: Gemma 4 12B (the Writer model)

> [!NOTE]
> The multi-model pipeline reduces the urgency of fine-tuning. The Checker model catches most issues that fine-tuning would fix. Fine-tuning becomes an optimization, not a necessity.

---

## Proposed Action Plan

1. **Pull 3 models**: `qwen3.6:35b-a3b`, `gemma4:12b`, `qwen3.5:9b`
2. **Build pipeline orchestrator**: Update `orchestrator.py` with multi-stage flow
3. **A/B test pipeline vs. single-model**: Run same 3 test cases
4. **Continue dataset generation**: Build to 50+ examples
5. **End-to-end test**: Full KDP generation with ComfyUI images

Approve this approach?
