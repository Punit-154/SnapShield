# SMSentry Privacy Proxy

Cloudflare Worker that proxies WHOIS lookups and page fetches for the Android app.

## Features

- **WHOIS lookups** via RDAP (Verisign) - returns creation date and registrar
- **Page fetching** with HTML-to-text conversion (strips scripts, styles, nav, footer)
- **Health check** endpoint
- **Rate limiting** (100 req/min per IP)
- **CORS headers** for mobile app access
- **Caching** (1 hour TTL)

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| GET | `/health` | Health check - returns `{"status":"ok"}` |
| GET | `/whois?domain=example.com` | WHOIS lookup - returns `{creationDate, registrar}` |
| GET | `/fetch-page?url=https://...` | Fetches URL and returns plain text |

## Deployment

```bash
# Install dependencies
npm install

# Local development
npm run dev

# Deploy to Cloudflare
npm run deploy
```

## Rate Limiting

- 100 requests per minute per IP address
- Returns 429 status when exceeded
