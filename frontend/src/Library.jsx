import { useEffect, useState } from "react";
import { api, downloadPdf, readJson } from "./api.js";
import { HoodScene } from "./art.jsx";
import PrintOrder, { money, orderStatus } from "./PrintOrder.jsx";

// "My books": everything this Google account has made, newest first, plus any printed copies ordered.
export default function Library({ onNew }) {
  const [books, setBooks] = useState(null);
  const [orders, setOrders] = useState([]);
  const [print, setPrint] = useState({ enabled: false, priceCents: 2499 });
  const [ordering, setOrdering] = useState(null);   // the book whose print form is open
  const [error, setError] = useState("");
  const [notice, setNotice] = useState("");

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
  async function loadOrders() {
    try {
      const res = await api("/api/print-orders");
      if (res.ok) setOrders(await readJson(res));
    } catch { /* the bookshelf still works without it */ }
  }
  useEffect(() => {
    load();
    loadOrders();
    api("/api/print/config").then(readJson).then(setPrint).catch(() => {});
  }, []);

  // Returning from Stripe Checkout for a printed copy: ?print=<id>&session_id=cs_...  (or ?print_canceled=1)
  useEffect(() => {
    const q = new URLSearchParams(window.location.search);
    if (!q.has("print") && !q.has("print_canceled")) return;
    window.history.replaceState({}, "", window.location.pathname);
    if (q.has("print_canceled")) { setNotice("Payment was canceled, so no printed copy was ordered and you weren't charged."); return; }
    confirmOrder(q.get("print"), q.get("session_id"));
  }, []);

  async function confirmOrder(id, sessionId) {
    setNotice("Sending your book to the printer…"); setError("");
    try {
      const res = await api(`/api/print-orders/${id}/confirm`, {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ sessionId: sessionId || "", origin: window.location.origin }),
      });
      const data = await readJson(res);
      if (!res.ok) throw new Error(data.error || `Could not confirm the order (HTTP ${res.status})`);
      if (data.error) { setNotice(""); setError(data.error); }
      else setNotice("Thank you! Your printed copy is on order. Its status is under Printed copies below.");
    } catch (err) {
      setNotice(""); setError(err.message);
    }
    loadOrders();
  }

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
  const bookName = (id) => books.find((b) => b.id === id);

  return (
    <section className="shelf-page">
      <div className="section-head">
        <p className="eyebrow">Your collection</p>
        <h1>My bookshelf</h1>
      </div>
      {notice && <p className="notice">{notice}</p>}
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
                  {print.enabled && (
                    <button className="btn btn-ghost btn-sm" onClick={() => setOrdering(b)}>
                      Order print · {money(print.priceCents)}
                    </button>
                  )}
                  <button className="btn btn-ghost btn-sm danger" onClick={() => remove(b)}>Delete</button>
                </div>
              </div>
            </li>
          ))}
        </ul>
      )}

      {orders.length > 0 && (
        <div className="orders">
          <h2>Printed copies</h2>
          <ul className="order-list">
            {orders.map((o) => {
              const b = bookName(o.bookId);
              return (
                <li key={o.id} className="order">
                  <div>
                    <strong>{b ? `${b.storyTitle || "Comic"} starring ${b.name}` : "Printed copy"}</strong>
                    <span className="hint">
                      Ordered {new Date(o.createdAt).toLocaleDateString()} · ships to {o.shipTo} · {money(o.priceCents)}
                    </span>
                    {o.error && <span className="hint error-text">{o.error}</span>}
                  </div>
                  <div className="row">
                    <span className="tag">{orderStatus(o)}</span>
                    {o.trackingUrl && <a className="btn btn-ghost btn-sm" href={o.trackingUrl} target="_blank" rel="noreferrer">Track</a>}
                    {o.status === "FAILED" && <button className="btn btn-ghost btn-sm" onClick={() => confirmOrder(o.id, "")}>Try again</button>}
                  </div>
                </li>
              );
            })}
          </ul>
        </div>
      )}

      {ordering && (
        <PrintOrder book={ordering} priceCents={print.priceCents}
          onClose={(sent) => { setOrdering(null); if (sent) { setNotice("Your printed copy is on order."); loadOrders(); } }} />
      )}
    </section>
  );
}
