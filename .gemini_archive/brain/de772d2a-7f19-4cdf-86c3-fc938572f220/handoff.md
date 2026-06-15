# 🤖 Agent Handoff Document

> **Date:** 2026-06-14  
> **Conversation ID:** `de772d2a-7f19-4cdf-86c3-fc938572f220`  
> **Conversation Log:** `C:\Users\joel0\.gemini\antigravity\brain\de772d2a-7f19-4cdf-86c3-fc938572f220\.system_generated\logs\transcript.jsonl`  
> **User:** Joel Alfred Israel  
> **Status:** Two active projects, one partially built by subagents

---

## 🧑 User Profile

Joel is a **2nd-year CS student at VIT Chennai** (CGPA 8.09/10). He prefers:
- CLI-first interaction, ThinkPad aesthetics, modular architectures
- Documentation-heavy output, leave `.md` files for future agents
- Multiple subagents for maximum parallelism
- Easter eggs and creative touches

### Key Accounts
| Platform | URL |
|---|---|
| GitHub | https://github.com/Joel01010/ |
| HuggingFace | https://huggingface.co/Joel01010 |
| Devfolio | https://devfolio.co/@joelalfred |
| Google Skills | https://www.skills.google/public_profiles/7dc6a9c6-f6ba-4302-84c4-5fa666a31087 |
| LinkedIn | www.linkedin.com/in/joel-alfred-israel-a24204315 |
| Gmail | joel010.alfred@gmail.com |
| VIT Email | joel.alfredisrael2024@vitstudent.ac.in |

### Preferred LLM Stack (Hybrid)
- **SambaNova/Xiaomi** (free API key) for complex tasks
- **GitHub Copilot** for coding
- **Ollama** (local) for small tasks / when cloud unavailable
- **Telegram** for mobile gateway (@BotFather bot — not yet created)

---

## 💻 System Specs

| Component | Value |
|---|---|
| **OS** | Windows 11 Pro x64 |
| **RAM** | 64 GB (38 GB free typically) |
| **GPU** | NVIDIA RTX 5070 (12 GB VRAM) |
| **WSL2** | Installed — archlinux (default), Arch, kali-linux, docker-desktop |
| **Docker Desktop** | Installed in WSL but CLI not in Windows PATH |
| **Node.js** | Available (used for portfolio Astro project) |
| **Python** | 3.13 (confirmed via __pycache__ cpython-313) |

---

## 📂 Project 1: Portfolio Website (`d:\portfolio-draft`)

### Status: ✅ FUNCTIONAL — needs polish

A ThinkPad-themed terminal portfolio built with Astro + React (xterm.js). Opens with a 3D laptop lid animation, then drops into a CLI with 20+ easter egg commands.

### Architecture
- **Framework:** Astro 5.18.2 + React islands
- **Terminal:** xterm.js via `client:only="react"`
- **Styling:** CSS (global.css, laptop.css, terminal.css, crt.css)
- **Dev server:** `npm run dev` → `localhost:4321`

### Key Files
| File | Purpose |
|---|---|
| `src/pages/index.astro` | Main page, lid open trigger |
| `src/components/Terminal.tsx` | xterm.js terminal with ASCII banner |
| `src/components/LaptopShell.astro` | ThinkPad E14 Gen 6 3D exterior |
| `src/terminal/commands.ts` | Command dispatcher |
| `src/terminal/easter.ts` | 20+ hidden commands (matrix, cuda, quantum, rust, wasm, sudo, btw, etc.) |
| `src/styles/laptop.css` | 3D CSS transforms, brushed metal, ThinkPad logo |
| `src/styles/global.css` | Design tokens, font size 16px |
| `src/styles/terminal.css` | Terminal padding |
| `src/styles/crt.css` | CRT scanline/distortion overlays |
| `legacy/` | Old portfolio code preserved here |

### Documentation (`d:\portfolio-draft\docs\`)
| File | Size | Content |
|---|---|---|
| `ARCHITECTURE.md` | 13 KB | Full system architecture |
| `CONTRIBUTING.md` | 10 KB | Contribution guide |
| `MAINTENANCE.md` | 12 KB | Maintenance playbook |
| `TECH_DEBT.md` | 11 KB | Known tech debt tracker |
| `AGENTS.md` | 12 KB | Agent collaboration guide |
| `components.md` | 11 KB | Component documentation |

