import { useState } from "react";
import { postJson } from "./api.js";
import { Brand, GateLayout } from "./Scenery.jsx";

// Page 1: the shared site password. Nothing else on the site loads until this is passed.
export default function PasswordGate({ onDone }) {
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(e) {
    e.preventDefault();
    setBusy(true);
    setError("");
    try {
      await postJson("/api/auth/password", { password });
      onDone();
    } catch (err) {
      setError(err.message);
      setPassword("");
    } finally {
      setBusy(false);
    }
  }

  return (
    <GateLayout>
      <Brand big />
      <p className="eyebrow">Members only</p>
      <h1 className="gate-title">Knock, knock! Who's there?</h1>
      <p className="lede">Our comic shop is open by invitation. Whisper the secret password to step inside and become the hero of your own tale.</p>
      <form onSubmit={submit} className="gate-form">
        <label className="field">
          <span className="field-label">Password</span>
          <span className="input-icon">
            <svg viewBox="0 0 24 24" width="20" height="20" aria-hidden="true"><path d="M7 11V8a5 5 0 0 1 10 0v3M6 11h12v9H6z" fill="none" stroke="currentColor" strokeWidth="2" strokeLinejoin="round" strokeLinecap="round" /></svg>
            <input type="password" value={password} autoFocus autoComplete="current-password"
              placeholder="Enter the password" onChange={(e) => setPassword(e.target.value)} required />
          </span>
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button className="btn btn-primary btn-block" disabled={busy || !password}>{busy ? "Opening the door…" : "Open the door"}</button>
      </form>
    </GateLayout>
  );
}
