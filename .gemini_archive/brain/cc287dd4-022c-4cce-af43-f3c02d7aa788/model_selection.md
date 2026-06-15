# Model Selection: Is Qwen 2.5 14B the Right Model?

**Short answer: No.** Qwen 2.5 14B is a general-purpose model from 2024. There are better options for this specific task in 2026.

---

## The Problem

Our ASSERT evaluation revealed Qwen 2.5 14B has a **70% failure rate** on the dimensions that matter most for product listings:
- Invents fake product specs and customer reviews
- Can't control its own word count
- Uses AI-sounding language it was literally trained on

This isn't a prompting problem — it's a model problem. General-purpose LLMs are trained on internet text, which is full of exactly the marketing clichés we're trying to avoid.

---

## 3 Options (Best → Most Effort)

### Option A: Quick Swap — Qwen3 8B ⚡ (15 minutes)

You already have `qwen3:8b` installed on your machine.

| | Qwen 2.5 14B (current) | Qwen3 8B |
|---|---|---|
| Size | 9.0 GB | 5.2 GB |
| Architecture | Dense | MoE-influenced, better alignment |
| Instruction following | Good | **Significantly better** |
| "Thinking" mode | No | **Yes** — deliberates before outputting |
| Creative writing quality | Decent | **Better nuance, less cliché** |
| VRAM usage | ~9 GB | ~5 GB (leaves room for ComfyUI) |
| Speed | Moderate | **Faster** (smaller model) |

**Why it's better**: Qwen3 was specifically improved for instruction-following and "thinking" tasks. It's much better at *not* doing things (like "don't use banned phrases") because it can reason about constraints before generating. It also uses 4 GB less VRAM, which is critical when running alongside ComfyUI.

**Cost**: Zero. Already installed. Just change the model name in config.

---

### Option B: Fine-Tune a LoRA — Custom Listing Writer 🎯 (2-4 hours)

Fine-tune a specialized LoRA adapter using **Unsloth** on your RTX 5070.

**Base model**: Qwen3 8B or Llama 3.1 8B  
**Method**: QLoRA (4-bit) via Unsloth — fits in 12GB VRAM  
**Training data**: 50-100 high-quality real listings scraped from top Amazon KDP sellers  
**Training time**: ~1-2 hours on RTX 5070  

This would produce a model that:
- Writes in a natural, human tone (learned from real sellers)
- Never uses AI clichés (never saw them in training)
- Respects word counts (learned from correctly-sized examples)
- Follows your exact format requirements (learned from structured examples)

**Cost**: Time to curate training data (manual effort) + 2 hours GPU time.

> [!IMPORTANT]
> This is the gold standard approach recommended by the entire industry in 2026. A fine-tuned 8B model will dramatically outperform a general 14B model for this specific task.

---

### Option C: Hybrid — Quick Swap Now, Fine-Tune Later 🔄

1. **Today**: Swap to Qwen3 8B (immediate improvement)
2. **This week**: Collect 50+ real high-quality KDP/Etsy listings as training data
3. **Next session**: Fine-tune a LoRA with Unsloth
4. **Ongoing**: Re-evaluate with ASSERT (with me as judge) after each improvement

---

## My Recommendation: Option C (Hybrid)

Here's why:

1. **Qwen3 8B is already on your machine** — zero effort, immediate improvement
2. **It frees 4 GB VRAM** — critical when running ComfyUI + Ollama simultaneously
3. **Fine-tuning on your GPU is feasible** — Unsloth + QLoRA fits an 8B model in 12 GB
4. **The bottleneck is training data, not compute** — we need to collect real listings first
5. **ASSERT + me as judge** gives us a reliable way to measure improvement

## Proposed Action Plan

```
Step 1 (Now):     Switch orchestrator from qwen2.5:14b → qwen3:8b
Step 2 (Now):     Run a quick A/B comparison on 3 test prompts
Step 3 (Today):   Scrape 50 top-selling KDP coloring book descriptions for training data
Step 4 (Session): Fine-tune a LoRA with Unsloth (2 hrs on RTX 5070)
Step 5 (Verify):  Re-run ASSERT evaluation → I judge the results
```

> [!NOTE]
> The fine-tuning step (Step 4) will be Phase 3 of the implementation plan. I'll set up the entire Unsloth pipeline, training script, and data preparation tooling.

## Which option would you like to go with?
