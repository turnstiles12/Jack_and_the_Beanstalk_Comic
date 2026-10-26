# Jack and the Beanstalk — personalised 20-page comic

## Architecture
| Layer | Choice |
|---|---|
| Frontend | React 18 + Vite (`frontend/`) — upload photo, name, "About me", polls progress, downloads PDF |
| Backend | Java 17, Spring Boot 3.3 (`backend/`) — async job per book, in-memory only |
| Image API | **Cloudflare Workers AI `flux-1-schnell`** by default (text-to-image, square). Selectable with `IMAGE_PROVIDER`: `openai` (photo-based edits), `fal` (FLUX + PuLID face lock), `mock` (free placeholders) |
| PDF | Apache PDFBox 3 — full-bleed images + real PDF text drawn on a translucent panel |
| Optional QA | `FaceVerifier` (`VERIFY_PROVIDER=openai|anthropic`) asks a vision model if Jack matches the photo; "no" => regenerate |
| Hosting | Render web service from the root `Dockerfile` (one container serves API + built React app) |

Flow: `POST /api/books` (multipart: photo, name, details) -> `{id}` -> poll `GET /api/books/{id}` -> `GET /api/books/{id}/pdf`.

## Rules and where they are enforced
- Fixed style string: `Storyboard.STYLE`, appended last to every prompt (unit test asserts this for all 20 pages).
- No text in images: no prompt asks for text or text-bearing objects; `negative_prompt` also lists text terms. All words come from `PdfComposer`.
- Consistency: one normalised reference (`BookService.normalisePhoto`) reused for every call.
- Storyboard: `Storyboard.java` (pages 1-3 as specified; 5 = planting seeds, 8 = climbing; 20 = About Me).

## Run locally
```
export CLOUDFLARE_ACCOUNT_ID=... CLOUDFLARE_API_TOKEN=...   # token needs Workers AI permission
# or IMAGE_PROVIDER=mock for free placeholder art, IMAGE_PROVIDER=openai + OPENAI_API_KEY=...
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
```
Or the whole thing as Render will run it: `docker build -t comic . && docker run -p 8080:8080 -e IMAGE_PROVIDER=mock comic` then open http://localhost:8080.

## Deploy on Render
1. Push this folder to GitHub (the `Dockerfile` and `render.yaml` must be at the repo root).
2. Render dashboard: New > Blueprint > pick the repo. It reads `render.yaml`.
3. When prompted, paste `CLOUDFLARE_ACCOUNT_ID` and `CLOUDFLARE_API_TOKEN`. (First deploy with `IMAGE_PROVIDER=mock` if you want a free smoke test.)
4. Open the service URL, upload a photo, wait for the book.

Notes: jobs and PDFs live in memory, so a redeploy/restart drops in-flight books; use the Starter plan (the free tier's 512 MB and idle spin-down are a poor fit); OpenAI image models may require API organisation verification.

## Known limits
- **Cloudflare flux-1-schnell cannot take a reference photo.** The docs list only `prompt`, `steps` (max 8) and `seed`. Face consistency is therefore approximate: a typed appearance description, one fixed seed, a fixed outfit and the fixed style string. The photo is not used (the UI hides it). For a true face lock use `IMAGE_PROVIDER=fal` (PuLID) or `openai`.
- Output is 1024x1024, so pages are square; the quiet text zones are the top/bottom strips.
- OpenAI has no seed and no hard identity lock: `input_fidelity=high` plus the same reference every call helps a lot but is not a guarantee, so consider `VERIFY_PROVIDER=openai`. OpenAI may also refuse some photos of minors via its safety system; the error is shown in the UI.
- With fal/PuLID: it locks ONE identity. Pages with the mother/old man/giant can occasionally bleed Jack's face onto them; the optional verifier and retries reduce, not eliminate, this.
- Art is 1024 px wide on 6 in pages (~170 dpi); fine on screen, add an upscaler for print.
- Bundled font is DejaVu Sans Bold (`resources/fonts/StoryFont.ttf`); swap in any TTF with the same name.
