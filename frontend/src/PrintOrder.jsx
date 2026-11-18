import { useState } from "react";
import { api, readJson } from "./api.js";

export const money = (cents) => `$${(cents / 100).toFixed(2)}`;

const EMPTY = { name: "", street1: "", street2: "", city: "", stateCode: "", postcode: "", countryCode: "US", phoneNumber: "", email: "" };

// Shipping form for one printed copy of a saved book. Submitting gets a quote from the printer (Lulu)
// and sends the customer to Stripe to pay the flat price.
export default function PrintOrder({ book, priceCents, onClose }) {
  const [a, setA] = useState(EMPTY);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const set = (k) => (e) => setA((x) => ({ ...x, [k]: e.target.value }));

  async function submit(e) {
    e.preventDefault();
    setBusy(true); setError("");
    try {
      const res = await api("/api/print-orders", {
        method: "POST", headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ bookId: book.id, address: a, origin: window.location.origin }),
      });
      const data = await readJson(res);
      if (!res.ok) throw new Error(data.error || `Could not start the order (HTTP ${res.status})`);
      if (data.checkoutUrl) { window.location.assign(data.checkoutUrl); return; }
      onClose(true);                                       // payments disabled (local dev): already sent
    } catch (err) {
      setError(err.message);
      setBusy(false);
    }
  }

  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true" aria-labelledby="print-title"
      onClick={(e) => { if (e.target === e.currentTarget && !busy) onClose(false); }}>
      <form className="modal" onSubmit={submit}>
        <h2 id="print-title">Order a printed copy</h2>
        <p className="hint">
          {book.storyTitle || "Comic"} starring {book.name}: a 6 × 9 in full-colour book, printed and mailed to you
          for <strong>{money(priceCents)}</strong>, shipping included.
        </p>
        <label className="field"><span className="field-label">Full name</span>
          <input value={a.name} onChange={set("name")} maxLength={60} required autoComplete="name" /></label>
        <label className="field"><span className="field-label">Street address</span>
          <input value={a.street1} onChange={set("street1")} maxLength={100} required autoComplete="address-line1" /></label>
        <label className="field"><span className="field-label">Apartment, suite (optional)</span>
          <input value={a.street2} onChange={set("street2")} maxLength={100} autoComplete="address-line2" /></label>
        <div className="field-grid">
          <label className="field"><span className="field-label">City</span>
            <input value={a.city} onChange={set("city")} maxLength={60} required autoComplete="address-level2" /></label>
          <label className="field"><span className="field-label">State</span>
            <input value={a.stateCode} onChange={set("stateCode")} maxLength={10} placeholder="NY" autoComplete="address-level1" /></label>
          <label className="field"><span className="field-label">Postal code</span>
            <input value={a.postcode} onChange={set("postcode")} maxLength={20} required autoComplete="postal-code" /></label>
          <label className="field"><span className="field-label">Country</span>
            <input value={a.countryCode} onChange={set("countryCode")} maxLength={2} required placeholder="US" autoComplete="country" /></label>
        </div>
        <label className="field"><span className="field-label">Phone (for the courier)</span>
          <input type="tel" value={a.phoneNumber} onChange={set("phoneNumber")} maxLength={25} required autoComplete="tel" /></label>
        <label className="field"><span className="field-label">Email for shipping updates (optional)</span>
          <input type="email" value={a.email} onChange={set("email")} maxLength={120} autoComplete="email" /></label>
        {error && <p className="error">{error}</p>}
        <div className="row">
          <button className="btn btn-primary" disabled={busy}>{busy ? "Checking shipping…" : `Continue to payment · ${money(priceCents)}`}</button>
          <button type="button" className="btn btn-ghost" disabled={busy} onClick={() => onClose(false)}>Cancel</button>
        </div>
      </form>
    </div>
  );
}

const STATUS = {
  CREATED: "Order received", UNPAID: "Order received", PAYMENT_IN_PROGRESS: "Order received",
  PRODUCTION_DELAYED: "Waiting to print", PRODUCTION_READY: "Waiting to print", IN_PRODUCTION: "Printing",
  SHIPPED: "Shipped", REJECTED: "Problem with the order", CANCELED: "Canceled", ERROR: "Problem with the order",
};

export function orderStatus(o) {
  if (o.status === "FAILED") return "Problem with the order";
  if (o.status === "SUBMITTING") return "Sending to the printer";
  return STATUS[o.luluStatus] || "Order received";
}
