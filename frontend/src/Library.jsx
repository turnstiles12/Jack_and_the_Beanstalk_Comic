import { useEffect, useState } from "react";
import { api, downloadPdf, readJson } from "./api.js";
import { HoodScene } from "./art.jsx";

// "My books": everything this Google account has made, newest first.
export default function Library({ onNew }) {
  const [books, setBooks] = useState(null);
  const [error, setError] = useState("");

  async function load() {
    try {
      const res = await api("/api/books");
      if (!res.ok) return;                       // 401 is handled globally
      setBooks(await readJson(res));
    } catch (err) {
      setError(err.message);
      setBooks([]);
    }
  }
  useEffect(() => { load(); }, []);

  async function remove(b) {
    if (!window.confirm(`Delete the book for ${b.name}? This can't be undone.`)) return;
    const res = await api(`/api/books/${b.id}`, { method: "DELETE" });
    if (res.ok) setBooks((list) => list.filter((x) => x.id !== b.id));
    else setError("Could not delete that book.");
  }

  async function download(b) {
    try { await downloadPdf(b.id, b.name, b.storyTitle); } catch (err) { setError(err.message); }
  }

  if (books === null) return <p className="loading">Loading your bookshelf…</p>;

  return (
    <section className="shelf-page">
      <div className="section-head">
        <p className="eyebrow">Your collection</p>
        <h1>My bookshelf</h1>
      </div>
      {error && <p className="error">{error}</p>}
      {books.length === 0 ? (
        <div className="empty-shelf">
          <HoodScene className="empty-art" />
          <h2>Your shelf is empty. For now!</h2>
          <p>Every comic you make is kept here, ready to download any time.</p>
          <button className="btn btn-primary" onClick={onNew}>Make my first comic</button>
        </div>
      ) : (
        <ul className="shelf">
          {books.map((b) => (
            <li key={b.id} className="book-card">
              <div className="book-thumb">
                <img src={`/api/books/${b.id}/thumb`} alt="" loading="lazy"
                  onError={(e) => { e.currentTarget.style.visibility = "hidden"; }} />
              </div>
              <div className="book-info">
                <span className="tag">{b.storyTitle || "Comic"}</span>
                <strong className="book-name">Starring {b.name}</strong>
                <span className="hint">{new Date(b.createdAt).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })}</span>
                <div className="row">
                  <button className="btn btn-primary btn-sm" onClick={() => download(b)}>Download PDF</button>
                  <button className="btn btn-ghost btn-sm danger" onClick={() => remove(b)}>Delete</button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
