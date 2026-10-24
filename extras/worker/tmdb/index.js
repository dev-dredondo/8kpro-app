/** OwnTV's backwards-compatible TMDB v3 caching gateway. */
const TMDB_ORIGIN = "https://api.themoviedb.org";
// 180 days, matched to the app's own MetadataRepository.POSITIVE_TTL_MS. At 30 days the Worker
// re-fetched from TMDB on day 31 even though no app would ask again for another five months —
// buying a re-download of identical JSON. Details for a released title barely change, and the
// app's "Refetch TMDB details" already exists for the rare case a user wants one sooner.
const METADATA_FRESH_SECONDS = 180 * 24 * 60 * 60;
const TRENDING_FRESH_SECONDS = 15 * 60;
// Must stay ABOVE the fresh window: this is the Cache-Control max-age, so it decides how long the
// entry survives at the edge at all. Were it lower, the stale-on-outage fallback below could
// never fire, because the entry would already be gone.
const METADATA_STALE_SECONDS = 365 * 24 * 60 * 60;
const TRENDING_STALE_SECONDS = 24 * 60 * 60;
const UPSTREAM_TIMEOUT_MS = 8_000;
const MAX_ATTEMPTS = 2;

export default {
  async fetch(request, env, context) {
    const requestId = crypto.randomUUID();
    if (request.method !== "GET") {
      return jsonError(405, "method_not_allowed", requestId, { Allow: "GET" });
    }
    if (!env.TMDB_KEY) {
      return jsonError(503, "tmdb_key_not_configured", requestId);
    }

    const incoming = new URL(request.url);
    if (!incoming.pathname.startsWith("/3/")) {
      return jsonError(404, "not_found", requestId);
    }

    // Client-supplied keys never affect auth or cache identity. Sorting also prevents equivalent
    // query strings from creating separate cache entries.
    incoming.searchParams.delete("api_key");
    incoming.searchParams.sort();
    const cacheKey = new Request(incoming.toString(), { method: "GET" });
    const cache = caches.default;
    const cached = await cache.match(cacheKey);
    const policy = cachePolicy(incoming.pathname);
    const cachedAge = cachedAgeSeconds(cached);
    if (cached && cachedAge !== null && cachedAge <= policy.freshSeconds) {
      return clientResponse(cached, policy.freshSeconds, requestId, "HIT");
    }

    const upstream = new URL(TMDB_ORIGIN + incoming.pathname + incoming.search);
    upstream.searchParams.set("api_key", env.TMDB_KEY);
    const result = await fetchWithRetry(upstream, requestId);
    if (result.response?.ok && result.body !== null) {
      const stored = new Response(result.body, {
        status: result.response.status,
        headers: result.response.headers,
      });
      stored.headers.set("Cache-Control", `public, max-age=${policy.staleSeconds}`);
      stored.headers.set("X-OwnTV-Cached-At", Date.now().toString());
      stored.headers.set("X-OwnTV-API-Version", "1");
      applyCommonHeaders(stored.headers, requestId);
      context.waitUntil(cache.put(cacheKey, stored.clone()));
      return clientResponse(stored, policy.freshSeconds, requestId, "MISS");
    }

    // A stale successful TMDB payload is safer than hiding Trending during a brief outage.
    if (cached) {
      const stale = clientResponse(cached, 0, requestId, "STALE");
      stale.headers.set("Warning", '110 - "TMDB response is stale"');
      return stale;
    }
    if (result.response) {
      const response = new Response(result.body, {
        status: result.response.status,
        headers: result.response.headers,
      });
      response.headers.set("Cache-Control", "no-store");
      applyCommonHeaders(response.headers, requestId);
      return response;
    }
    return jsonError(502, result.error || "tmdb_unavailable", requestId);
  },
};

async function fetchWithRetry(url, requestId) {
  let lastError = "tmdb_unavailable";
  for (let attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
    try {
      const response = await fetch(url.toString(), {
        headers: { Accept: "application/json", "X-OwnTV-Request-Id": requestId },
        signal: AbortSignal.timeout(UPSTREAM_TIMEOUT_MS),
      });
      const body = await response.text();
      if (response.ok) {
        try {
          JSON.parse(body);
        } catch {
          return { response: null, body: null, error: "invalid_tmdb_json" };
        }
        return { response, body, error: null };
      }
      if (!retryable(response.status) || attempt === MAX_ATTEMPTS) {
        return { response, body, error: null };
      }
      await delay(retryDelayMs(response, attempt));
    } catch (error) {
      lastError = error?.name === "TimeoutError" ? "tmdb_timeout" : "tmdb_unavailable";
      if (attempt < MAX_ATTEMPTS) await delay(150 * attempt);
    }
  }
  return { response: null, body: null, error: lastError };
}

function cachePolicy(pathname) {
  const trending = /^\/3\/trending\/(movie|tv)\/day$/.test(pathname);
  return trending
    ? { freshSeconds: TRENDING_FRESH_SECONDS, staleSeconds: TRENDING_STALE_SECONDS }
    : { freshSeconds: METADATA_FRESH_SECONDS, staleSeconds: METADATA_STALE_SECONDS };
}

function cachedAgeSeconds(response) {
  if (!response) return null;
  const cachedAt = Number(response.headers.get("X-OwnTV-Cached-At"));
  return Number.isFinite(cachedAt) && cachedAt > 0 ? Math.max(0, (Date.now() - cachedAt) / 1000) : null;
}

function clientResponse(source, maxAge, requestId, cacheStatus) {
  const response = new Response(source.body, source);
  response.headers.set("Cache-Control", `public, max-age=${Math.max(0, maxAge)}`);
  response.headers.set("X-OwnTV-Cache", cacheStatus);
  response.headers.set("X-OwnTV-API-Version", "1");
  applyCommonHeaders(response.headers, requestId);
  return response;
}

function jsonError(status, code, requestId, extraHeaders = {}) {
  const response = Response.json({ error: code, request_id: requestId }, { status, headers: extraHeaders });
  response.headers.set("Cache-Control", "no-store");
  applyCommonHeaders(response.headers, requestId);
  return response;
}

function applyCommonHeaders(headers, requestId) {
  headers.set("Access-Control-Allow-Origin", "*");
  headers.set("X-OwnTV-Request-Id", requestId);
  headers.set("Vary", "Accept-Encoding");
}

function retryable(status) {
  return status === 429 || (status >= 500 && status <= 504);
}

function retryDelayMs(response, attempt) {
  const retryAfter = Number(response.headers.get("Retry-After"));
  if (Number.isFinite(retryAfter) && retryAfter >= 0) return Math.min(2_000, retryAfter * 1000);
  return 150 * attempt;
}

function delay(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
