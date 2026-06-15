# Training Data Inventory — Complete

> **Last Updated**: June 14, 2026  
> **Goal**: Sufficient data to train/fine-tune each pipeline stage model

---

## Stage 2: WRITER — Creative Listing Copy

This is where data volume matters most. The Writer needs to learn natural human writing style for product listings.

### Primary Datasets

| # | Dataset | Source | Records | Content | Use |
|---|---------|--------|--------:|---------|-----|
| 1 | **`Ateeqq/Amazon-Product-Description`** | HuggingFace | **421,000** | Cleaned Amazon product titles + descriptions. Filtered >200 chars. | Core training corpus — teaches product description structure |
| 2 | **`iarbel/amazon-product-data-filter`** | HuggingFace | **Large** | Structured: titles, bullet features, technical specs, categories | Teaches structured listing format |
| 3 | **`philschmid/amazon-product-descriptions-vlm`** | HuggingFace | **~50K** | Product names + descriptions paired with images | Multimodal; useful for learning visual-product description mapping |
| 4 | **`open-datalab/AdvertiseGen`** | HuggingFace | **114,000** | Product attributes → advertising copy | Core training for attribute-to-copy generation |
| 5 | **`Shopify/product-catalogue`** | HuggingFace | **10K–100K** | Product title, description, brand, category, image | Real e-commerce listings with proper structure |
| 6 | **`McAuley-Lab/Amazon-Reviews-2023`** | HuggingFace | **Millions** | Product metadata + reviews (Books, Arts & Crafts categories) | Filter to "coloring book" and "wall art" for domain-specific data |
| 7 | **`llm-wizard/Product-Descriptions-and-Ads`** | HuggingFace | **100** | GPT-4 generated clothing product descriptions + ads | Few-shot examples of high-quality AI copy |

### Custom Generated Data

| # | Dataset | Method | Records | Content |
|---|---------|--------|--------:|---------|
| 8 | **Claude Gold Standard v1** | Written by me (Claude) | **15** | Perfect KDP/Etsy listings following all rules |
| 9 | **Claude Gold Standard v2** | Written by me | **50+** | More niches, edge cases, varied formats |
| 10 | **Synthetic Negative Examples** | Generate with vanilla LLMs | **50+** | Bad listings with violations (for DPO training) |
| 11 | **Real Top-Seller Scrape** | Web search for best-selling listings | **100+** | Actual #1 Amazon/Etsy listings in our niches |

### Pre-Trained Marketing Base Models (instead of generic Gemma/Qwen)

| # | Model | Source | Size | Trained On |
|---|-------|--------|------|-----------|
| 12 | **`marketeam/LLa-Marketing`** | HuggingFace | **8B** | LLaMA-3-8B continually pre-trained on **43 BILLION tokens** of marketing corpus |
| 13 | **`marketeam/Qwen-Marketing`** | HuggingFace | **~8B** | Qwen base fine-tuned for marketing reasoning + campaign copy |
| 14 | **`marketeam/Gem-Marketing`** | HuggingFace | **3B** | Gemma base, marketing-specialized. Tiny, fast. |
| 15 | **`marketeam/Phi-Marketing`** | HuggingFace | **4B** | Phi base, marketing-specialized |

> [!IMPORTANT]
> **Game changer**: `marketeam/LLa-Marketing` was pre-trained on **43B marketing tokens**. This is a much better base for our Writer LoRA than generic Gemma 4 — it already understands marketing language deeply. We fine-tune THIS with our KDP/Etsy-specific LoRA on top.

### Writer Training Data Total

| Layer | Records | Purpose |
|-------|--------:|---------|
| Base model pre-training | 43B tokens (Marketeam) | Marketing language understanding |
| General product descriptions | ~585K | Product listing structure |
| Advertising copy pairs | ~114K | Attribute → copy generation |
| Domain-specific (coloring books, wall art) | ~500+ (filtered from millions) | Our exact niche |
| Gold standard examples | ~65+ | Our exact format and rules |
| Negative examples (DPO) | ~50+ | What NOT to do |
| **Total fine-tuning records** | **~700K+** | |

---

## Stage 3: CHECKER — AI Text Detection / Tone Classification

### Datasets for the "Does this sound like AI?" classifier

| # | Dataset | Source | Records | Content |
|---|---------|--------|--------:|---------|
| 1 | **`Defactify_Text_Dataset`** | HuggingFace | **73,000+** | NYT articles paired with synthetic versions from GPT-4o, LLaMA, Mistral, Gemma. Labeled human vs AI. |
| 2 | **`andythetechnerd03/AI-human-text`** | HuggingFace | **400,000+** | Large binary-labeled dataset (human vs AI). Popular benchmark. |
| 3 | **`artem9k/ai-text-detection-pile`** | HuggingFace | **~50K+** | Long-form text from GPT-2, GPT-3, ChatGPT, GPT-J vs human. |
| 4 | **`ahmadreza13/human-vs-Ai-generated-dataset`** | HuggingFace | **~10K+** | Community paired samples for detection |
| 5 | **Custom: Marketing-domain pairs** | We generate | **200+** | Our gold listings (human label) vs vanilla LLM listings (AI label) |

### Hallucination Detection

