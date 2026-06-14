import { htmlToText } from './utils/htmlToText.js';
import { getCachedResponse, putCachedResponse } from './utils/cache.js';

// Removed wildcard CORS — Android app doesn't need CORS headers.
// Only allow the specific app origin if web access is ever needed.
const CORS_HEADERS = {
  'Access-Control-Allow-Origin': 'null', // Block all browser origins
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type, X-API-Key',
};

const RATE_LIMIT = 100;
const RATE_WINDOW = 60;

const rateLimitMap = new Map();

function checkRateLimit(ip) {
  const now = Math.floor(Date.now() / 1000);
  const entry = rateLimitMap.get(ip);
  if (!entry || now - entry.start > RATE_WINDOW) {
    rateLimitMap.set(ip, { start: now, count: 1 });
    return true;
  }
  if (entry.count >= RATE_LIMIT) return false;
  entry.count++;
  return true;
}

/**
 * Validate API key from request header against the stored secret.
 * The API_KEY should be set as a Cloudflare Worker secret via:
 *   wrangler secret put API_KEY
 */
function validateApiKey(request, env) {
  const apiKey = request.headers.get('X-API-Key');
  if (!env.API_KEY) return true; // Skip auth if secret not configured yet
  return apiKey === env.API_KEY;
}

/**
 * Block internal/private IPs and dangerous URL schemes to prevent SSRF.
 */
function isUrlSafe(targetUrl) {
  try {
    const parsed = new URL(targetUrl);
    
    // Only allow http/https
    if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
      return false;
    }

    const hostname = parsed.hostname.toLowerCase();

    // Block localhost variants
    if (hostname === 'localhost' || hostname === '127.0.0.1' || hostname === '::1' ||
        hostname === '0.0.0.0' || hostname === '[::1]') {
      return false;
    }

    // Block RFC1918 private ranges
    const ipMatch = hostname.match(/^(\d+)\.(\d+)\.(\d+)\.(\d+)$/);
    if (ipMatch) {
      const [, a, b] = ipMatch.map(Number);
      if (a === 10) return false;                          // 10.0.0.0/8
      if (a === 172 && b >= 16 && b <= 31) return false;   // 172.16.0.0/12
      if (a === 192 && b === 168) return false;             // 192.168.0.0/16
      if (a === 169 && b === 254) return false;             // 169.254.0.0/16 (metadata)
      if (a === 127) return false;                          // 127.0.0.0/8
      if (a === 0) return false;                            // 0.0.0.0/8
    }

    // Block cloud metadata endpoints
    if (hostname === 'metadata.google.internal' ||
        hostname === 'metadata.google.com' ||
        hostname === 'instance-data') {
      return false;
    }

    return true;
  } catch {
    return false;
  }
}

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const clientIP = request.headers.get('CF-Connecting-IP') || 'unknown';

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
    }

    // API key authentication
    if (!validateApiKey(request, env)) {
      return new Response(JSON.stringify({ error: 'Unauthorized' }), {
        status: 401,
        headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
      });
    }

    if (!checkRateLimit(clientIP)) {
      return new Response(JSON.stringify({ error: 'Rate limit exceeded' }), {
        status: 429,
        headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
      });
    }

    try {
      if (url.pathname === '/health') {
        return new Response(JSON.stringify({ status: 'ok' }), {
          status: 200,
          headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
        });
      }

      if (url.pathname === '/whois') {
        const domain = url.searchParams.get('domain');
        if (!domain) {
          return new Response(JSON.stringify({ error: 'Missing domain parameter' }), {
            status: 400,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
          });
        }

        // Validate domain format (basic check)
        if (!/^[a-zA-Z0-9][a-zA-Z0-9.-]+\.[a-zA-Z]{2,}$/.test(domain)) {
          return new Response(JSON.stringify({ error: 'Invalid domain format' }), {
            status: 400,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
          });
        }

        const cached = await getCachedResponse(url.toString());
        if (cached) {
          return new Response(cached.body, {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json', 'X-Cache': 'HIT' },
          });
        }

        const rdapUrl = `https://rdap.verisign.com/com/v1/domain/${encodeURIComponent(domain)}`;
        const resp = await fetch(rdapUrl);
        if (!resp.ok) {
          return new Response(JSON.stringify({ error: 'Upstream RDAP request failed' }), {
            status: 502,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
          });
        }

        const data = await resp.json();
        const creationDate = data.events?.find(e => e.eventAction === 'registration')?.eventDate || null;
        const registrar = data.entities?.[0]?.vcardArray?.[1]?.find(v => v[0] === 'fn')?.[3] || null;

        const result = JSON.stringify({ creationDate, registrar });
        await putCachedResponse(url.toString(), result);

        return new Response(result, {
          status: 200,
          headers: { ...CORS_HEADERS, 'Content-Type': 'application/json', 'X-Cache': 'MISS' },
        });
      }

      if (url.pathname === '/fetch-page') {
        const targetUrl = url.searchParams.get('url');
        if (!targetUrl) {
          return new Response(JSON.stringify({ error: 'Missing url parameter' }), {
            status: 400,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
          });
        }

        // SSRF protection: validate URL before fetching
        if (!isUrlSafe(targetUrl)) {
          return new Response(JSON.stringify({ error: 'Blocked: URL targets a private or internal address' }), {
            status: 403,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
          });
        }

        const cached = await getCachedResponse(url.toString());
        if (cached) {
          return new Response(cached.body, {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'text/plain', 'X-Cache': 'HIT' },
          });
        }

        const resp = await fetch(targetUrl);
        if (!resp.ok) {
          return new Response(JSON.stringify({ error: 'Failed to fetch page' }), {
            status: 502,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
          });
        }

        const html = await resp.text();
        const text = htmlToText(html).slice(0, 50000);
        await putCachedResponse(url.toString(), text);

        return new Response(text, {
          status: 200,
          headers: { ...CORS_HEADERS, 'Content-Type': 'text/plain', 'X-Cache': 'MISS' },
        });
      }

      return new Response(JSON.stringify({ error: 'Not found' }), {
        status: 404,
        headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
      });
    } catch (err) {
      return new Response(JSON.stringify({ error: 'Internal server error' }), {
        status: 500,
        headers: { ...CORS_HEADERS, 'Content-Type': 'application/json' },
      });
    }
  },
};
