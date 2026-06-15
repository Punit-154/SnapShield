# Portfolio Agent Prompt

> **Instructions:** Copy everything below the line and paste it as the opening message to a new agent.

---

## Prompt

You are taking over an existing **ThinkPad-themed terminal portfolio website** for Joel Alfred Israel. The project is fully functional but needs polish, content updates, and deployment.

### Project Location
```
d:\portfolio-draft
```

### First Steps
1. Read the full handoff: `d:\portfolio-draft\PORTFOLIO_HANDOFF.md`
2. Read the architecture: `d:\portfolio-draft\docs\ARCHITECTURE.md`
3. Read Joel's profile: `d:\portfolio-draft\docs\personal\database.md`
4. Start the dev server: `cd d:\portfolio-draft && npm run dev` → http://localhost:4321
5. Open the site in a browser to see the current state.

### What This Project Is
A **single-page portfolio website** built as a 3D ThinkPad laptop that opens to reveal an interactive terminal. Built with:
- **Astro 5.x** (static site generator, islands architecture)
- **React 19** (terminal + boot overlay components, hydrated via `client:only="react"`)
- **xterm.js 5.5** (real terminal emulator in the browser)
- **Vanilla CSS** (3D transforms, CRT effects, ThinkPad aesthetic)

The terminal supports 20+ commands including: `help`, `about`, `projects`, `skills`, `neofetch`, `theme`, `snake` (game), `chat` (AI), plus hidden easter eggs (cowsay, cmatrix, fortune, etc.).

### Key Files
```
src/components/LaptopShell.astro  ← 3D laptop exterior
src/components/Terminal.tsx       ← xterm.js terminal (main component)
src/components/BootOverlay.tsx    ← POST boot animation
src/terminal/commands.ts          ← Command dispatcher (20+ commands)
src/terminal/easter.ts            ← Easter egg commands
src/terminal/neofetch.ts          ← System info display
src/data/portfolio.json           ← Portfolio content data
src/styles/laptop.css             ← 3D transforms, lid animation
src/styles/crt.css                ← CRT scanline effects
```

### What Needs Doing (in priority order)

**Priority 1 — Deploy:**
- Add `.gitignore` (node_modules, dist, .astro, .env)
- Clean up git: `git add -A && git commit -m "chore: migrate to Astro 5"`
- Add Vercel adapter: `npx astro add vercel`
- Deploy: `npx vercel`

**Priority 2 — Content:**
- Update `src/data/portfolio.json` — add the AgentOS project:
  ```json
  {
    "name": "AgentOS",
    "description": "Windows-first AI agent stack with 3-layer model routing, 10 Hermes skills, n8n automation, and idempotent installer",
    "tech": ["Python", "Pydantic", "LiteLLM", "Hermes Agent", "n8n", "WSL2", "PowerShell", "Bash"],
    "links": { "github": "https://github.com/Joel01010/agentos" }
  }
  ```
- Fix unused variable in `src/terminal/neofetch.ts:116` (remove `_owner` line)
- Generate and add favicon + OG meta images to `public/`

**Priority 3 — Polish:**
- Test mobile responsiveness (the laptop CSS uses fixed pixel sizes)
- Run Lighthouse audit and optimize (lazy load fonts, compress assets)
- Verify all terminal commands work correctly
- Add more easter eggs that showcase Joel's skills (e.g., `rust` command showing a Rust snippet, `wasm` command showing research abstract)

### Critical Constraints
- **DO NOT** change `client:only="react"` to `client:load` on Terminal.tsx — xterm.js cannot SSR
- **DO NOT** add Tailwind — the project uses vanilla CSS intentionally
- **DO NOT** change the ThinkPad aesthetic (brushed metal, red LED, TrackPoint nub)
- **Preserve all existing easter eggs** — they're Joel's personality
- All CSS is component-scoped in separate files, not inline

### User Profile
Joel Alfred Israel — 2nd-year CS student at VIT Chennai (CGPA 8.09/10). Published researcher (WASM, ICTMIM 2026). Skills: Python, TypeScript, C/C++, Java, Rust, Dart. Certs: NVIDIA CUDA, Azure CV, Qiskit, Google Cloud. Won 1st place nationally in Scientia Exertus.

Full profile: `d:\portfolio-draft\docs\personal\database.md`
LLM context: `d:\portfolio-draft\docs\personal\llm_context.md`

### Reference Docs Available
| Doc | Path |
|---|---|
| Full handoff | `d:\portfolio-draft\PORTFOLIO_HANDOFF.md` |
| Architecture | `d:\portfolio-draft\docs\ARCHITECTURE.md` |
| Components | `d:\portfolio-draft\docs\components.md` |
| Tech debt | `d:\portfolio-draft\docs\TECH_DEBT.md` |
| Maintenance | `d:\portfolio-draft\docs\MAINTENANCE.md` |
| Astro+React skills | `d:\portfolio-draft\docs\skills\astro-react.md` |

Start by reading `PORTFOLIO_HANDOFF.md`, then run the dev server and open it in a browser to assess the current state. Report what you see and propose a plan.