### Personal Database (`d:\portfolio-draft\docs\personal\`)
| File | Size | Content |
|---|---|---|
| `database.md` | 5.8 KB | Identity, skills, projects, GitHub stats, USPs |
| `resume_raw.md` | 2.8 KB | Original resume (source of truth) |
| `resume_enhanced.md` | 4.3 KB | Enhanced resume for applications |
| `career_analytics.md` | 6.9 KB | Salary projections, skill gaps, target companies |
| `market_insights.md` | 8.0 KB | VIT placements, AI startups, GCCs, competitive analysis |
| `marketing_strategy.md` | 7.4 KB | LinkedIn, GitHub, blogging, cold outreach playbook |
| `self_improvement_plan.md` | 7.4 KB | 18-month learning roadmap |
| `llm_context.md` | 4.9 KB | LLM-ready context dump with market data |

### AgentOS Reference Docs (`d:\portfolio-draft\docs\agentos\`)
| File | Size | Content |
|---|---|---|
| `HERMES_REFERENCE.md` | 4.6 KB | Hermes v0.16.0 CLI, config, skills, gateway, tools |
| `ROUTER_REFERENCE.md` | 3.9 KB | Router design decisions, tools evaluated, architecture rationale |

### What's Left for Portfolio
- [ ] Test ThinkPad lid animation in browser (verify 780px wide shell responsiveness)
- [ ] Verify all 20+ easter egg commands work in terminal
- [ ] Build for production and deploy
- [ ] Add SEO meta tags, OpenGraph, sitemap

---

## 📂 Project 2: AgentOS (`d:\agentos`)

### Status: 🔄 PARTIALLY BUILT — needs completion

A Windows-first AI agent stack wrapping Hermes Agent with an ML+rules hybrid model router, installer scripts, n8n integration, Telegram gateway, email monitoring, and automated internship applications.

### Implementation Plan
Full plan is at: `C:\Users\joel0\.gemini\antigravity\brain\de772d2a-7f19-4cdf-86c3-fc938572f220\implementation_plan.md` (896 lines, 33 KB)

### What's DONE ✅

#### Router Module (100% complete — 9 files, all syntax-validated)
| File | Size | Status |
|---|---|---|
| `router/__init__.py` | 276 B | ✅ Re-exports AgentOSRouter, TaskType, ClassificationResult, RoutingDecision |
| `router/models.py` | 3.7 KB | ✅ Pydantic models + full RouterConfig schema |
| `router/config.py` | 5.5 KB | ✅ YAML loader with ${VAR} env resolution, deep merge |
| `router/rules.py` | 7.9 KB | ✅ Layer 1: 5 keyword sets, ~0ms |
| `router/semantic.py` | 7.0 KB | ✅ Layer 2: semantic-router + HuggingFace encoder, ~5-20ms |
| `router/llm_classifier.py` | 7.8 KB | ✅ Layer 3: litellm.acompletion(), ~100-300ms |
| `router/logger.py` | 7.1 KB | ✅ Async SQLite logging with stats/recent |
| `router/router.py` | 9.1 KB | ✅ 3-layer cascade with cache, privacy override |
| `requirements.txt` | 112 B | ✅ 6 deps: litellm, pydantic, pyyaml, semantic-router, sentence-transformers, aiosqlite |

#### Hermes Skills (8 of 10 complete)
| Skill | Status |
|---|---|
| `skills/agentos-setup/SKILL.md` (7.3 KB) | ✅ First-run wizard |
| `skills/joel-context/SKILL.md` (5.1 KB) | ✅ Permanent identity |
| `skills/joel-context/context.md` (5.4 KB) | ✅ Full profile text |
| `skills/internship-scout/SKILL.md` (7.3 KB) | ✅ Job search + scoring |
| `skills/resume-tailor/SKILL.md` (7.8 KB) | ✅ JD → tailored resume |
| `skills/code-review/SKILL.md` (8.9 KB) | ✅ Code quality review |
| `skills/portfolio-sync/SKILL.md` (5.2 KB) | ✅ Portfolio data sync |
| `skills/study-tracker/SKILL.md` (6.8 KB) | ✅ Learning progress |
| `skills/email-triage/SKILL.md` (7.6 KB) | ✅ Email categorization |

