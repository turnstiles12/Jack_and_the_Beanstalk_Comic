import { useCallback, useEffect, useState } from "react";
import { authEvents, postJson, readJson } from "./api.js";
import PasswordGate from "./PasswordGate.jsx";
import GoogleSignIn from "./GoogleSignIn.jsx";
import Maker from "./Maker.jsx";
import Library from "./Library.jsx";

// Three stages, decided by the server (/api/auth/status):
//   1. site password  ->  2. Google sign-in  ->  3. the app (make a book / My books)
export default function App() {
  const [auth, setAuth] = useState(null);
  const [loadError, setLoadError] = useState("");
  const [view, setView] = useState("make"); // make | library

  const refresh = useCallback(async () => {
    try {
      setAuth(await readJson(await fetch("/api/auth/status", { credentials: "same-origin" })));
      setLoadError("");
    } catch (err) {
      setLoadError(err.message);
    }
  }, []);

  useEffect(() => {
    refresh();
    authEvents.addEventListener("lost", refresh);
    return () => authEvents.removeEventListener("lost", refresh);
  }, [refresh]);

  async function signOut() {
    try { await postJson("/api/auth/logout", {}); } catch { /* cookie may already be gone */ }
    window.google?.accounts?.id?.disableAutoSelect?.();
    setView("make");
    refresh();
  }

  if (loadError) return (
    <main className="card gate">
      <p className="error">{loadError}</p>
      <button onClick={refresh}>Try again</button>
    </main>
  );
  if (!auth) return <p className="sub center">Loading…</p>;
  if (!auth.passwordOk) return <PasswordGate onDone={refresh} />;
  if (!auth.user) return <GoogleSignIn clientId={auth.googleClientId} onDone={refresh} />;

  return (
    <>
      <header className="bar">
        <nav>
          <button className={view === "make" ? "tab on" : "tab"} onClick={() => setView("make")}>New book</button>
          <button className={view === "library" ? "tab on" : "tab"} onClick={() => setView("library")}>My books</button>
        </nav>
        <div className="who">
          {auth.user.picture && <img src={auth.user.picture} alt="" referrerPolicy="no-referrer" />}
          <span title={auth.user.email}>{auth.user.name || auth.user.email}</span>
          <button className="link" onClick={signOut}>Sign out</button>
        </div>
      </header>
      {view === "make"
        ? <Maker onLibrary={() => setView("library")} />
        : <main className="card wide"><Library onNew={() => setView("make")} /></main>}
    </>
  );
}
