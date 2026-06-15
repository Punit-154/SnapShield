# Per-Stage Model Sourcing Plan

> For each pipeline stage: pre-trained option, custom training plan, datasets, and infrastructure.

---

## Stage 1: PLANNER — Niche Analysis & Routing

**Task**: Take a niche request → output structured JSON spec (title, subtitle, pages, audience, style)

### Pre-trained Option
| Model | Why | VRAM | Ollama |
|-------|-----|:---:|--------|
| **Qwen 3.6 35B-A3B** | Best structured output for its effective size. 35B knowledge, 3B compute. MoE. | ~2-3 GB | `ollama pull qwen3.6` |
| Fallback: Qwen 3 4B | Tiny, fast, decent JSON output | ~2 GB | Already installed |

### Custom Training: **Not needed**
This task is simple structured output from a prompt template. Constrained decoding (Ollama JSON mode or llama.cpp grammars) solves it without training. The model just needs to fill in a schema.

### Recommendation: **Use pre-trained Qwen 3.6 35B-A3B + JSON mode**
No training required. Just good system prompt + Ollama's `format: json` parameter.

---

## Stage 2: WRITER — Creative Product Listing Copy

**Task**: Take structured spec → write natural, human-sounding product listing (150-300 words)

### Pre-trained Options
| Model | Why | VRAM | Ollama |
|-------|-----|:---:|--------|
| **Gemma 4 12B** | Best natural prose quality (June 2026). QAT-optimized. | ~8 GB | `ollama pull gemma4:12b` |
| **Gemma 3 12B** | Proven "lively" text, stable fallback | ~8 GB | `ollama pull gemma3:12b` |

### Custom Training: **YES — LoRA fine-tune**

This is where custom training makes the biggest difference. We need the model to:
- Write like a real Etsy/Amazon seller (not like an AI)
- Never use cliché marketing phrases
- Respect word counts intuitively
- Follow our exact format structure

#### Base Model for Fine-Tuning
**Gemma 4 12B** (if GPU can handle it) or **Gemma 3 12B** (safer for 12GB VRAM)

#### Training Data

| Source | Records | Type | Status |
|--------|:---:|------|:---:|
| **Claude-generated gold examples** | 15 | Instruction/Output pairs I wrote myself | ✅ Done |
| **Claude-generated batch 2** | 35+ | More niches, edge cases | 🔄 To generate |
| **`Ateeqq/Amazon-Product-Description`** | 421K | Real Amazon descriptions, cleaned | 📦 HuggingFace |
| **`iarbel/amazon-product-data-filter`** | Large | Structured product data with bullets | 📦 HuggingFace |
| **Negative examples** | 20+ | Bad listings with violations marked (DPO) | ⏳ Planned |

#### Training Infrastructure

| Option | GPU | Cost | Time Est. |
|--------|-----|:---:|:---------:|
| **Local RTX 5070** | 12GB | Free | ~2-3 hrs for 8B base |
| **Google Colab Free** | T4 16GB | Free | ~1-2 hrs for 8B base |
| **Kaggle Free** | P100 16GB | Free | ~1-2 hrs for 8B base |

#### Training Recipe
```
Framework:    Unsloth
Method:       QLoRA (4-bit base + LoRA rank 16)
Base:         unsloth/gemma-3-12b-it-bnb-4bit (or gemma4 when supported)
Dataset:      listings_gold_v1.json + Amazon descriptions (filtered for quality)
Epochs:       3
Batch:        1 (gradient accumulation 4)
Max seq len:  2048
Output:       GGUF Q4_K_M → import into Ollama
```

### Recommendation: **Gemma 4 12B base + LoRA fine-tune on 100+ examples**
Train on local GPU or free Colab. Export to GGUF. Create custom Ollama model.

---

## Stage 3: CHECKER — Quality Validation Gate

**Task**: Check listing against hard rules — banned phrases, hallucination, word count, format compliance

### Pre-trained Options

This is where it gets interesting. We DON'T necessarily need a full LLM here. Most checks are deterministic:

#### Hybrid Checker Architecture (Recommended)

```
┌─────────────────────────────────────────────────────────────┐
│                    CHECKER (Python)                          │
├─────────────────┬───────────────────┬───────────────────────┤
│  Rule Engine    │  Hallucination    │   Tone Classifier     │
│  (Python)       │  Detector         │   (tiny LLM)          │
│                 │  (Pre-trained)    │                       │
│ • Word count    │                   │                       │
│ • Banned phrase │ Vectara HHEM      │  Qwen 3 4B or         │
│   regex         │ (~439MB)          │  Gemma 4 E2B          │
│ • Format check  │ Compares listing  │  "Does this sound     │
│ • Fake review   │ vs input spec     │   like AI wrote it?"  │
│   detection     │ for hallucinated  │  → yes/no score       │
│                 │ facts             │                       │
└─────────────────┴───────────────────┴───────────────────────┘
```

