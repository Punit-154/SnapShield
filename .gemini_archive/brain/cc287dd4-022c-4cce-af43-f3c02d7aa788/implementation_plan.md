# Implementation Plan: Automated AI Production Agent

## Goal

Build a **self-operating, self-documenting local agent platform** that:
1. Researches profitable niches
2. Generates images via ComfyUI API (coloring pages + wall art)
3. Post-processes and assembles products (PDFs, print-ready files)
4. Writes product listings via local LLM
5. Outputs ready-to-publish packages
6. Maintains logs, diagnostics, and documentation so **any AI agent or human can take over**

---

## System Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                    ORCHESTRATOR AGENT (Python)                      │
│                    D:\AI-Business\agent\orchestrator.py             │
│                                                                     │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐       │
│  │  NICHE   │──►│  IMAGE   │──►│  POST-   │──►│ LISTING  │       │
│  │ RESEARCH │   │   GEN    │   │ PROCESS  │   │  WRITER  │       │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘       │
│       │              │              │              │               │
│       ▼              ▼              ▼              ▼               │
│  ┌──────────┐   ┌──────────┐   ┌──────────┐   ┌──────────┐       │
│  │ Niche DB │   │ ComfyUI  │   │ Pillow + │   │  Ollama  │       │
│  │ (JSON)   │   │ REST API │   │ Scripts  │   │   API    │       │
│  └──────────┘   └──────────┘   └──────────┘   └──────────┘       │
│                  127.0.0.1:8188              127.0.0.1:11434       │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                    OUTPUT PACKAGE                         │      │
│  │  📁 book-001-bold-lions/                                 │      │
│  │  ├── interior.pdf          (KDP-ready)                   │      │
│  │  ├── cover.png             (Book cover)                  │      │
│  │  ├── kdp_description.txt   (Amazon listing)              │      │
│  │  ├── kdp_keywords.txt      (7 keywords)                  │      │
│  │  ├── etsy_listings/        (Wall art variants)           │      │
│  │  │   ├── listing_1.zip     (Multi-size prints)           │      │
│  │  │   └── listing_1.json    (Title, desc, tags)           │      │
│  │  ├── redbubble/            (Upload-ready images)         │      │
│  │  └── manifest.json         (Package metadata)            │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                  KNOWLEDGE BASE                           │      │
│  │  📁 D:\AI-Business\knowledge\                            │      │
│  │  ├── niches.json           (Validated niche database)    │      │
│  │  ├── performance.json      (Sales/ranking history)       │      │
│  │  ├── prompts_tested.json   (Prompt → quality scores)     │      │
│  │  ├── model_configs.json    (Best settings per task)      │      │
│  │  └── error_log.json        (Failures + resolutions)      │      │
│  └──────────────────────────────────────────────────────────┘      │
│                                                                     │
│  ┌──────────────────────────────────────────────────────────┐      │
│  │                  DIAGNOSTICS & LOGS                       │      │
│  │  📁 D:\AI-Business\logs\                                 │      │
│  │  ├── agent.log             (Main orchestrator log)       │      │
│  │  ├── comfyui.log           (API call log)                │      │
│  │  ├── ollama.log            (LLM call log)                │      │
│  │  ├── errors.log            (All errors)                  │      │
│  │  └── diagnostics.py        (System health checker)       │      │
│  └──────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Model Stack (Specialized, Not General)

### Image Generation Models

| Task | Base Model | Specialized Add-on | Why This Combo |
|------|-----------|-------------------|----------------|
| **Coloring Pages** | SDXL Base 1.0 | **ColoringBook.Redmond LoRA** | LoRA is specifically trained on coloring book line art. Produces thick, clean lines with no shading. SDXL's negative prompts give precise control. |
| **Wall Art** | Flux.1 Dev FP8 | (base model, no LoRA needed) | Flux produces the highest quality full-color art. 2:3 ratio support. No negative prompts needed. |
| **Upscaling** | 4x-UltraSharp | — | Best general-purpose upscaler for print. Handles both line art and color. |

### Text/Writing Model

