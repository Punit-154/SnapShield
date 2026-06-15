# Portfolio — Task Checklist

## Phase 1: Deploy Prep
- [x] Create `.gitignore`
- [x] Update `astro.config.mjs` — set `site: 'https://joelalfred.me'`
- [x] Create GitHub Actions deploy workflow (`.github/workflows/deploy.yml`)
- [x] Create `public/CNAME` for custom domain
- [x] Update `Layout.astro` — fix site URL fallback to `joelalfred.me`

## Phase 2: Content & Fixes
- [x] Fix `neofetch.ts` — remove unused `_owner` variable (line 116)
- [x] Fix `global.css` — remove duplicate `@import` font loading
- [x] Fix `commands.ts` — fix misleading `cd projects && ls` hint
- [x] Fix `package.json` — move dev deps to `devDependencies`
- [x] Update `types.ts` — add missing `certifications`, `achievements`, `research` fields + `huggingface`/`devfolio` in Contact
- [x] Update `portfolio.json` — add AgentOS + 5 missing projects from database.md
- [x] Sync `public/data/portfolio.json` with updated `src/data/portfolio.json`
- [x] Generate OG image for social sharing previews

## Verification
- [x] `npx astro check` → 0 errors, 0 warnings, 0 hints
- [x] `npx astro build` → Clean build, 1.31s, all modules compiled

## Remaining (Phase 3 — Later)
- [ ] Mobile responsiveness audit
- [ ] Lighthouse performance audit
- [ ] Accessibility review
- [ ] Additional easter eggs
- [ ] Real ambient.mp3 audio file
