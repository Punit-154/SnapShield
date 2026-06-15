# AgentOS — Implementation Tasks

## Phase 1: Foundation
- [x] Create project directory structure at `d:\agentos\`
- [x] Write `scripts/install.ps1` (Windows entry point)
- [x] Write `scripts/install.sh` (WSL-side installer)
- [x] Write `scripts/health-check.sh`
- [x] Write `scripts/uninstall.sh`
- [x] Write `config/defaults.yaml`
- [x] Write `config/config.yaml.template`
- [x] Write `.env.template`

## Phase 2: Router (Python)
- [x] Write `router/__init__.py`
- [x] Write `router/models.py` (Pydantic schemas)
- [x] Write `router/rules.py` (Layer 1: keyword rules)
- [x] Write `router/semantic.py` (Layer 2: embedding classifier)
- [x] Write `router/llm_classifier.py` (Layer 3: LLM fallback)
- [x] Write `router/router.py` (3-layer orchestrator)
- [x] Write `router/logger.py` (SQLite logging)
- [x] Write `router/config.py` (config loader)
- [x] Write `router/litellm_config.yaml`
- [x] Write `requirements.txt`

## Phase 3: Hermes Skills (10/10)
- [x] Write `skills/agentos-setup/SKILL.md`
- [x] Write `skills/joel-context/SKILL.md`
- [x] Write `skills/joel-context/context.md`
- [x] Write `skills/internship-scout/SKILL.md`
- [x] Write `skills/resume-tailor/SKILL.md`
- [x] Write `skills/code-review/SKILL.md`
- [x] Write `skills/portfolio-sync/SKILL.md`
- [x] Write `skills/study-tracker/SKILL.md`
- [x] Write `skills/email-triage/SKILL.md`
- [x] Write `skills/daily-briefing/SKILL.md`
- [x] Write `skills/git-commit/SKILL.md`

## Phase 4: n8n Workflows (4/4)
- [x] Write `n8n/internship-auto-apply.json`
- [x] Write `n8n/email-triage.json`
- [x] Write `n8n/daily-briefing.json`
- [x] Write `n8n/github-monitor.json`

## Phase 5: Documentation & Polish
- [x] Write `docs/ARCHITECTURE.md`
- [x] Write `docs/ROUTER.md`
- [x] Write `docs/SKILLS.md`
- [x] Write `docs/CONTRIBUTING.md`
- [x] Write `README.md`
- [x] Write `.gitignore`
- [x] Write `LICENSE`

## Extras
- [x] Skills library (`d:\portfolio-draft\docs\skills\`) — 7 skill docs, 208 KB
- [x] Personal database update (database.md, llm_context.md)
- [x] Agent handoff doc (3 copies)

## Remaining (for next agent)
- [ ] Actually run `install.ps1` to deploy to WSL
- [ ] Create Telegram bot via @BotFather
- [ ] Set up Gmail App Password for IMAP
- [ ] Fill in API keys in `~/.agentos/.env`
- [ ] Import n8n workflows into n8n editor
- [ ] Test portfolio website in browser
- [ ] Deploy portfolio to production
