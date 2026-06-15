# Portfolio — Phase 1+2 Walkthrough

## Summary

All Deploy Prep and Content/Fixes work is complete. **13 files** modified or created, **0 errors** on type check and build.

---

## Phase 1: Deploy Prep

### [NEW] [.gitignore](file:///d:/portfolio-draft/.gitignore)
Standard Astro gitignore — excludes `node_modules/`, `dist/`, `.astro/`, `.env`, OS files, editor dirs, and legacy `*.done` marker files.

### [MODIFY] [astro.config.mjs](file:///d:/portfolio-draft/astro.config.mjs)
- Added `site: 'https://joelalfred.me'` so OG meta tags resolve correctly at build time

### [NEW] [.github/workflows/deploy.yml](file:///d:/portfolio-draft/.github/workflows/deploy.yml)
GitHub Actions workflow that:
- Triggers on push to `main` branch
- Uses Node 22 + npm ci
- Builds with `npm run build`
- Deploys to GitHub Pages via `actions/deploy-pages@v4`

### [NEW] [public/CNAME](file:///d:/portfolio-draft/public/CNAME)
Contains `joelalfred.me` — tells GitHub Pages to serve at your custom domain instead of the default `*.github.io` URL.

### [MODIFY] [Layout.astro](file:///d:/portfolio-draft/src/layouts/Layout.astro)
- Changed site URL fallback from `joelalfredisrael.dev` → `joelalfred.me`

---

## Phase 2: Content & Fixes

### [MODIFY] [neofetch.ts](file:///d:/portfolio-draft/src/terminal/neofetch.ts)
- Removed unused `_owner` variable at line 116. The code was already accessing `data?.owner?.name` directly — this was dead code.

### [MODIFY] [global.css](file:///d:/portfolio-draft/src/styles/global.css)
- Removed render-blocking `@import url('https://fonts.googleapis.com/...')`. Fonts were being double-loaded (CSS @import + HTML `<link>` preconnect in Layout.astro). Kept only the faster `<link>` approach.

### [MODIFY] [commands.ts](file:///d:/portfolio-draft/src/terminal/commands.ts)
- Changed `cd projects && ls` → `cd projects` then `ls` — the terminal doesn't support `&&` chaining.

### [MODIFY] [package.json](file:///d:/portfolio-draft/package.json)
- Moved `@astrojs/check`, `@types/react`, `@types/react-dom`, `typescript` from `dependencies` to `devDependencies`.

### [MODIFY] [types.ts](file:///d:/portfolio-draft/src/terminal/types.ts)
- Added `Certification`, `Achievement`, `Research` interfaces
- Added `huggingface?`, `devfolio?` to `Contact` (made `twitter` optional)
- Added `certifications?`, `achievements?`, `research?` to `PortfolioData`
- Now fully matches the actual `portfolio.json` schema

### [MODIFY] [portfolio.json](file:///d:/portfolio-draft/src/data/portfolio.json)
**Projects added (5 new):**
| # | Project | Source |
|---|---------|--------|
| 1 | **AgentOS** | Handoff prompt — top priority |
| 2 | **Uni-Union Website** | database.md #2 |
| 3 | **Carta** | database.md #9 |
| 4 | **Bionary Blog** | database.md #10 |
| 5 | **Google Cloud Skills Script** | database.md #14 |

**Skills expanded:**
- Added `Shell` to Languages
- Added `Flutter` to Frontend
- Added `FastAPI` to Backend
- Added `WSL2` to Systems

**Contact updated:**
- Added `huggingface` and `devfolio` links
- Removed empty `twitter` field

**Excluded** (intentionally):
- Forks (komorebi, opik) — not original work
- Design-only projects (Digital Well-Being App) — no GitHub repo
- Club Events Website — no GitHub repo

### [SYNC] public/data/portfolio.json
Copied updated `src/data/portfolio.json` → `public/data/portfolio.json` to keep the runtime fetch copy in sync.

### [NEW] public/og-image.png
Generated OG image for social sharing — dark background with amber terminal aesthetic showing "Joel Alfred Israel" identity.

---

## Verification Results

```
npx astro check → 0 errors, 0 warnings, 0 hints (17 files)
npx astro build → Clean build, 1.31s, 1 page, 6 JS bundles
```

---

## To Deploy

Once you push to GitHub with Pages enabled:

```bash
git add -A
git commit -m "chore: deploy prep — GitHub Pages, content updates, bug fixes"
git push origin main
```

Then in GitHub repo settings:
1. **Settings → Pages → Source** → "GitHub Actions"
2. The workflow will auto-run on push to `main`
3. Site will be live at `https://joelalfred.me`

---

## Remaining (Phase 3 — for later)
- Mobile responsiveness audit
- Lighthouse performance optimization
- Accessibility review
- Additional easter eggs
- Real `ambient.mp3` audio file (currently 44-byte placeholder)
