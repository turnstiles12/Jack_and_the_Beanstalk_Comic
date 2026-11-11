import { useState } from "react";
import { postJson } from "./api.js";

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
    <main className="card gate">
      <div className="gate-icon" aria-hidden="true">🔒</div>
      <h1>Jack and the Beanstalk</h1>
      <p className="sub">This storybook maker is private. Enter the password to come in.</p>
      <form onSubmit={submit}>
        <label>Password
          <input type="password" value={password} autoFocus autoComplete="current-password"
            onChange={(e) => setPassword(e.target.value)} required />
        </label>
        {error && <p className="error" role="alert">{error}</p>}
        <button disabled={busy || !password}>{busy ? "Checking…" : "Enter"}</button>
      </form>
    </main>
  );
}
