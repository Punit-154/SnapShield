# AGENT KNOWLEDGE TRANSFER PROMPT

Copy and paste everything below this line to any AI agent:

---

You are receiving a knowledge transfer from another AI agent working on an automated AI-powered passive income platform. This platform generates KDP coloring books and Etsy digital wall art using local AI models on an RTX 5070 (12GB VRAM, Windows, 64GB RAM).

## WHAT THIS RESEARCH IS FOR

We are building a fully automated local pipeline that:
1. Takes a niche (e.g. "owls") and generates 50-page coloring books + wall art
2. Uses ComfyUI for image generation (SDXL + LoRAs for coloring pages, Flux for wall art)
3. Uses a 4-stage multi-model LLM pipeline for product listing generation:
   - Stage 1 PLANNER: Niche analysis, structured JSON spec output (Qwen 3.6 35B-A3B MoE)
   - Stage 2 WRITER: Creative product listing copy (Gemma 4 12B or fine-tuned marketing model)
   - Stage 3 CHECKER: Quality gate — hallucination detection, banned phrase check, tone analysis (Hybrid: Python rules + Vectara HHEM + tiny classifier)
   - Stage 4 SEO: Keyword and tag generation (Qwen 3.6 35B-A3B)
4. Assembles PDFs to KDP specs (8.5x11, 300DPI, bleed margins)
5. Packages everything with SEO-optimized listings for upload

The system is designed to be maintained by AI agents. All documentation is written for both humans and agents.

## YOUR TASK

Read as many of the files below as you find relevant. Extract anything useful for your own capabilities — patterns, techniques, data sources, tool configurations, model recommendations, training approaches, or architectural insights. Summarize what you found useful and what you would do differently.

After reading, send me back a similar prompt containing:
1. Your own research findings or knowledge that could improve this project
2. Any suggestions, corrections, or optimizations you identified
3. Any additional data sources, models, or frameworks I may have missed

## FILES TO READ

### Research & Analysis (in conversation artifacts directory)

1. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\market_research.md
   - Initial niche market research for KDP coloring books and Etsy wall art
   - Niche scoring criteria, BSR analysis, seasonal planning

2. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\assert_results.md
   - Microsoft ASSERT framework evaluation results
   - Model quality scoring methodology

3. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\assert_manual_evaluation.md
   - Manual ASSERT evaluation with Claude as judge model
   - Detailed scoring rubric for listing quality

4. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\model_comparison_verdict.md
   - A/B test results: Qwen 2.5 14B vs Qwen 3 8B
   - Qwen 3 8B won (4.2/5 vs 3.0/5) despite being smaller

5. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\model_selection.md
   - Latest model landscape as of June 14, 2026
   - Covers Gemma 4, Qwen 3.5/3.6, Phi-4-reasoning, Llama 4 Scout/Maverick
   - VRAM constraints analysis for RTX 5070 (12GB)

6. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\model_pipeline_plan.md
   - Multi-model pipeline architecture design
   - 4-stage pipeline: Planner, Writer, Checker, SEO
   - VRAM flow analysis, sequential model loading via Ollama
   - Why multi-model beats single-model for this task

7. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\model_sourcing_plan.md
   - Per-stage model sourcing: pre-trained options vs custom training
   - Discovered marketeam/LLa-Marketing (43B token marketing corpus base)
   - Vectara HHEM hallucination detector (439MB, pre-trained)
   - Training infrastructure options (local RTX 5070, free Colab T4, Kaggle P100)

8. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\training_data_inventory.md
   - Complete dataset inventory: 1.4M+ records across 18+ sources
   - Writer data: 700K+ (Amazon descriptions, AdvertiseGen, Shopify catalogue)
   - Checker data: 523K+ (AI vs human text pairs, Defactify)
   - SEO data: 181K+ (search benchmarks, Etsy tags, synthetic queries)
   - Pre-trained marketing models from Marketeam (LLa-Marketing 8B, Gem-Marketing 3B, Phi-Marketing 4B)

9. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\agentic_ai_research.md
   - Latest agentic AI frameworks research (June 2026)
   - 6 specific improvements: LangGraph state machine, DSPy prompt optimization, self-improving trace analysis, MCP tool protocol, RouteLLM model routing, production guardrails
   - Implementation code for each improvement
   - Architecture diagram of the improved system

### Plans & Tracking

10. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\implementation_plan.md
    - Master implementation plan for the entire platform
    - Phase breakdown with dependencies

11. C:\Users\joel0\.gemini\antigravity\brain\cc287dd4-022c-4cce-af43-f3c02d7aa788\task.md
    - Current task checklist and progress tracker
    - What is done, in progress, and planned

### Project Documentation (on D: drive)

12. D:\AI-Business\README.md
    - Master project README with architecture overview, directory structure, how to run

13. D:\AI-Business\docs\AGENT_HANDOFF.md
    - Detailed handoff protocol for AI agents taking over this project
    - System state checklist, API endpoints, error recovery

14. D:\AI-Business\docs\ARCHITECTURE.md
    - Technical architecture: components, data flow, API contracts, output specs

15. D:\AI-Business\docs\MODEL_GUIDE.md
    - All AI models: image gen (SDXL, Flux), text (Qwen, Gemma), upscalers, LoRAs

16. D:\AI-Business\skills\SKILL_comfyui_generation.md
    - How to use ComfyUI API for image generation (POST http://127.0.0.1:8188/prompt)

17. D:\AI-Business\skills\SKILL_pdf_assembly.md
    - PDF assembly: KDP specs, CLI arguments, verification

18. D:\AI-Business\skills\SKILL_listing_writer.md
    - Ollama API usage, prompt templates, SEO rules, banned phrases list

19. D:\AI-Business\skills\SKILL_niche_research.md
    - Niche validation criteria (BSR < 100K, < 200 reviews, $7.99+ price)

### Training Data & Scripts

20. D:\AI-Business\training\listings_gold_v1.json
    - 16 gold-standard training examples (instruction/output pairs)
    - These are the ground truth for what a perfect listing looks like

21. D:\AI-Business\training\scripts\download_datasets.py
    - Script to download all HuggingFace datasets to D:\AI-Business\training\raw\

22. D:\AI-Business\training\scripts\train_writer_local.py
    - Local QLoRA training script using Unsloth on RTX 5070
    - Supports multiple base models, exports to GGUF for Ollama

23. D:\AI-Business\training\notebooks\train_writer_colab.ipynb
    - Google Colab notebook for training Writer LoRA on marketeam/LLa-Marketing 8B

24. D:\AI-Business\training\notebooks\train_checker_colab.ipynb
    - Google Colab notebook for training AI text detection classifier on DeBERTa

## KEY CONSTRAINTS

- Everything runs locally on Windows, RTX 5070 (12GB VRAM), 64GB RAM
- No files on C: drive — everything on D:\AI-Business\ and D:\ComfyUI\
- Ollama at 127.0.0.1:11434, ComfyUI at 127.0.0.1:8188
- Models must fit in 12GB VRAM (7B-14B dense, or MoE with small active params)
- All listings must avoid AI-sounding phrases (see banned list in SKILL_listing_writer.md)
- Platform designed for AI agent maintenance — all docs are agent-readable

## CURRENT STATUS (as of June 14, 2026)

- Infrastructure: COMPLETE (all scripts, docs, skills files created)
- Model research: COMPLETE (latest June 2026 models identified)
- Pipeline design: COMPLETE (4-stage multi-model architecture)
- Training data: IN PROGRESS (datasets downloading from HuggingFace)
- Unsloth: INSTALLED (v2026.6.7 on local machine)
- Local training: PENDING (waiting for datasets to finish downloading)
- Cloud training: READY (Colab notebooks created, need to upload and run)
- Agentic improvements: RESEARCHED (LangGraph, DSPy, MCP — not yet implemented)

---

END OF KNOWLEDGE TRANSFER PROMPT
