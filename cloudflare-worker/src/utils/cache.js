const DEFAULT_TTL = 3600;

export async function getCachedResponse(url) {
  const cache = caches.default;
  const cacheKey = new Request(url, { method: 'GET' });
  const response = await cache.match(cacheKey);
  if (!response) return null;
  const body = await response.text();
  return { body, status: response.status };
}

export async function putCachedResponse(url, body, ttl = DEFAULT_TTL) {
  const cache = caches.default;
  const cacheKey = new Request(url, { method: 'GET' });
  const response = new Response(body, {
    headers: {
      'Content-Type': 'application/json',
      'Cache-Control': `max-age=${ttl}`,
    },
  });
  await cache.put(cacheKey, response);
}
