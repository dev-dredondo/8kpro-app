# OwnTV OpenSubtitles proxy (Cloudflare Worker)

A tiny Cloudflare Worker that proxies OpenSubtitles `/api/v1/...` REST calls, injects a
server-side application consumer key (`Api-Key`), and edge-caches subtitle **search** responses
for a few hours. Users still sign in with their **own** OpenSubtitles account inside OwnTV; only
the app's consumer key lives here, so it never ships in the APK. This is the same proxy logic
OwnTV's built-in default OpenSubtitles server runs.

You can run your **own free copy** and point OwnTV at it — no programming needed. A personal
copy serves only your own devices, so it needs none of the abuse-protection the public
deployment carries (see [`../README.md`](../README.md)).

## What you need (both free)

1. An **OpenSubtitles API consumer key**: create an account at
   [opensubtitles.com](https://www.opensubtitles.com), then register an API consumer under
   [API consumers](https://www.opensubtitles.com/en/consumers) and copy its key.
2. A **Cloudflare account**: sign up at [dash.cloudflare.com/sign-up](https://dash.cloudflare.com/sign-up)
   (free plan is plenty — no credit card, no domain needed).

## 🟢 Easy way — everything in the browser (no tools to install)

1. Log in to the [Cloudflare dashboard](https://dash.cloudflare.com).
2. In the left menu choose **Workers & Pages**, then click **Create** → **Create Worker**.
3. Give it any name you like (e.g. `my-owntv-opensub`) and click **Deploy**.
4. Click **Edit code**. Delete everything in the editor, then open
   [`index.js`](index.js) from this folder, copy **all** of it, paste it into the editor,
   and click **Deploy** (top right).
5. Add your consumer key as a secret: worker's page → **Settings** →
   **Variables and Secrets** → **Add** → type = **Secret**, name = `OPENSUB_API_KEY`
   (exactly like that), value = your OpenSubtitles API key → **Deploy** / **Save**.
6. Find your worker's address on its overview page — it looks like
   `https://my-owntv-opensub.<your-subdomain>.workers.dev`.
7. **Test it** in any browser — open:

   `https://my-owntv-opensub.<your-subdomain>.workers.dev/api/v1/subtitles?query=oppenheimer`

   If you see a wall of subtitle data (JSON), it works. If you see an error, re-check step 5
   (the secret must be named `OPENSUB_API_KEY`).

## 🛠️ Developer way — wrangler CLI

```sh
npm install -g wrangler
wrangler login
wrangler deploy                    # run from this folder
wrangler secret put OPENSUB_API_KEY # paste your consumer key when prompted
```

The deployed URL is printed by `wrangler deploy`; test as in step 7 above.

## Files in this folder

| File | What it is |
|---|---|
| [`index.js`](index.js) | The whole server — one file. This is what you paste in the browser editor. |
| [`wrangler.toml`](wrangler.toml) | Config for the CLI way only (name, entry file). Ignore it if you used the browser. |

## Notes

- GET, POST **and** DELETE are accepted (OpenSubtitles login/download are POST; logout is DELETE).
- The client's `Authorization: Bearer` header is forwarded untouched; only
  `/download` and `/infos/user` need it — search works without sign-in.
- Only GET `/api/v1/subtitles` search responses are edge-cached (6 h TTL). Login,
  logout, download, and user-info responses are **never** cached.
- VIP routing: the app can set the `X-OS-Host` header to `vip-api.opensubtitles.com`
  when OpenSubtitles' login response directs it there. The header is allowlisted to
  `api.opensubtitles.com` and `vip-api.opensubtitles.com` only — the Worker cannot be
  used as an open proxy.
- Upstream errors are passed through uncached, so a transient failure doesn't stick.

## Attribution

This product uses the OpenSubtitles API but is not endorsed or certified by OpenSubtitles.
