import { useCallback, useEffect, useState } from "react";
import { authEvents, postJson, readJson } from "./api.js";
import PasswordGate from "./PasswordGate.jsx";
import GoogleSignIn from "./GoogleSignIn.jsx";
import Maker from "./Maker.jsx";
import Library from "./Library.jsx";
import { Backdrop, Brand } from "./Scenery.jsx";

// Three stages, decided by the server (/api/auth/status):
//   1. site password  ->  2. Google sign-in  ->  3. the app (make a book / My books)
export default function App() {
  const [auth, setAuth] = useState(null);
  const [loadError, setLoadError] = useState("");
  // make | library. Coming back from paying for a printed copy lands on the bookshelf, where the order is confirmed.
  const [view, setView] = useState(() => /[?&]print(_canceled)?=/.test(window.location.search) ? "library" : "make");

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
    <Backdrop>
      <main className="notice-card">
        <Brand />
        <p className="error">{loadError}</p>
        <button className="btn btn-primary" onClick={refresh}>Try again</button>
      </main>
    </Backdrop>
  );
  if (!auth) return <Backdrop><p className="loading">Opening the shop…</p></Backdrop>;
  if (!auth.passwordOk) return <PasswordGate onDone={refresh} />;
  if (!auth.user) return <GoogleSignIn clientId={auth.googleClientId} onDone={refresh} />;

  return (
    <Backdrop>
      <header className="topbar">
        <div className="topbar-inner">
          <Brand />
          <nav className="topnav" aria-label="Main">
            <button className={view === "make" ? "navlink on" : "navlink"} onClick={() => setView("make")}>Make a comic</button>
            <button className={view === "library" ? "navlink on" : "navlink"} onClick={() => setView("library")}>My bookshelf</button>
          </nav>
          <div className="who">
            {auth.user.picture && <img src={auth.user.picture} alt="" referrerPolicy="no-referrer" />}
            <span title={auth.user.email}>{auth.user.name || auth.user.email}</span>
            <button className="navlink subtle" onClick={signOut}>Sign out</button>
          </div>
        </div>
      </header>
      <div className="page">
        {view === "make"
          ? <Maker onLibrary={() => setView("library")} />
          : <Library onNew={() => setView("make")} />}
      </div>
      <footer className="sitefoot">
        <p>Once Upon a Comic · Personalized comics from classic tales · Secure checkout by Stripe</p>
      </footer>
    </Backdrop>
  );
}