| Task | Model | Why |
|------|-------|-----|
| **All writing tasks** | Qwen 2.5 14B (via Ollama) | Best quality-per-VRAM for 12GB. Handles product descriptions, SEO, keywords. Can run alongside ComfyUI by using RAM offloading. |

### LoRA Fine-Tuning Plan (Phase 3)

| What | Details |
|------|---------|
| **Goal** | Train a custom "Bold & Easy" LoRA that produces the exact thick-line style that sells best on KDP |
| **Feasibility** | ✅ Possible on RTX 5070 12GB using Kohya_ss with fused backward pass + FP8 quantization + block swapping |
| **Training Data** | 20-50 high-quality coloring pages (curated from best outputs) |
| **Training Time** | 2-4 hours per LoRA on 12GB VRAM (slow but workable) |
| **RAM Needed** | 32GB+ (you have 64GB — perfect for block swapping) |
| **Tool** | FluxGym (user-friendly) or Kohya_ss (advanced control) |

> [!IMPORTANT]
> **Phase 1 uses pre-made LoRAs** (ColoringBook.Redmond). Custom LoRA training is Phase 3 — only after we've validated what styles sell best.

---

## Proposed Changes

### Component 1: Orchestrator Agent

#### [NEW] `D:\AI-Business\agent\orchestrator.py`
The main entry point. A Python CLI that runs the full pipeline:
```
python orchestrator.py --mode coloring-book --niche "bold-easy-lions" --pages 50
python orchestrator.py --mode wall-art --niche "botanical-kitchen" --count 6
python orchestrator.py --mode full-package --niche "bold-easy-dogs"
```

**Responsibilities:**
- Parse niche config from knowledge base
- Build ComfyUI workflow JSON with correct prompts + settings
- Submit to ComfyUI API, monitor via WebSocket
- Retrieve generated images
- Run post-processing (threshold for B&W, resize for print)
- Call PDF assembler for coloring books
- Call Ollama API for listing copy
- Package everything into output folder
- Log everything

#### [NEW] `D:\AI-Business\agent\comfyui_client.py`
Python wrapper around ComfyUI REST API:
- `start_comfyui()` — launch ComfyUI if not running
- `is_running()` — health check
- `queue_prompt(workflow_json)` — submit a generation job
- `wait_for_result(prompt_id)` — WebSocket listener
- `get_image(filename)` — download generated image
- `get_models()` — list available models

#### [NEW] `D:\AI-Business\agent\ollama_client.py`
Python wrapper around Ollama API:
- `generate(prompt, model="qwen2.5:14b")` — generate text
- `is_running()` — health check
- `list_models()` — available models

#### [NEW] `D:\AI-Business\agent\niche_manager.py`
Manages the niche knowledge base:
- `get_niche(name)` — load niche config
- `add_niche(name, data)` — save new niche
- `score_niche(data)` — calculate viability score
- `get_best_niches()` — ranked list

#### [NEW] `D:\AI-Business\agent\post_processor.py`
Image post-processing pipeline:
- `threshold_bw(image)` — force pure B&W for coloring pages
- `resize_print(image, sizes)` — multi-size export
- `add_margins(image, margin_px)` — safe area margins
- `upscale(image)` — call ComfyUI upscaler

---

### Component 2: Knowledge Base

#### [NEW] `D:\AI-Business\knowledge\niches.json`
```json
{
  "bold-easy-lions": {
    "name": "Bold & Easy Lions",
    "category": "coloring-book",
    "style": "bold-easy",
    "subjects": ["male lion", "lion cub", "lioness", "lion family"],
    "prompt_template": "coloring book page, bold and easy style, thick outlines...",
    "negative_prompt": "shading, grayscale, color...",
    "comfyui_settings": {"model": "sdxl", "lora": "ColoringBook.Redmond", "steps": 25},
    "kdp_settings": {"trim": "8.5x11", "pages": 50, "price": "$9.99"},
    "validated": true,
    "score": 85
  }
}
```

#### [NEW] `D:\AI-Business\knowledge\model_configs.json`
Best ComfyUI settings discovered per task (updated as we learn).

#### [NEW] `D:\AI-Business\knowledge\performance.json`
Track what sells: BSR, reviews, revenue per product. Informs future niche selection.