| # | Model/Dataset | Source | Type |
|---|---------------|--------|------|
| 6 | **`vectara/hallucination_evaluation_model`** | HuggingFace | **Pre-trained model** (439MB). Ready to use. Compares premise vs hypothesis. |
| 7 | **HaluEval-QA benchmark** | HuggingFace | Evaluation dataset for hallucination detection |

### Checker Training Data Total

| Component | Records |
|-----------|--------:|
| AI vs Human text classifier | **523,000+** |
| Domain-specific marketing pairs | **200+** |
| Hallucination detector | **Pre-trained (no training needed)** |

---

## Stage 4: SEO OPTIMIZER — Keywords & Tags

### Datasets for Search Term / Keyword Generation

| # | Dataset | Source | Records | Content |
|---|---------|--------|--------:|---------|
| 1 | **`McAuley-Lab/Amazon-Reviews-2023`** | HuggingFace | **Millions** | Product metadata with categories, search terms, browse nodes |
| 2 | **`apexlearningcurve/Amazon-Search-Benchmark`** | HuggingFace | **Large** | Product titles + descriptions + categories for search relevance |
| 3 | **`EmbeddingStudio/synthetic-search-queries`** | HuggingFace | **~50K+** | Natural language search queries |
| 4 | **`Shopify/product-catalogue`** | HuggingFace | **10K–100K** | Products with hierarchical taxonomy labels |
| 5 | **`MuratcanKoylan/MarketingStructuralPrompts`** | HuggingFace | **4,600** | Marketing-specific prompts for various channels |
| 6 | **Kaggle: Etsy Shops Dataset** | Kaggle | **20,000** | Shop attributes and listing data |
| 7 | **Kaggle: Etsy Listings Dataset** | Kaggle | **~10K+** | Listing names, prices, review data |
| 8 | **Amazon auto-suggest scrape** | Custom script | **500+** | Real-time search queries for our niches |
| 9 | **Etsy search suggest scrape** | Custom script | **500+** | Real-time tag suggestions for wall art + coloring books |

### Pre-Trained SEO Models

| # | Model | Source | Type |
|---|-------|--------|------|
| 10 | **`dejanseo/ecommerce-taxonomy-classifier`** | HuggingFace | Classifies products into hierarchical taxonomy |
| 11 | **`Ateeqq/product-description-generator`** | HuggingFace | T5-based product description generator |
| 12 | **`Marqo/marqo-ecommerce-embeddings-L`** | HuggingFace | Semantic search embeddings for e-commerce |
| 13 | **`prhegde/query-product-relevance-model-ecommerce`** | HuggingFace | Query → product relevance scoring |

### SEO Training Data Total

| Source | Records |
|--------|--------:|
| Amazon product search data | **100K+** (filtered) |
| Etsy listing/tag data | **30K+** |
| Synthetic search queries | **50K+** |
| Custom niche scrapes | **1,000+** |
| **Total** | **~181K+** |

---

## Grand Total: All Training Data

| Stage | Pre-existing Data | Custom Data | Pre-trained Models |
|-------|------------------:|------------:|:--:|
| **Planner** | N/A (no training) | N/A | Qwen 3.6 as-is |
| **Writer** | ~700K records | ~165+ gold/negative | **marketeam/LLa-Marketing** (43B token base) |
| **Checker** | ~523K AI-vs-human pairs | ~200 domain pairs | **Vectara HHEM** (pre-trained) |
| **SEO** | ~181K search/product records | ~1K niche scrapes | **dejanseo taxonomy classifier** |
| **TOTAL** | **~1.4 million records** | **~1,365+** | **4 specialized models** |

---

## Revised Writer Strategy

Given the discovery of `marketeam/LLa-Marketing` (43B marketing tokens), the training stack changes:

```
OLD:  Generic Gemma 4 12B  →  LoRA on 100 examples  →  hope for the best
NEW:  LLa-Marketing 8B    →  LoRA on 700K+ listings →  guaranteed domain fit
      (already knows        (product descriptions     (our exact niche
       marketing)            + ad copy)                + rules)
```

### Training Stack Per Stage

| Stage | Base Model | LoRA Data | Training Time | Where |
|-------|-----------|-----------|:---:|-------|
| **Writer** | `marketeam/LLa-Marketing` 8B | 700K listings + 65 gold | ~3-4 hrs | Local GPU or Colab |
| **Checker** | `DeBERTa-v3-base` (classifier) | 523K AI/human pairs | ~1 hr | Free Colab T4 |
| **SEO** | `Qwen 3.6 35B-A3B` or `qwen3.5:4b` | 181K search terms | ~1 hr | Local GPU |

> [!NOTE]  
> We don't need to train on ALL 700K records. A quality-filtered subset of 5K–10K high-quality listings + our gold examples is likely sufficient for LoRA. The rest serves as evaluation/validation data.

---

## Data Collection Scripts Needed

1. **`download_datasets.py`** — Pull all HuggingFace datasets to `D:\AI-Business\training\raw\`
2. **`filter_amazon_books.py`** — Filter McAuley-Lab data to coloring books + wall art categories
3. **`scrape_amazon_suggest.py`** — Collect auto-suggest keywords for target niches
4. **`scrape_etsy_suggest.py`** — Collect Etsy search suggestions for target niches
5. **`prepare_training_data.py`** — Format all data into Unsloth-compatible instruction/output pairs
6. **`generate_gold_v2.py`** — Script to systematically generate more Claude gold examples

Approve this dataset plan?
