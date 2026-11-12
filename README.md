# Jack and the Beanstalk — personalised 20-page comic

## Architecture
| Layer | Choice |
|---|---|
| Frontend | React 18 + Vite (`frontend/`) — upload photo, name, "About me", polls progress, downloads PDF |
| Backend | Java 17, Spring Boot 3.3 (`backend/`) — async job per book; finished books saved per Google account on disk |
| Image API | **Cloudflare Workers AI FLUX.2 klein** by default (`IMAGE_PROVIDER=cloudflare-ref`: the uploaded reference image is sent with every page). `cloudflare` is the text-only `flux-1-schnell` option (square, no photo). Selectable with `IMAGE_PROVIDER`: `openai` (photo-based edits), `fal` (FLUX + PuLID face lock), `mock` (free placeholders) |
| PDF | Apache PDFBox 3 — full-bleed images + real PDF text drawn on a translucent panel |
| Optional QA | `FaceVerifier` (`VERIFY_PROVIDER=openai|anthropic`) asks a vision model if Jack matches the photo; "no" => regenerate |
| Hosting | Render web service from the root `Dockerfile` (one container serves API + built React app) |

Flow: `POST /api/books` (multipart: photo, name, details) -> `{id}` -> poll `GET /api/books/{id}` -> `GET /api/books/{id}/pdf`.

## Access control and saved books
Visitors pass two pages before they can use the app, and every `/api/**` call (except `/api/health` and `/api/auth/*`) is rejected with 401 until both are done:

1. **Password page** - one shared password (`SITE_PASSWORD`), checked on the server in constant time, with a lockout after 5 wrong tries per 10 minutes per address.
2. **Google sign-in page** - "Sign in with Google" (`GOOGLE_CLIENT_ID`). The server verifies Google's ID token itself (RS256 signature against Google's keys, issuer, audience = your client id, expiry, verified e-mail).

Both steps are remembered in one signed, HttpOnly, SameSite=Lax cookie (30 days, `AuthTokens`). It is stateless, so deploys don't log anyone out. The signing key mixes `SESSION_SECRET` with the site password, so **changing `SITE_PASSWORD` signs everyone out**.

Each Google account gets a row in `users` at sign-in (`UserStore`), and each finished book is a row in `books` (PDF + thumbnail as bytea, `BookStore`). Every query filters on the owner. Signing back in lists them under **My books** (download or delete). An account can only ever read its own rows. Photos are still never stored.

| Env var | Purpose |
|---|---|
| `SITE_PASSWORD` | the shared password. **Unset = nobody can get in.** |
| `GOOGLE_CLIENT_ID` | OAuth *Web application* client id (public value) |
| `SESSION_SECRET` | long random string that signs the cookie (Render generates one) |
| `COOKIE_SECURE` | `true` on HTTPS (set in `render.yaml`) |
| `DATABASE_URL` | Neon Postgres connection string (required). Tables `users`, `books`, `cost_stats` are created on first start |

### One-time Google setup
1. Google Cloud Console > APIs & Services > **OAuth consent screen**: configure it (External is fine; add yourself as a test user until you publish it).
2. **Credentials > Create credentials > OAuth client ID > Web application.**
3. Under **Authorized JavaScript origins** add every address the site is opened from, e.g. `http://localhost:5173`, `http://localhost:8080` and `https://<your-service>.onrender.com`. (No redirect URI is needed.)
4. Copy the client ID into `GOOGLE_CLIENT_ID`.

