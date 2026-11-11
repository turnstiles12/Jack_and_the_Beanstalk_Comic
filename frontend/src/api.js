// Parse a response without assuming JSON: proxies (Render, Vite) return HTML/plain text on 502/503/413.
export async function readJson(res) {
  const text = await res.text();
  try {
    return JSON.parse(text);
  } catch {
    const hint = res.status === 502 || res.status === 503 || res.status === 504
      ? "The server is starting up or restarting. Wait a minute and try again."
      : res.status === 413 ? "The photo is too large."
      : "The server sent an unexpected reply.";
    const e = new Error(`${hint} (HTTP ${res.status}${text ? `: ${text.replace(/<[^>]*>/g, " ").trim().slice(0, 120)}` : ""})`);
    e.nonJson = true;
    throw e;
  }
}

// Fired whenever a protected /api call comes back 401 (cookie expired, password changed, signed out in another tab).
export const authEvents = new EventTarget();

// fetch() for the protected API: same as fetch, but a 401 sends the user back to the right login page.
export async function api(url, opts) {
  const res = await fetch(url, { credentials: "same-origin", ...opts });
  if (res.status === 401) authEvents.dispatchEvent(new Event("lost"));
  return res;
}

export async function postJson(url, body) {
  const res = await fetch(url, {
    method: "POST",
    credentials: "same-origin",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const data = await readJson(res);
  if (!res.ok) throw new Error(data.error || `Request failed (HTTP ${res.status})`);
  return data;
}

// Download a PDF from the (cookie-protected) API as a file.
export async function downloadPdf(id, name) {
  const res = await api(`/api/books/${id}/pdf`);
  if (!res.ok) throw new Error("Could not download the book. It may have been deleted.");
  const url = URL.createObjectURL(await res.blob());
  const a = document.createElement("a");
  a.href = url;
  a.download = `Jack-and-the-Beanstalk-${(name || "My").replace(/[^\w-]+/g, "_")}.pdf`;
  document.body.appendChild(a); a.click(); a.remove();
  URL.revokeObjectURL(url);
}
