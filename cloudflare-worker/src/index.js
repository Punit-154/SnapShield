import { htmlToText } from './utils/htmlToText.js';
import { getCachedResponse, putCachedResponse } from './utils/cache.js';

const CORS_HEADERS = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Methods': 'GET, OPTIONS',
  'Access-Control-Allow-Headers': 'Content-Type',
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

export default {
  async fetch(request, env, ctx) {
    const url = new URL(request.url);
    const clientIP = request.headers.get('CF-Connecting-IP') || 'unknown';

    if (request.method === 'OPTIONS') {
      return new Response(null, { status: 204, headers: CORS_HEADERS });
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

        const cached = await getCachedResponse(url.toString());
        if (cached) {
          return new Response(cached.body, {
            status: 200,
            headers: { ...CORS_HEADERS, 'Content-Type': 'application/json', 'X-Cache': 'HIT' },
          });
        }

        const rdapUrl = `https://rdap.verisign.com/com/v1/domain/${domain}`;
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