### What's NOT DONE ❌

#### Remaining Skills (2 of 10)
| Skill | Trigger | Brief |
|---|---|---|
| `skills/daily-briefing/SKILL.md` | Cron 8:30 AM | GitHub + email + jobs → Telegram digest |
| `skills/git-commit/SKILL.md` | "commit" | Analyze staged changes → conventional commit |

#### Installer Scripts (0% — not started)
| File | Purpose |
|---|---|
| `scripts/install.ps1` | Windows entry: check WSL2, install Ubuntu-26.04 as "agentos" distro, launch install.sh |
| `scripts/install.sh` | WSL-side: install Python/Node/Docker/Ollama/Hermes, clone repo to /opt/agentos, create systemd services, run first-time setup |
| `scripts/health-check.sh` | Verify all services (Ollama, LiteLLM :4000, n8n :5678, Hermes :9119) |
| `scripts/uninstall.sh` | Clean removal |
| `config/defaults.yaml` | Shipped defaults (don't edit) |
| `config/config.yaml.template` | User config template |
| `.env.template` | API key placeholders |

#### n8n Workflows (0% — agent hit rate limit)
| File | Purpose |
|---|---|
| `n8n/internship-auto-apply.json` | Daily 9 AM: scrape jobs → AI score → draft emails → log to Sheets → Telegram summary |
| `n8n/email-triage.json` | IMAP trigger → AI classify → Telegram for urgent |
| `n8n/daily-briefing.json` | 8:30 AM: GitHub + email + jobs → Telegram digest |
| `n8n/github-monitor.json` | Weekly: GitHub stats comparison → Telegram report |

#### Project Docs (0% — agent hit rate limit)
| File | Purpose |
|---|---|
| `docs/ARCHITECTURE.md` | System + router diagrams (Mermaid), port assignments |
| `docs/ROUTER.md` | 3-layer routing docs, config reference, tuning guide |
| `docs/SKILLS.md` | Skill creation guide, format reference |
| `docs/CONTRIBUTING.md` | Dev environment, code style, PR template |
| `README.md` | Project overview, quick start, features |
| `.gitignore` | Python + Node + secrets |
| `LICENSE` | MIT, Copyright 2026 Joel Alfred Israel |

---

## 🔧 How to Continue

### Option A: Complete AgentOS (recommended)

The fastest path is to re-run the failed/incomplete work:

**1. Complete remaining 6 skills:**
Use this prompt for a `skills_builder` subagent (or write manually):
```
Create 6 more Hermes skills at d:\agentos\skills\:
- code-review, portfolio-sync, study-tracker, email-triage, daily-briefing, git-commit
(See implementation_plan.md for full specs of each)
```

**2. Create n8n workflows + docs:**
This was the `workflows_docs_builder` agent that hit rate limit. Re-run with same prompt (see conversation log step where subagents were invoked with 3 detailed prompts — the 3rd prompt has the full spec for all 11 files).

**3. Write installer scripts:**
Never started. The implementation plan has full specs under "Section 2: Installer Scripts" including:
- `install.ps1` flags: `--dry-run`, `--uninstall`, `--no-ollama`, `--no-n8n`
- `install.sh` responsibilities: create non-root user, install 8 system deps, install Ollama + pull 2 models, install Hermes, clone repo, create venv, install n8n, create systemd services, symlink skills, copy configs
- Must be idempotent (re-runnable safely)

**4. Test the router:**
```bash
cd d:\agentos
python -c "from router import AgentOSRouter; print('Import OK')"
# Note: full test requires Ollama running for Layer 3
```

### Option B: Return to Portfolio

If the user asks about the portfolio instead:
- Dev server: `cd d:\portfolio-draft && npm run dev` → `localhost:4321`
- All source in `src/`, styles in `src/styles/`, terminal logic in `src/terminal/`
- Easter eggs in `src/terminal/easter.ts`

---

## 📐 Architecture Quick Reference

### AgentOS 3-Layer Router
```
Layer 1: Rules (0ms)       → keywords, length, privacy patterns
Layer 2: Semantic (5-20ms) → embedding similarity (HuggingFace, local)
Layer 3: LLM (100-300ms)   → Ollama/phi4, only for ambiguous cases (~5%)
Privacy Override            → sensitive patterns force Ollama local
LiteLLM Proxy (:4000)      → unified API, failover, caching
```

### Port Assignments
| Port | Service |
|---|---|
| 4000 | LiteLLM Proxy |
| 4321 | Portfolio dev server (Astro) |
| 5678 | n8n |
| 9119 | Hermes Dashboard |
| 11434 | Ollama |

### Config Locations (inside WSL "agentos" distro)
| Path | Content |
|---|---|
| `~/.hermes/config.yaml` | Hermes agent config |
| `~/.hermes/.env` | Hermes API keys |
| `~/.hermes/skills/` | Skills (symlinked to /opt/agentos/skills/) |
| `~/.agentos/config.yaml` | AgentOS router config |
| `~/.agentos/.env` | AgentOS secrets (SAMBANOVA_API_KEY, TELEGRAM_BOT_TOKEN, GMAIL_APP_PASSWORD, etc.) |
| `~/.agentos/logs/routing.sqlite` | Routing decision log |
| `/opt/agentos/` | AgentOS repo root (cloned from d:\agentos) |

### Key Config Schema (`~/.agentos/config.yaml`)
```yaml
identity:
  name: "Joel Alfred Israel"
router:
  method: "hybrid"
  confidence_threshold: 0.7
  routes:
    coding-complex: "sambanova"
    coding-simple: "ollama"
    testing: "sambanova"
    research: "sambanova"
    planning: "hermes"
    quick-edit: "ollama"
    privacy-sensitive: "ollama"
providers:
  ollama: { base_url: "http://localhost:11434", default_model: "llama3.1:8b" }
  sambanova: { base_url: "https://api.sambanova.ai/v1", api_key: "${SAMBANOVA_API_KEY}" }
privacy:
  sensitive_patterns: [password, secret, token, api_key, private_key, .env]
integrations:
  telegram: { enabled: true, bot_token: "${TELEGRAM_BOT_TOKEN}" }
  email:
    accounts:
      - { name: Gmail, imap_host: imap.gmail.com, username: joel010.alfred@gmail.com }
      - { name: VIT, imap_host: outlook.office365.com, username: joel.alfredisrael2024@vitstudent.ac.in }
  n8n: { enabled: true, base_url: "http://localhost:5678" }
```

---

## ⚠️ Open Questions for Joel

These were asked but NOT yet answered:
1. **Telegram Bot:** Has he created a bot via @BotFather? If not, include setup instructions.
2. **Gmail App Password:** Does he have 2FA on Gmail? Needed for IMAP App Password.
3. **VIT Email IMAP:** VIT uses Microsoft 365 — may need admin approval for IMAP.
4. **Internship Auto-Apply:** Confirmed as **draft-only** (Gmail Drafts, never auto-send).

---

## 📝 Key Design Decisions Made

1. **Ubuntu 26.04 LTS** in a new WSL distro named "agentos" — isolated from existing Arch/Kali
2. **LiteLLM** as unified proxy (not raw Ollama API) — handles failover + caching for free
3. **Semantic Router** (`aurelio-labs/semantic-router`) for Layer 2 — sub-20ms, fully local
4. **RouteLLM** kept as optional dependency — overkill for current binary strong/weak needs
5. **n8n over custom code** for email/job automation — visual workflows, 400+ integrations
6. **Draft-only emails** — never auto-send job applications, human reviews first
7. **SQLite for routing logs** — lightweight, no external DB dependency
8. **Hermes v0.16.0** — dashboard on :9119, gateway supports Telegram/Discord/Slack, 70+ built-in tools
9. **Skills as SKILL.md** — compatible with Hermes standard + agentskills.io open format
