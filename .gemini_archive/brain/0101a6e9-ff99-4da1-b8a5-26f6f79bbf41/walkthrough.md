# CBSE Class XII — Complete PYQ Archive & Prediction Papers

## 📦 Archive Summary

**Location:** `D:\Study Papers\Jeffrey\`

| Metric | Value |
|--------|-------|
| **Total Subjects** | 342 |
| **Total Files** | 4,242 |
| **Total Size** | 5.24 GB |
| **PYQ Years** | 2022, 2023, 2024, 2025, 2026 |
| **SQP Years** | 2020-21 through 2025-26 |
| **Marking Schemes** | 2022, 2023, 2024, 2025, 2026 |

### Sources Used
- **cbse.gov.in** — Official board exam question papers (ZIP archives)
- **cbseacademic.nic.in** — Sample Question Papers + Marking Schemes
- **cbse.gov.in/marking-scheme** — Official marking schemes for all subjects

> [!NOTE]
> Pre-2022 papers (2016, 2018, 2019, 2020) were discovered to exist on cbse.gov.in but were not bulk-downloaded in this session. They can be fetched using the same scripts.

---

## 🔮 Prediction Papers (2027 Board Exam)

**12 prediction papers** generated across 4 subjects, each analyzed from 5 years of board papers + 6 years of SQPs.

---

### ⚛️ Physics — 3 Papers

| Paper | File | Size |
|-------|------|------|
| Paper 1 | [Physics_Prediction_Paper_1.md](file:///D:/Study Papers/Jeffrey/Physics/Prediction Papers/Physics_Prediction_Paper_1.md) | 15.8 KB |
| Paper 2 | [Physics_Prediction_Paper_2.md](file:///D:/Study Papers/Jeffrey/Physics/Prediction Papers/Physics_Prediction_Paper_2.md) | 15.8 KB |
| Paper 3 | [Physics_Prediction_Paper_3.md](file:///D:/Study Papers/Jeffrey/Physics/Prediction Papers/Physics_Prediction_Paper_3.md) | 17.0 KB |

**Format:** 33 questions, 70 marks, 3 hours
- Section A: 16 MCQs (12 standard + 4 Assertion-Reasoning) = 16 marks
- Section B: 5 × 2 marks = 10 marks
- Section C: 7 × 3 marks = 21 marks
- Section D: 2 × 4 marks (case-based) = 8 marks
- Section E: 3 × 5 marks = 15 marks

**Case Studies:** Cardiac defibrillators, optical fibres, induction cooktops, solar cells, MRI, nuclear power

---

### 🧪 Chemistry — 3 Papers

| Paper | File | Size |
|-------|------|------|
| Paper 1 | [Chemistry_Prediction_Paper_1.md](file:///D:/Study Papers/Jeffrey/Chemistry/Prediction Papers/Chemistry_Prediction_Paper_1.md) | 17.7 KB |
| Paper 2 | [Chemistry_Prediction_Paper_2.md](file:///D:/Study Papers/Jeffrey/Chemistry/Prediction Papers/Chemistry_Prediction_Paper_2.md) | 17.5 KB |
| Paper 3 | [Chemistry_Prediction_Paper_3.md](file:///D:/Study Papers/Jeffrey/Chemistry/Prediction Papers/Chemistry_Prediction_Paper_3.md) | 19.3 KB |

**Format:** 33 questions, 70 marks, 3 hours
- Same section structure as Physics
- Covers: Solutions, Electrochemistry, Chemical Kinetics, d-block, Coordination Compounds, Organic Chemistry, Polymers, Biomolecules

**Case Studies:** Corrosion, Crystal Field Theory, drug classification, SN1/SN2 mechanisms

---

### 📐 Mathematics — 3 Papers

| Paper | File | Size |
|-------|------|------|
| Paper 1 | [Maths_Prediction_Paper_1.md](file:///D:/Study Papers/Jeffrey/Mathematics/Prediction Papers/Maths_Prediction_Paper_1.md) | 14.5 KB |
| Paper 2 | [Maths_Prediction_Paper_2.md](file:///D:/Study Papers/Jeffrey/Mathematics/Prediction Papers/Maths_Prediction_Paper_2.md) | 13.6 KB |
| Paper 3 | [Maths_Prediction_Paper_3.md](file:///D:/Study Papers/Jeffrey/Mathematics/Prediction Papers/Maths_Prediction_Paper_3.md) | 13.8 KB |

**Format:** 38 questions, 80 marks, 3 hours
- Section A: 20 × 1 mark (18 MCQs + 2 A-R) = 20 marks
- Section B: 5 × 2 marks = 10 marks
- Section C: 6 × 3 marks = 18 marks
- Section D: 4 × 5 marks = 20 marks
- Section E: 3 × 4 marks (case-based) = 12 marks

**Case Studies:** Manufacturing LPP, pool/track optimization, Bayes' theorem (hospitals, factories)

---

### 📖 English Core — 3 Papers

| Paper | File | Size |
|-------|------|------|
| Paper 1 | [English_Core_Prediction_Paper_1.md](file:///D:/Study Papers/Jeffrey/English Core/Prediction Papers/English_Core_Prediction_Paper_1.md) | 25.6 KB |
| Paper 2 | [English_Core_Prediction_Paper_2.md](file:///D:/Study Papers/Jeffrey/English Core/Prediction Papers/English_Core_Prediction_Paper_2.md) | 26.0 KB |
| Paper 3 | [English_Core_Prediction_Paper_3.md](file:///D:/Study Papers/Jeffrey/English Core/Prediction Papers/English_Core_Prediction_Paper_3.md) | 28.8 KB |

**Format:** 13 questions, 80 marks, 3 hours
- Section A (Reading): 22 marks — prose passage + case study with data table
- Section B (Writing): 18 marks — notice, invitation, letter, article/report
- Section C (Literature): 40 marks — poetry/prose extracts, short answers, long answers

**Literature covered:** All Flamingo chapters, all Vistas chapters, all 6 prescribed poems

---

## 🛠️ Scripts (saved in archive)

| Script | Location | Purpose |
|--------|----------|---------|
| [download_cbse_pyqs.ps1](file:///D:/Study Papers/Jeffrey/_scripts/download_cbse_pyqs.ps1) | `_scripts\` | Main PYQ downloader (4 parallel batches) |
| [download_additional.ps1](file:///D:/Study Papers/Jeffrey/_scripts/download_additional.ps1) | `_scripts\` | SQP + Marking Scheme + older year downloader |
| [cleanup_archive.ps1](file:///D:/Study Papers/Jeffrey/_scripts/cleanup_archive.ps1) | `_scripts\` | Folder merge/cleanup tool |

> [!TIP]
> To re-run downloads for any updates, execute: `powershell -ExecutionPolicy Bypass -File "_scripts\download_cbse_pyqs.ps1" -BatchId "1"`
