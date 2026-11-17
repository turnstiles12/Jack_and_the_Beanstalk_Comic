import { useEffect, useRef, useState } from "react";
import { api, downloadPdf, readJson } from "./api.js";
import { BeanstalkScene, HoodScene, StoryCover } from "./art.jsx";

const MAX_BYTES = 10 * 1024 * 1024;

export default function Maker({ onLibrary }) {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [name, setName] = useState("");
  const [details, setDetails] = useState("");
  const [consent, setConsent] = useState(false);
  const [phase, setPhase] = useState("form"); // form | working | done | error
  const [progress, setProgress] = useState(0);
  const [donePages, setDonePages] = useState([]);   // page numbers whose real illustration is ready to show
  const [error, setError] = useState("");
  const [jobId, setJobId] = useState(null);
  const [appearance, setAppearance] = useState("");
  const [story, setStory] = useState("jack");
  const [elapsed, setElapsed] = useState(0);        // seconds since generation started, shown while painting
  const [tookMs, setTookMs] = useState(null);       // server-measured generation time, shown when done
  const [cfg, setCfg] = useState({
    usesPhoto: true, needsAppearance: false, priceCents: 99, avgCostCents: null,
    stories: [{ id: "jack", title: "Jack and the Beanstalk" }, { id: "red", title: "Little Red Riding Hood" }],
  });
  const timer = useRef(null);
  const clock = useRef(null);
  const stories = cfg.stories || [];
  const storyTitle = stories.find((s) => s.id === story)?.title || "Jack and the Beanstalk";

  function startPolling(id) {
    setProgress(0); setDonePages([]); setElapsed(0); setTookMs(null);
    const t0 = Date.now();
    clearInterval(clock.current);
    clock.current = setInterval(() => setElapsed(Math.floor((Date.now() - t0) / 1000)), 500);
    clearInterval(timer.current);
    timer.current = setInterval(() => poll(id), 1000);
  }
  function stopPolling() { clearInterval(timer.current); clearInterval(clock.current); }

  useEffect(() => {
    api("/api/config").then(readJson).then(setCfg).catch(() => {});
  }, []);

  // Returning from Stripe Checkout: ?book=<id>&session_id=cs_...  (or ?canceled=1)
  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    if (!q.has("book") && !q.has("canceled")) return;
    window.history.replaceState({}, "", window.location.pathname);
    if (q.has("canceled")) { setError("Payment was canceled, so no book was made and you weren't charged. Fill in the form to try again."); return; }
    const id = q.get("book");
    setPhase("working");
    api(`/api/books/${id}/confirm`, {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ sessionId: q.get("session_id") }),
    }).then(async (res) => {
      const data = await readJson(res);
      if (!res.ok) throw new Error(data.error || `Could not confirm payment (HTTP ${res.status})`);
      setJobId(id); setName("");
      startPolling(id);
    }).catch((err) => { setError(err.message); setPhase("error"); });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => () => { stopPolling(); if (preview) URL.revokeObjectURL(preview); }, [preview]);

  function onPick(e) {
    const f = e.target.files?.[0];
    if (!f) return;
    if (!/^image\/(jpeg|png)$/.test(f.type)) return setError("Please choose a JPG or PNG reference image.");
    if (f.size > MAX_BYTES) return setError("That image is over 10 MB.");
    setError("");
    setFile(f);
    setPreview(URL.createObjectURL(f));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    const body = new FormData();
    if (file && cfg.usesPhoto) body.append("photo", file);
    body.append("appearance", appearance.trim());
    body.append("name", name.trim());
    body.append("details", details.trim());
    body.append("origin", window.location.origin);
    body.append("story", story);
    try {
      const res = await api("/api/books", { method: "POST", body });
      const data = await readJson(res);
      if (!res.ok) throw new Error(data.error || `Upload failed (HTTP ${res.status})`);
      if (data.checkoutUrl) { window.location.assign(data.checkoutUrl); return; }   // pay on Stripe, then come back
      setJobId(data.id);
      setPhase("working");
      startPolling(data.id);
    } catch (err) {
      setError(err.message);
    }
  }

  async function poll(id) {
    try {
      const res = await api(`/api/books/${id}`);
      if (res.status === 404) throw new Error("The server restarted and lost your book. Please try again.");
      const s = await readJson(res);
      setProgress(s.pagesDone);
      if (Array.isArray(s.donePages)) setDonePages(s.donePages);
      if (s.story) setStory(s.story);
      if (s.state === "DONE") { stopPolling(); setTookMs(s.elapsedMs || null); setPhase("done"); }
      if (s.state === "FAILED") { stopPolling(); setError(s.error || "Generation failed."); setPhase("error"); }
    } catch (err) {
      stopPolling(); setError(err.message); setPhase("error");
    }
  }

  async function download() {
    try { await downloadPdf(jobId, name.trim(), storyTitle); } catch (err) { setError(err.message); }
  }

  function reset() { setPhase("form"); setError(""); setJobId(null); }

  const canSubmit = name.trim() && consent && (!cfg.usesPhoto || file) && (!cfg.needsAppearance || appearance.trim());

  const price = `$${(cfg.priceCents / 100).toFixed(2)}`;
  const blurb = {
    jack: "Trade a cow for magic beans, climb the giant stalk and outwit a giant in his castle in the clouds.",
    red: "Take the forest path to Grandmother's cottage, and keep an eye out for one very cheeky wolf.",
  };
  const Hero = story === "red" ? HoodScene : BeanstalkScene;

  // The 20 page slots: each one swaps from a numbered placeholder to the real illustration as soon as it is drawn.
  const pageGrid = jobId && (
    <ol className="page-grid" aria-label="Pages of your comic">
      {Array.from({ length: 20 }, (_, i) => i + 1).map((n) => (
        <li key={n} className={donePages.includes(n) ? "page-slot done" : "page-slot"}>
          {donePages.includes(n)
            ? <img src={`/api/books/${jobId}/pages/${n}/thumb`} alt={`Page ${n}`} />
            : <span aria-label={`Page ${n}, not drawn yet`}>{n}</span>}
        </li>
      ))}
    </ol>
  );

  if (phase !== "form") {
    return (
      <main className="status-card">
        <Hero className="status-art" />
        {phase === "working" && (
          <div className="status">
            <p className="eyebrow">{storyTitle}</p>
            <h1>Your comic is being drawn</h1>
            <p className="lede">
              {progress >= 20
                ? "Putting the pages together into your PDF…"
                : `Painting your pages: ${progress} of 20 done`}
              {" "}({elapsed}s)
            </p>
            <progress max="20" value={progress} aria-label="Pages painted" />
            {pageGrid}
            <p className="hint">Usually under a minute. You can keep this tab open.</p>
          </div>
        )}
        {phase === "done" && (
          <div className="status">
            <p className="eyebrow">The End. Or is it?</p>
            <h1>Your comic is ready!</h1>
            <p className="lede">{tookMs ? `Made in ${Math.round(tookMs / 1000)} seconds, and ` : ""}saved to your bookshelf.</p>
            <div className="row center">
              <button className="btn btn-primary" onClick={download}>Download PDF</button>
              <button className="btn btn-ghost" onClick={onLibrary}>View my bookshelf</button>
              <button className="btn btn-ghost" onClick={reset}>Make another</button>
            </div>
            {error && <p className="error">{error}</p>}
            {donePages.length > 0 && pageGrid}
          </div>
        )}
        {phase === "error" && (
          <div className="status">
            <p className="eyebrow">Oh no</p>
            <h1>Something went wrong</h1>
            <p className="error">{error}</p>
            <button className="btn btn-primary" onClick={reset}>Try again</button>
          </div>
        )}
      </main>
    );
  }

  return (
    <main className="shop">
      <section className="hero">
        <div className="hero-copy">
          <p className="eyebrow">Personalized comics</p>
          <h1>Become the hero of a classic tale</h1>
          <p className="lede">Upload one photo and we'll turn you into the star of Jack and the Beanstalk or Little Red Riding Hood: a 20-page comic book, ready in about a minute.</p>
          <ul className="ticks">
            <li>20 illustrated pages starring you</li>
            <li>Ready in about a minute</li>
            <li>Download a PDF to print or share</li>
          </ul>
          <a className="btn btn-primary" href="#order">Choose your story</a>
        </div>
        <div className="hero-art"><Hero className="hero-svg" /></div>
      </section>

      <form onSubmit={onSubmit} id="order" className="order">
        <section aria-labelledby="pick-title">
          <div className="section-head">
            <p className="eyebrow">Step 1</p>
            <h2 id="pick-title">Choose your story</h2>
          </div>
          <fieldset className="catalog">
            <legend className="sr-only">Choose a story</legend>
            {stories.map((s) => (
              <label key={s.id} className={s.id === story ? "product on" : "product"}>
                <input type="radio" name="story" value={s.id} checked={s.id === story} onChange={() => setStory(s.id)} />
                <span className="product-cover"><StoryCover id={s.id} /></span>
                <span className="product-body">
                  <span className="product-title">{s.title}</span>
                  <span className="product-blurb">{blurb[s.id]}</span>
                  <span className="product-meta">20 pages · PDF · <strong>{price}</strong></span>
                  <span className="product-pick">{s.id === story ? "✓ Selected" : "Select this story"}</span>
                </span>
              </label>
            ))}
          </fieldset>
        </section>

        <div className="order-grid">
          <div className="panel">
            <div className="section-head">
              <p className="eyebrow">Step 2</p>
              <h2>Tell us about the hero</h2>
            </div>

            {cfg.usesPhoto && (<>
              <label className="drop">
                {preview
                  ? <img src={preview} alt="Your photo" />
                  : <span className="drop-empty">
                      <svg viewBox="0 0 24 24" width="34" height="34" aria-hidden="true"><path d="M4 8h3l2-3h6l2 3h3v11H4z" fill="none" stroke="currentColor" strokeWidth="1.8" strokeLinejoin="round" /><circle cx="12" cy="13" r="3.5" fill="none" stroke="currentColor" strokeWidth="1.8" /></svg>
                      <strong>Add a photo of yourself</strong>
                      <span>JPG or PNG, one clear front-facing photo</span>
                    </span>}
                <input type="file" accept="image/jpeg,image/png" onChange={onPick} />
              </label>
              <p className="hint">Tips: just you, face fully visible, good light, no sunglasses or hats.</p>
            </>)}

            {cfg.needsAppearance && (
              <label className="field">
                <span className="field-label">How do you look? <span className="count">{appearance.length}/150</span></span>
                <input value={appearance} maxLength={150} onChange={(e) => setAppearance(e.target.value)}
                  placeholder="e.g. short curly brown hair, brown eyes, freckles, light-tan skin" required />
                <span className="hint">This text model can't see photos, so the hero is drawn from this description.
                  The face will be similar from page to page but not an exact likeness.</span>
              </label>
            )}

            <label className="field">
              <span className="field-label">Your name</span>
              <input value={name} maxLength={30} onChange={(e) => setName(e.target.value)} placeholder="e.g. Jack" required />
            </label>

            <label className="field">
              <span className="field-label">About me <span className="count">{details.length}/400</span></span>
              <textarea value={details} maxLength={400} rows={4} onChange={(e) => setDetails(e.target.value)}
                placeholder="Age, favourite food, hobbies, best friend, pets…" />
              <span className="hint">This appears on the "About Me" page at the back of your comic.</span>
            </label>

            <label className="check">
              <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
              <span>{cfg.usesPhoto
                ? "I agree this photo is sent to an AI image service to make my comic. The photo is not stored and is discarded after generation; the finished book is saved to your account."
                : "I agree my name and description are sent to an AI image service to make my comic. The finished book is saved to your account."}</span>
            </label>
          </div>

          <aside className="summary" aria-label="Order summary">
            <p className="eyebrow">Step 3</p>
            <h2>Your order</h2>
            <div className="summary-item">
              <div className="summary-cover"><StoryCover id={story} /></div>
              <div>
                <strong>{storyTitle}</strong>
                <span className="hint">20-page personalized comic (PDF)</span>
              </div>
            </div>
            <dl className="totals">
              <div><dt>Comic</dt><dd>{price}</dd></div>
              <div className="total"><dt>Total</dt><dd>{price}</dd></div>
            </dl>
            {error && <p className="error">{error}</p>}
            <button className="btn btn-primary btn-block btn-lg" disabled={!canSubmit}>Buy &amp; create my comic · {price}</button>
            {!canSubmit && <p className="hint center-text">Add {cfg.usesPhoto && !file ? "a photo, " : ""}your name and tick the box to continue.</p>}
            <ul className="assure">
              <li>Secure checkout with Stripe</li>
              <li>Ready in about a minute</li>
              <li>Saved to your bookshelf</li>
            </ul>
            {cfg.avgCostCents != null && (
              <p className="fineprint">Average cost to make one comic: ${(cfg.avgCostCents / 100).toFixed(2)}</p>
            )}
          </aside>
        </div>
      </form>
    </main>
  );
}
