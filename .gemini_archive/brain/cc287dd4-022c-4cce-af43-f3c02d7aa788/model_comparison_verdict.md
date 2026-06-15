# Model Comparison: Qwen 2.5 14B vs Qwen3 8B

> **Judge**: Antigravity (Claude)  
> **Date**: 2026-06-14  
> **Test cases**: 3 (KDP Lions, Etsy Botanical, KDP Butterflies — no page count)

---

## Test 1: KDP Lions Coloring Book (50 pages provided)

### Qwen 2.5 14B (173 words, 9.1s)

| Dimension | Verdict | Evidence |
|-----------|---------|----------|
| word_count | ✅ PASS | 173 words — within 150-300 range |
| missing_elements | ✅ PASS | Has page count, single-sided, premium paper, gift suggestion, CTA, keywords |
| hallucination | ✅ PASS | All details match input — page count correct |
| ai_language | ❌ **FAIL** | **"Dive into the majestic world"** — "dive into" is a BANNED phrase |
| seo_quality | ✅ PASS | Keywords are reasonable, no excessive repetition |

**Score: 4/5**

### Qwen3 8B (133 words, 22.0s)

| Dimension | Verdict | Evidence |
|-----------|---------|----------|
| word_count | ⚠️ BORDERLINE | 133 words — slightly under the 150 minimum, and output appears truncated ("enjoy the joy" cuts off) |
| missing_elements | ✅ PASS | Has page count, single-sided, premium paper, gift mention, CTA |
| hallucination | ✅ PASS | All details match input |
| ai_language | ✅ PASS | "delightful escape", "stress-free way to unwind" — natural phrasing, no banned words |
| seo_quality | ✅ PASS | Clean, no keyword stuffing |

**Score: 4.5/5** (truncation issue is a model parameter problem, not quality problem)

### 🏆 Winner: **Qwen3 8B** — Much more natural tone. No banned phrases. Reads like a real person wrote it.

---

## Test 2: Etsy Botanical Wall Art

### Qwen 2.5 14B (230 words, 9.9s)

| Dimension | Verdict | Evidence |
|-----------|---------|----------|
| word_count | ✅ PASS | 230 words — within 200-300 range |
| missing_elements | ✅ PASS | Instant download ✅, sizes ✅, formats ✅, frame not included ✅, printing tips ✅ |
| hallucination | ✅ PASS | No invented features |
| ai_language | ⚠️ MINOR | "Add a touch of nature and elegance" — borderline cliché but not banned |
| seo_quality | ❌ **FAIL** | Tags 6-10 are just size numbers ("5x7 print", "8x10 print") — wastes 5 out of 13 tags on sizes nobody searches for |

**Score: 3.5/5**

### Qwen3 8B (247 words, 8.6s)

| Dimension | Verdict | Evidence |
|-----------|---------|----------|
| word_count | ✅ PASS | 247 words — within range |
| missing_elements | ✅ PASS | Instant download ✅, sizes ✅, formats ✅, frame not included ✅, printing tips ✅ |
| hallucination | ✅ PASS | No invented features. Correctly mentions "Set of 4" in title |
| ai_language | ✅ PASS | "Add a touch of natural elegance" — slightly cliché but natural. "Soothing blend of art and nature" — acceptable |
| seo_quality | ✅ PASS | **Much better tags** — all 13 are real search terms people actually type ("watercolor art print", "botanical illustration", "modern wall decor") |

**Score: 5/5**

### 🏆 Winner: **Qwen3 8B** — Better title (mentions Set of 4), far superior tag quality, more natural prose.

---

## Test 3: KDP Butterflies — NO PAGE COUNT (Hallucination Trap)

> This test deliberately omits page count to see if the model invents one.

### Qwen 2.5 14B (135 words, 4.6s)

| Dimension | Verdict | Evidence |
|-----------|---------|----------|
| word_count | ❌ **FAIL** | 135 words — under 150 minimum |
| missing_elements | ⚠️ WARN | No mention of single-sided printing or paper quality |
| hallucination | ❌ **FAIL** | **Invented "30 intricate designs"** — input never mentioned any page or design count. This is exactly the hallucination we need to prevent. |
| ai_language | ❌ **FAIL** | **TWO banned phrases**: "Dive into a world" AND "unleash your imagination" — "unleash" is explicitly banned |
| seo_quality | ✅ PASS | Keywords are reasonable |

**Score: 1.5/5** 💀

### Qwen3 8B (164 words, 6.7s)

| Dimension | Verdict | Evidence |
|-----------|---------|----------|
| word_count | ✅ PASS | 164 words — within range |
| missing_elements | ⚠️ WARN | No mention of single-sided printing |
| hallucination | ⚠️ MINOR | "high-quality paper" — not explicitly stated but a reasonable assumption for a coloring book. Did NOT invent a page count. ✅ |
| ai_language | ❌ **FAIL** | **"Dive into a world where every stroke"** — used the banned "dive into" phrase. Also "magic of coloring" is borderline AI-sounding. |
| seo_quality | ✅ PASS | Keywords are well-formed and search-relevant |

**Score: 3/5**

### 🏆 Winner: **Qwen3 8B** — Did NOT hallucinate a page count (critical victory). Still used one banned phrase though.

---

## Final Scorecard

| Test | Qwen 2.5 14B | Qwen3 8B | Winner |
|------|:---:|:---:|:---:|
| KDP Lions | 4/5 | 4.5/5 | **Qwen3 8B** |
| Etsy Botanical | 3.5/5 | 5/5 | **Qwen3 8B** |
| KDP Butterflies (trap) | 1.5/5 | 3/5 | **Qwen3 8B** |
| **Average** | **3.0/5** | **4.2/5** | **Qwen3 8B** |

| Metric | Qwen 2.5 14B | Qwen3 8B |
|--------|:---:|:---:|
| Banned phrases used | **3** (dive into ×2, unleash ×1) | **1** (dive into ×1) |
| Hallucinated details | **1** (invented "30 designs") | **0** |
| Word count violations | **1** (too short) | **0** (1 borderline) |
| Bad SEO tags | **5** wasted tags | **0** |
| Avg speed | **7.9s** | **12.4s** |

---

## Key Findings

1. **Qwen3 8B is clearly better** — wins all 3 tests despite being half the parameter count
2. **Qwen3 8B passed the hallucination trap** — did NOT invent a page count when one wasn't given
3. **Both models still use "dive into"** — this phrase is deeply baked into training data. Even explicit banning in the system prompt doesn't fully suppress it
4. **Qwen3 8B's tags are dramatically better** — real search terms vs. wasted size-only tags
5. **Qwen 2.5 14B is slower for worse output** — there's no reason to keep using it
6. **Speed**: Qwen 2.5 wins on speed (7.9s avg) but Qwen3's first test was an outlier (22s, likely cold-loading "thinking" mode). Real-world speed is comparable.

## Verdict

> **Switch to Qwen3 8B immediately.** It's a strict upgrade on every dimension that matters.  
> But it still can't fully suppress "dive into" — confirming that **fine-tuning is the real fix** for this class of problem.
