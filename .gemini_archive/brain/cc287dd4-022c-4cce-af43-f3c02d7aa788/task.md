## Phase 1–4: COMPLETE ✅
*(Infrastructure, docs, ASSERT eval, core scripts — all done)*

## Phase 5: Model Selection & Optimization
- [x] A/B comparison: Qwen 2.5 14B vs Qwen3 8B → Qwen3 8B wins (4.2 vs 3.0/5)
- [x] Research latest models (June 2026): Gemma 4, Qwen 3.5/3.6, Phi-4, Llama 4
- [x] Design 4-stage multi-model pipeline (Planner → Writer → Checker → SEO)
- [x] Identify per-stage model sourcing (pre-trained + custom train plans)
- [x] Discover marketeam/LLa-Marketing (43B marketing token base model)

## Phase 6: Training Data Collection
- [/] Download HuggingFace datasets (9 datasets, running now)
  - Amazon Product Descriptions (421K)
  - AdvertiseGen (114K ad copy pairs)
  - Shopify Product Catalogue (10K-100K)
  - AI vs Human text (400K+ for Checker)
  - Defactify (73K NYT vs AI)
  - Amazon Search Benchmark (for SEO)
  - Synthetic search queries
  - Marketing prompts (4.6K)
  - GPT-4 product ads (100)
- [x] Claude gold standard batch 1 (16 examples)
- [ ] Claude gold standard batch 2 (50+ more examples)
- [ ] Scrape Amazon auto-suggest keywords
- [ ] Scrape Etsy search suggestions

## Phase 7: Local Training (RTX 5070, 12GB)
- [/] Install Unsloth + dependencies (installing now)
- [x] Create local Writer training script (train_writer_local.py)
- [x] Prepare gold standard training data (16 records ready)
- [ ] Train Writer LoRA on Gemma 3 4B base (local, ~2 hrs)
- [ ] Export Writer to GGUF → import into Ollama
- [ ] Train SEO LoRA on Qwen 3.5 4B base (local, ~30 min)

## Phase 8: Cloud Training (Free Colab/Kaggle)
- [x] Create Writer Colab notebook (marketeam/LLa-Marketing 8B)
- [x] Create Checker Colab notebook (DeBERTa AI text classifier)
- [ ] Upload gold data to Colab
- [ ] Run Writer training on Colab T4
- [ ] Run Checker training on Colab T4
- [ ] Download trained models

## Phase 9: Integration & Testing
- [ ] Import all trained models into Ollama
- [ ] Install Vectara HHEM hallucination detector
- [ ] Build multi-model pipeline orchestrator
- [ ] End-to-end test: full KDP generation pipeline
- [ ] ASSERT eval with Claude as judge
