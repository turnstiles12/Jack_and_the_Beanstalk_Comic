# Jack and the Beanstalk — personalised 20-page comic

## Architecture
| Layer | Choice |
|---|---|
| Frontend | React 18 + Vite (`frontend/`) — upload photo, name, "About me", polls progress, downloads PDF |
| Backend | Java 17, Spring Boot 3.3 (`backend/`) — async job per book, in-memory only |
| Image API | **OpenAI** Images edits (`gpt-image-1.5`, `input_fidelity=high`) by default; fal.ai `flux-pulid` and a free `mock` provider are selectable with `IMAGE_PROVIDER`. The same reference photo is sent on all 20 calls |
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
export OPENAI_API_KEY=...        # or IMAGE_PROVIDER=mock for free placeholder art
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev   # http://localhost:5173 (proxies /api to :8080)
```
Or the whole thing as Render will run it: `docker build -t comic . && docker run -p 8080:8080 -e IMAGE_PROVIDER=mock comic` then open http://localhost:8080.

## Deploy on Render
1. Push this folder to GitHub (the `Dockerfile` and `render.yaml` must be at the repo root).
2. Render dashboard: New > Blueprint > pick the repo. It reads `render.yaml`.
3. When prompted, paste `OPENAI_API_KEY`. (First deploy with `IMAGE_PROVIDER=mock` if you want a free smoke test.)
4. Open the service URL, upload a photo, wait for the book.

Notes: jobs and PDFs live in memory, so a redeploy/restart drops in-flight books; use the Starter plan (the free tier's 512 MB and idle spin-down are a poor fit); OpenAI image models may require API organisation verification.

## Known limits
- OpenAI has no seed and no hard identity lock: `input_fidelity=high` plus the same reference every call helps a lot but is not a guarantee, so consider `VERIFY_PROVIDER=openai`. OpenAI may also refuse some photos of minors via its safety system; the error is shown in the UI.
- With fal/PuLID: it locks ONE identity. Pages with the mother/old man/giant can occasionally bleed Jack's face onto them; the optional verifier and retries reduce, not eliminate, this.
- 1024x1536 art on 6x9in pages = ~170 dpi. Fine on screen; add an upscaler call for print.
- Bundled font is DejaVu Sans Bold (`resources/fonts/StoryFont.ttf`); swap in any TTF with the same name.