## Payment ($0.99 per book) and cost display
- `POST /api/books` validates the form, holds it unpaid in memory and returns a Stripe Checkout URL. The browser pays on Stripe and returns to `/?book=<id>&session_id=...`; `POST /api/books/{id}/confirm` asks Stripe (`StripePayments`) whether that session is paid, for this book, this account and the right amount, and only then starts generation.
- The form shows "Purchase book for $0.99 - average book costs $0.0X to make". The average comes from `CostTracker`: every image call and identity-check call a finished book needed (retries included) is priced at a per-call rate and the running total is saved in the `cost_stats` table. Providers don't return a dollar amount per call, so the rates are config (`IMAGE_CALL_COST_USD`, `VERIFY_CALL_COST_USD`; defaults per provider). Before the first book it shows an estimate.
- Env: `STRIPE_SECRET_KEY` (required; use `sk_test_...` first), optional `BOOK_PRICE_CENTS`, `APP_BASE_URL`. `STRIPE_PAYMENTS_DISABLED=true` skips payment for local development only.
- Limitation: an unpaid/paid job lives in server memory, so a restart between paying and returning loses the book (the user is told to ask for a refund).

## Rules and where they are enforced
- Fixed style string: `Storyboard.STYLE`, appended last to every prompt (unit test asserts this for all 20 pages).
- No text in images: no prompt asks for text or text-bearing objects; `negative_prompt` also lists text terms. All words come from `PdfComposer`.
- Consistency: one normalised reference (`BookService.normalisePhoto`) reused for every call.
- Storyboard: `Storyboard.java` (pages 1-3 as specified; 5 = planting seeds, 8 = climbing; 20 = About Me).

## Run locally
```
export SITE_PASSWORD=pick-something GOOGLE_CLIENT_ID=xxxx.apps.googleusercontent.com
export CLOUDFLARE_ACCOUNT_ID=... CLOUDFLARE_API_TOKEN=...   # token needs Workers AI permission
# or IMAGE_PROVIDER=mock for free placeholder art, IMAGE_PROVIDER=openai + OPENAI_API_KEY=...
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
```
Or the whole thing as Render will run it: `docker build -t comic . && docker run -p 8080:8080 -e IMAGE_PROVIDER=mock comic` then open http://localhost:8080.

## Deploy on Render
1. Push this folder to GitHub (the `Dockerfile` and `render.yaml` must be at the repo root).
2. Render dashboard: New > Blueprint > pick the repo. It reads `render.yaml`.
3. When prompted, paste `CLOUDFLARE_ACCOUNT_ID`, `CLOUDFLARE_API_TOKEN`, `SITE_PASSWORD` and `GOOGLE_CLIENT_ID`, then add the Render URL to the Google client's authorized JavaScript origins. (First deploy with `IMAGE_PROVIDER=mock` if you want a free smoke test.)
4. Open the service URL, upload a photo, wait for the book.

Notes: in-flight jobs live in memory, so a redeploy/restart drops books that are still being painted (finished books are on the persistent disk and survive); the disk requires a paid plan; use the Starter plan (the free tier's 512 MB and idle spin-down are a poor fit); OpenAI image models may require API organisation verification.

## Known limits
- **Cloudflare flux-1-schnell cannot take a reference photo.** The docs list only `prompt`, `steps` (max 8) and `seed`. Face consistency is therefore approximate: a typed appearance description, one fixed seed, a fixed outfit and the fixed style string. The photo is not used (the UI hides it). For a true face lock use `IMAGE_PROVIDER=fal` (PuLID) or `openai`.
- Output is 1024x1024, so pages are square; the quiet text zones are the top/bottom strips.
- OpenAI has no seed and no hard identity lock: `input_fidelity=high` plus the same reference every call helps a lot but is not a guarantee, so consider `VERIFY_PROVIDER=openai`. OpenAI may also refuse some photos of minors via its safety system; the error is shown in the UI.
- With fal/PuLID: it locks ONE identity. Pages with the mother/old man/giant can occasionally bleed Jack's face onto them; the optional verifier and retries reduce, not eliminate, this.
- Art is 1024 px wide on 6 in pages (~170 dpi); fine on screen, add an upscaler for print.
- Bundled font is DejaVu Sans Bold (`resources/fonts/StoryFont.ttf`); swap in any TTF with the same name.