---

### Component 3: Diagnostics & Logging

#### [NEW] `D:\AI-Business\agent\diagnostics.py`
System health checker that any agent can call:
```python
python diagnostics.py              # Full system check
python diagnostics.py --gpu        # GPU status only
python diagnostics.py --comfyui    # ComfyUI health
python diagnostics.py --ollama     # Ollama health
python diagnostics.py --disk       # Disk space
python diagnostics.py --models     # Verify all models present
```

**Output format:** JSON for agents, colored text for humans.

#### [NEW] `D:\AI-Business\agent\logger.py`
Structured logging system:
- All operations logged to `logs/agent.log`
- Errors to `logs/errors.log`
- ComfyUI calls to `logs/comfyui.log`
- Ollama calls to `logs/ollama.log`
- JSON format for agent parsing + human-readable timestamps

---

### Component 4: Documentation & Skills (for agent handoff)

#### [NEW] `D:\AI-Business\README.md`
Master document — read this first.

#### [NEW] `D:\AI-Business\docs\AGENT_HANDOFF.md`
Step-by-step for a new agent taking over.

#### [NEW] `D:\AI-Business\docs\ARCHITECTURE.md`
Technical architecture reference.

#### [NEW] `D:\AI-Business\docs\MODEL_GUIDE.md`
All models, where they are, how to swap them.

#### [NEW] `D:\AI-Business\skills\SKILL_comfyui_generation.md`
How to use ComfyUI API (for agents).

#### [NEW] `D:\AI-Business\skills\SKILL_pdf_assembly.md`
How to assemble KDP PDFs.

#### [NEW] `D:\AI-Business\skills\SKILL_listing_writer.md`
How to use Ollama for listings.

#### [NEW] `D:\AI-Business\skills\SKILL_niche_research.md`
How to research and validate niches.

---

## Phase Execution Plan

### Phase 1: Agent Core (This Session)
- [x] ComfyUI installed on D: with all models
- [x] Ollama + Qwen 2.5 14B ready
- [x] Automation scripts (resize, PDF assembly)
- [x] 60 prompt templates
- [ ] Documentation suite (9 files — building now via subagent)
- [ ] Download SDXL base model + ColoringBook.Redmond LoRA
- [ ] Orchestrator agent (orchestrator.py)
- [ ] ComfyUI client (comfyui_client.py)
- [ ] Ollama client (ollama_client.py)
- [ ] Diagnostics tool (diagnostics.py)
- [ ] Logger (logger.py)
- [ ] Knowledge base (niches.json, model_configs.json)
- [ ] End-to-end test: generate one coloring book package

### Phase 2: Full Pipeline (Next Session)
- [ ] Niche manager with scoring
- [ ] Post-processor with quality checks
- [ ] Cover generation pipeline
- [ ] Etsy listing package generation
- [ ] Redbubble upload-ready exports
- [ ] Pinterest pin generation
- [ ] Full `--mode full-package` command

### Phase 3: LoRA Training (Week 2+)
- [ ] Install Kohya_ss / FluxGym
- [ ] Collect training data from best outputs
- [ ] Train custom "Bold & Easy" LoRA
- [ ] A/B test custom LoRA vs. generic
- [ ] Train niche-specific LoRAs (dogs, botanicals, etc.)

### Phase 4: Intelligence (Month 2+)
- [ ] Niche research automation (trend detection)
- [ ] Performance tracking (connect KDP/Etsy sales data)
- [ ] Auto-suggest next niche based on performance data
- [ ] Quality scoring (auto-reject bad generations)

---

## Open Questions

> [!IMPORTANT]
> 1. **Start building the orchestrator agent now?** The documentation is being created by a subagent. I can begin coding the core agent (orchestrator, ComfyUI client, diagnostics) immediately.
>
> 2. **SDXL model**: Should I also download the SDXL base model (~7GB) + ColoringBook.Redmond LoRA? SDXL is better for coloring pages than Flux (more control via negative prompts). Both models can coexist.
>
> 3. **Agent naming**: What should we call this platform? (e.g., "CreatorForge", "PrintPilot", "AssetEngine")
