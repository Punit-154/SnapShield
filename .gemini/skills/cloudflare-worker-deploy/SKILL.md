# Cloudflare Worker Deployment

## Metadata
- name: cloudflare-worker-deploy
- description: How to deploy, configure, and manage the SMSentry Cloudflare Worker privacy proxy

## Overview
The Cloudflare Worker acts as a privacy proxy between the Android app and the Gemini API:
- Strips PII from requests
- Validates API keys
- Blocks SSRF attempts
- Adds WHOIS domain validation
- Restricts CORS

## Worker Location
```
D:\SMSentry\cloudflare-worker\
├── src/index.js       # Worker logic
├── wrangler.toml      # Configuration
└── package.json
```

## Deployment

### First-time setup
```powershell
cd D:\SMSentry\cloudflare-worker
npm install
npx wrangler login  # Opens browser for OAuth
```

### Deploy
```powershell
cd D:\SMSentry\cloudflare-worker
npx wrangler deploy
```

### Set API Key Secret
```powershell
npx wrangler secret put API_KEY
# Paste the key when prompted (same as BuildConfig.PROXY_API_KEY in Android app)
```

### Verify Deployment
```powershell
# Should return 401 (no API key)
curl https://smsentry-proxy.joel010-alfred.workers.dev/health

# Should return 200 (with API key)
curl -H "X-API-Key: YOUR_KEY" https://smsentry-proxy.joel010-alfred.workers.dev/health
```

## Worker Architecture

### Request Flow
```
Android App
  → POST /analyze (with X-API-Key header)
  → Worker validates API key
  → Worker sanitizes request (strips phone numbers, etc.)
  → Worker checks for SSRF (blocks private IPs)
  → Worker forwards to Gemini API
  → Worker returns response
```

### Security Features
1. **API Key Auth**: Validates `X-API-Key` header against `API_KEY` secret
2. **CORS Restriction**: Only allows requests from the app (no browser access)
3. **SSRF Protection**: Resolves hostnames and blocks RFC1918/localhost IPs
4. **WHOIS Validation**: Checks domain registration for suspicious patterns
5. **Request Sanitization**: Strips PII before forwarding

## Gotchas
1. **Wrangler login expires** — if deploy fails with auth error, re-run `npx wrangler login`
2. **Secrets are per-environment** — `wrangler secret put` applies to production by default
3. **Worker URL format**: `https://{worker-name}.{account-subdomain}.workers.dev`
4. **Joel's worker URL**: `https://smsentry-proxy.joel010-alfred.workers.dev`
5. **Don't commit secrets** — API keys go via `wrangler secret put`, never in source code