#### Component Details

| Component | Model/Tool | Size | What it does |
|-----------|-----------|:---:|-------------|
| **Rule Engine** | Python (no model) | 0 GB | `len(text.split())` for word count, regex for banned phrases, pattern match for fake reviews |
| **Hallucination Detector** | `vectara/hallucination_evaluation_model` | 439 MB | Compares listing (hypothesis) vs input spec (premise). Score < 0.5 = hallucinated |
| **Tone Classifier** | `qwen3:4b` (already installed) | 2.3 GB | "Rate 1-10 how much this sounds like AI-generated marketing copy" |

### Custom Training: **Train the Tone Classifier**

We can fine-tune a tiny model to specifically detect AI-sounding text:

#### Training Data for Tone Classifier
| Label | Source | Count |
|-------|--------|:---:|
| **Human (good)** | Real top-selling Amazon/Etsy listings scraped from web | 100+ |
| **AI (bad)** | Generate listings with vanilla LLMs, no prompt engineering | 100+ |

This creates a binary classifier: "human-sounding" vs "AI-sounding"

#### Training Infrastructure
- **Qwen 3 4B** base → LoRA fine-tune as classifier
- Fits on free Colab T4 easily
- Training time: ~30 min

### Recommendation: **Hybrid — Python rules + Vectara HHEM + fine-tuned tiny tone classifier**
No expensive LLM needed. The Checker is mostly deterministic code + two small specialized models.

---

## Stage 4: SEO OPTIMIZER — Keywords, Tags, Metadata

**Task**: Generate 7 KDP keywords or 13 Etsy tags from approved listing text

### Pre-trained Options
| Model | Why | VRAM | Ollama |
|-------|-----|:---:|--------|
| **Qwen 3.6 35B-A3B** | Same as Planner. Structured output, fast, analytical. | ~2-3 GB | Same model as Stage 1 |
| Fallback: Qwen 3.5 9B | Strong instruction following | ~6 GB | `ollama pull qwen3.5:9b` |

### Custom Training: **YES — LoRA fine-tune on real SEO data**

SEO keywords aren't creative — they're data-driven. The model needs to learn what people actually search for.

#### Training Data

| Source | Records | Content |
|--------|:---:|---------|
| **`McAuley-Lab/Amazon-Reviews-2023`** | Millions | Product metadata with categories, search terms |
| **Amazon auto-suggest scraping** | 500+ | Real search queries for coloring book + wall art niches |
| **Etsy tag mining** | 200+ | Tags from top-selling digital art shops |
| **Claude-generated examples** | 50 in gold dataset | Instruction → 7 keywords or 13 tags |

#### Training Recipe
```
Framework:    Unsloth
Method:       QLoRA on Qwen 3.6 35B-A3B (or Qwen 3.5 4B for speed)
Dataset:      Real search terms + product-to-keyword pairs
Task:         Given product description → output ranked keyword list
Epochs:       3
Time:         ~30 min on local GPU (tiny model)
```

### Recommendation: **Qwen 3.6 35B-A3B + LoRA fine-tuned on real marketplace search data**

---

## Summary: The Full Stack

| Stage | Base Model | Pre-trained? | Custom Train? | Train Where | Key Dataset |
|-------|-----------|:---:|:---:|-------------|-------------|
| **Planner** | Qwen 3.6 35B-A3B | ✅ Use as-is | ❌ Not needed | — | — |
| **Writer** | Gemma 4 12B | ✅ Good baseline | ✅ **LoRA fine-tune** | Local GPU or Colab | Claude gold + Amazon descriptions |
| **Checker** | Python + Vectara HHEM + Qwen 3 4B | ✅ Mostly pre-trained | ✅ **Tone classifier** | Free Colab | Human vs AI listing pairs |
| **SEO** | Qwen 3.6 35B-A3B | ✅ Good baseline | ✅ **LoRA fine-tune** | Local GPU | Real marketplace search terms |

---

## Execution Order

```
Phase A (Now):        Pull qwen3.6, gemma4:12b, install Vectara HHEM
Phase B (Today):      Generate 50 more gold training examples
Phase C (Today):      Download Amazon dataset from HuggingFace  
Phase D (Tomorrow):   Set up Unsloth training pipeline
Phase E (Tomorrow):   Train Writer LoRA (2-3 hrs on local GPU or Colab)
Phase F (Tomorrow):   Train Tone Classifier (30 min)
Phase G (Day 3):      Scrape real marketplace search terms
Phase H (Day 3):      Train SEO LoRA (30 min)
Phase I (Day 3):      Build pipeline orchestrator, end-to-end test
Phase J (Ongoing):    ASSERT eval with me as judge after each iteration
```

> [!NOTE]
> **Free training paths**: All fine-tuning can be done on Google Colab (free T4) or Kaggle (free P100) if you prefer not to use the local GPU. The local RTX 5070 is equally capable.

Approve this plan?
