import { useEffect, useState } from "react";
import { api, downloadPdf, readJson } from "./api.js";

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
    try { await downloadPdf(b.id, b.name); } catch (err) { setError(err.message); }
  }

  if (books === null) return <p className="sub">Loading your books…</p>;

  return (
    <section>
      <h2>My books</h2>
      {error && <p className="error">{error}</p>}
      {books.length === 0 ? (
        <div className="empty">
          <p>You haven't made a book yet.</p>
          <button onClick={onNew}>Make my first book</button>
        </div>
      ) : (
        <ul className="shelf">
          {books.map((b) => (
            <li key={b.id} className="book">
              <img src={`/api/books/${b.id}/thumb`} alt="" loading="lazy"
                onError={(e) => { e.currentTarget.style.visibility = "hidden"; }} />
              <div className="book-info">
                <strong>{b.name}</strong>
                <span className="hint">{new Date(b.createdAt).toLocaleDateString(undefined, { year: "numeric", month: "short", day: "numeric" })}</span>
                <div className="row">
                  <button onClick={() => download(b)}>Download PDF</button>
                  <button className="link danger" onClick={() => remove(b)}>Delete</button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
