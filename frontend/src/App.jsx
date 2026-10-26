import { useEffect, useRef, useState } from "react";

const MAX_BYTES = 10 * 1024 * 1024;

export default function App() {
  const [file, setFile] = useState(null);
  const [preview, setPreview] = useState(null);
  const [name, setName] = useState("");
  const [details, setDetails] = useState("");
  const [consent, setConsent] = useState(false);
  const [phase, setPhase] = useState("form"); // form | working | done | error
  const [progress, setProgress] = useState(0);
  const [error, setError] = useState("");
  const [jobId, setJobId] = useState(null);
  const timer = useRef(null);

  useEffect(() => () => { clearInterval(timer.current); if (preview) URL.revokeObjectURL(preview); }, [preview]);

  function onPick(e) {
    const f = e.target.files?.[0];
    if (!f) return;
    if (!/^image\/(jpeg|png)$/.test(f.type)) return setError("Please choose a JPG or PNG photo.");
    if (f.size > MAX_BYTES) return setError("That photo is over 10 MB.");
    setError("");
    setFile(f);
    setPreview(URL.createObjectURL(f));
  }

  async function onSubmit(e) {
    e.preventDefault();
    setError("");
    const body = new FormData();
    body.append("photo", file);
    body.append("name", name.trim());
    body.append("details", details.trim());
    try {
      const res = await fetch("/api/books", { method: "POST", body });
      const data = await res.json();
      if (!res.ok) throw new Error(data.error || "Upload failed");
      setJobId(data.id);
      setPhase("working");
      setProgress(0);
      timer.current = setInterval(() => poll(data.id), 3000);
    } catch (err) {
      setError(err.message);
    }
  }

  async function poll(id) {
    try {
      const res = await fetch(`/api/books/${id}`);
      if (!res.ok) throw new Error("Lost track of your book. Please try again.");
      const s = await res.json();
      setProgress(s.pagesDone);
      if (s.state === "DONE") { clearInterval(timer.current); setPhase("done"); }
      if (s.state === "FAILED") { clearInterval(timer.current); setError(s.error || "Generation failed."); setPhase("error"); }
    } catch (err) {
      clearInterval(timer.current); setError(err.message); setPhase("error");
    }
  }

  async function download() {
    const res = await fetch(`/api/books/${jobId}/pdf?name=${encodeURIComponent(name.trim())}`);
    if (!res.ok) return setError("Could not download the book. It may have expired.");
    const url = URL.createObjectURL(await res.blob());
    const a = document.createElement("a");
    a.href = url;
    a.download = `Jack-and-the-Beanstalk-${name.trim().replace(/[^\w-]+/g, "_")}.pdf`;
    document.body.appendChild(a); a.click(); a.remove();
    URL.revokeObjectURL(url);
  }

  function reset() { setPhase("form"); setError(""); setJobId(null); }

  const canSubmit = file && name.trim() && consent;

  return (
    <main className="card">
      <h1>Jack and the Beanstalk</h1>
      <p className="sub">A 20-page storybook starring your child.</p>

      {phase === "form" && (
        <form onSubmit={onSubmit}>
          <label className="drop">
            {preview ? <img src={preview} alt="Chosen child" /> : <span>Choose one clear, front-facing photo</span>}
            <input type="file" accept="image/jpeg,image/png" onChange={onPick} />
          </label>
          <p className="hint">Tips: one child only, face fully visible, good light, no sunglasses or hats.</p>

          <label>Child's name
            <input value={name} maxLength={30} onChange={(e) => setName(e.target.value)} placeholder="e.g. Mason" required />
          </label>

          <label>About me <span className="count">{details.length}/400</span>
            <textarea value={details} maxLength={400} rows={4} onChange={(e) => setDetails(e.target.value)}
              placeholder="Age, favourite food, hobbies, best friend, pets…" />
          </label>

          <label className="check">
            <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)} />
            <span>I'm the parent/guardian and I agree this photo is sent to an AI image service to make the book.
              It is not stored by this app and is discarded after generation.</span>
          </label>

          {error && <p className="error">{error}</p>}
          <button disabled={!canSubmit}>Create my book</button>
        </form>
      )}

      {phase === "working" && (
        <div className="status">
          <p>Painting page {Math.min(progress + 1, 20)} of 20… this takes a few minutes.</p>
          <progress max="20" value={progress} />
        </div>
      )}

      {phase === "done" && (
        <div className="status">
          <p>Your book is ready!</p>
          <button onClick={download}>Download PDF</button>
          <button className="link" onClick={reset}>Make another</button>
          {error && <p className="error">{error}</p>}
        </div>
      )}

      {phase === "error" && (
        <div className="status">
          <p className="error">{error}</p>
          <button onClick={reset}>Try again</button>
        </div>
      )}
    </main>
  );
}
