import { useEffect, useRef, useState } from "react";
import { postJson } from "./api.js";

function loadGoogleScript() {
  return new Promise((resolve, reject) => {
    if (window.google?.accounts?.id) return resolve();
    const fail = () => reject(new Error("Could not load Google sign-in. Check your connection or ad blocker and reload."));
    const existing = document.getElementById("google-gsi");
    if (existing) {
      existing.addEventListener("load", resolve);
      existing.addEventListener("error", fail);
      return;
    }
    const s = document.createElement("script");
    s.id = "google-gsi";
    s.src = "https://accounts.google.com/gsi/client";
    s.async = true;
    s.onload = resolve;
    s.onerror = fail;
    document.head.appendChild(s);
  });
}

// Page 2: sign in with Google. Books are saved to (and listed from) this account.
export default function GoogleSignIn({ clientId, onDone }) {
  const buttonRef = useRef(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    if (!clientId) return;
    let cancelled = false;
    loadGoogleScript().then(() => {
      if (cancelled || !buttonRef.current) return;
      window.google.accounts.id.initialize({
        client_id: clientId,
        auto_select: false,
        callback: async ({ credential }) => {
          setBusy(true);
          setError("");
          try {
            await postJson("/api/auth/google", { credential });
            onDone();
          } catch (err) {
            setError(err.message);
            setBusy(false);
          }
        },
      });
      window.google.accounts.id.renderButton(buttonRef.current, {
        theme: "outline", size: "large", shape: "pill", text: "signin_with", width: 280,
      });
    }).catch((err) => !cancelled && setError(err.message));
    return () => { cancelled = true; };
  }, [clientId, onDone]);

  return (
    <main className="card gate">
      <div className="gate-icon" aria-hidden="true">📖</div>
      <h1>Sign in</h1>
      <p className="sub">Sign in with Google so every book you make is saved to your account and waiting for you next time.</p>
      {clientId
        ? <div className="google-btn" ref={buttonRef} />
        : <p className="error">Google sign-in has not been set up on the server yet (GOOGLE_CLIENT_ID is missing).</p>}
      {busy && <p className="hint">Signing you in…</p>}
      {error && <p className="error" role="alert">{error}</p>}
    </main>
  );
}
