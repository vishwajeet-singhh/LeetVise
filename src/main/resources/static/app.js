"use strict";

const DAY = 86400;
const $ = (id) => document.getElementById(id);

let data = { problems: [], log: [], now: Date.now() / 1000 };
let pick = null;           // currently picked problem slug
let noteSlug = null;       // problem whose note is open
const f = loadFilters();   // current filters
let sort = f.sort || { key: "lastSolved", dir: -1 };

// ------------------------------------------------------------------ api

async function api(path, body) {
  const res = await fetch("/api/" + path, body === undefined ? {} : {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = await res.json().catch(() => ({ error: "Bad response from server" }));
  if (!res.ok) throw new Error(json.error || "Request failed");
  return json;
}

async function load(path = "state", body) {
  const s = await api(path, body);
  if (s.problems) {
    data = s;
    render();
  }
  if (s.message) toast(s.message);
  return s;
}

// ------------------------------------------------------------------ helpers

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const now = () => Date.now() / 1000;
const daysAgo = (ts) => (ts ? Math.floor((now() - ts) / DAY) : Infinity);

function rel(ts) {
  if (!ts) return "—";
  const d = (now() - ts) / DAY;
  if (d < 0) {
    const a = Math.ceil(-d);
    return a <= 1 ? "tomorrow" : `in ${a} days`;
  }
  if (d < 1) return "today";
  if (d < 2) return "yesterday";
  if (d < 30) return `${Math.floor(d)} days ago`;
  if (d < 365) return `${Math.floor(d / 30)} mo ago`;
  return `${(d / 365).toFixed(1)} yr ago`;
}

const dateStr = (ts) => (ts ? new Date(ts * 1000).toLocaleDateString(undefined, { day: "numeric", month: "short", year: "numeric" }) : "");
const tagsOf = (p) => (p.tags ? p.tags.split(",").map((t) => t.trim()).filter(Boolean) : []);
const touched = (p) => Math.max(p.lastSolved || 0, p.lastRevised || 0);

function isDue(p) {
  if (p.nextReview) return p.nextReview <= now();
  // never revised: due once it's been a week since you solved it
  return p.lastSolved > 0 && now() - p.lastSolved > 7 * DAY;
}

function stars(c) {
  if (!c) return '<span class="muted">—</span>';
  const names = ["", "Again", "Hard", "Good", "Easy"];
  let bars = "";
  for (let i = 1; i <= 4; i++) bars += `<i class="${i <= c ? "on c" + c : ""}"></i>`;
  return `<span class="meter" title="Last rated ${names[c]}">${bars}</span>`;
}

const diffTag = (d) => d ? `<span class="diff ${esc(d)}"><i class="dot ${esc(d).toLowerCase()}"></i>${esc(d)}</span>` : '<span class="muted">—</span>';

function toast(msg, err = false) {
  const t = $("toast");
  t.textContent = msg;
  t.className = "toast show" + (err ? " err" : "");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.className = "toast" + (err ? " err" : "")), err ? 6000 : 3000);
}

function loadFilters() {
  const def = { q: "", diffs: ["Easy", "Medium", "Hard"], range: "any", tag: "", due: false, never: false, weak: false };
  try {
    return Object.assign(def, JSON.parse(localStorage.getItem("leetvise.filters") || "{}"));
  } catch {
    return def;
  }
}

function saveFilters() {
  try {
    localStorage.setItem("leetvise.filters", JSON.stringify({ ...f, sort }));
  } catch { /* storage unavailable – fine */ }
}

// ------------------------------------------------------------------ filtering

function filtered() {
  const q = f.q.trim().toLowerCase();
  return data.problems.filter((p) => {
    if (p.difficulty && !f.diffs.includes(p.difficulty)) return false;
    if (f.tag && !tagsOf(p).includes(f.tag)) return false;
    if (f.due && !isDue(p)) return false;
    if (f.never && p.timesRevised > 0) return false;
    if (f.weak && !(p.confidence === 1 || p.confidence === 2)) return false;
    if (f.range !== "any") {
      const n = +f.range.slice(1);
      const d = daysAgo(p.lastSolved);
      if (f.range[0] === "w" && !(d < n)) return false;
      if (f.range[0] === "o" && !(d >= n)) return false;
    }
    if (q) {
      const hay = `${p.id} ${p.title} ${p.slug} ${p.tags} ${p.notes}`.toLowerCase();
      if (!hay.includes(q)) return false;
    }
    return true;
  });
}

function sorted(list) {
  const { key, dir } = sort;
  const order = { Easy: 1, Medium: 2, Hard: 3 };
  const val = (p) => {
    if (key === "id") return +p.id || 99999;
    if (key === "difficulty") return order[p.difficulty] || 0;
    if (key === "title") return p.title.toLowerCase();
    if (key === "nextReview") return p.nextReview || (isDue(p) ? 1 : 9e12);
    return p[key] || 0;
  };
  return [...list].sort((a, b) => {
    const x = val(a), y = val(b);
    return (x < y ? -1 : x > y ? 1 : 0) * dir;
  });
}

// ------------------------------------------------------------------ random pick (weighted)

function weight(p) {
  const days = Math.min(daysAgo(touched(p)), 365);
  let w = 1 + days / 5;                                   // longer untouched -> more likely
  w *= { 0: 1.5, 1: 2.5, 2: 1.7, 3: 1, 4: 0.5 }[p.confidence || 0];
  if (isDue(p)) w *= 3;                                   // due problems first
  if (p.slug === pick) w *= 0.05;                         // avoid repeating the same one
  return w;
}

function pickRandom() {
  const pool = filtered();
  if (!pool.length) {
    pick = null;
    renderPick();
    toast("No problems match your filters", true);
    return;
  }
  const total = pool.reduce((s, p) => s + weight(p), 0);
  let r = Math.random() * total;
  for (const p of pool) {
    r -= weight(p);
    if (r <= 0) { pick = p.slug; break; }
  }
  if (r > 0) pick = pool[pool.length - 1].slug;
  renderPick();
}

function why(p) {
  const bits = [];
  const d = daysAgo(touched(p));
  if (d !== Infinity) bits.push(d === 0 ? "touched today" : `not touched for ${d} day${d === 1 ? "" : "s"}`);
  if (!p.timesRevised) bits.push("never revised");
  else bits.push(`revised ${p.timesRevised}×`);
  if (p.confidence === 1) bits.push("you rated it Again last time");
  if (p.confidence === 2) bits.push("you found it Hard last time");
  if (isDue(p)) bits.push("due for revision");
  return bits.join(" · ");
}

// ------------------------------------------------------------------ rendering

function render() {
  renderHeader();
  renderStats();
  renderTagOptions();
  renderTable();
  renderPick();
  renderLog();
  renderTopics();
}

function renderHeader() {
  $("subtitle").textContent = `@${data.username} · ${data.lastSync ? "synced " + data.lastSync : "not synced yet"}`;
  $("excel-hint").innerHTML = `Everything is saved in <code>${esc(data.excelPath)}</code> (backups in the <code>backups</code> folder next to it). You can edit it in Excel too – the app picks up changes.`;
  const b = $("banner");
  const total = +data.profileTotal || 0;
  if (!data.problems.length) {
    b.hidden = false;
    b.innerHTML = `<b>Welcome!</b> Click <b>Sync LeetCode</b> to pull your solved problems into the table.`;
  } else if (!data.authed && total > data.problems.length) {
    b.hidden = false;
    b.innerHTML = `<b>${data.problems.length} of ${total}</b> solved problems loaded. LeetCode only shares your ~20 most recent solves publicly –
      set <code>LEETCODE_SESSION</code> (see README) and sync again to load all of them.`;
  } else {
    b.hidden = true;
  }
}

function renderStats() {
  const ps = data.problems;
  const count = (d) => ps.filter((p) => p.difficulty === d).length;
  const due = ps.filter(isDue).length;
  const weekAgo = now() - 7 * DAY;
  const week = data.log.filter((l) => l.date >= weekAgo).length;
  const total = +data.profileTotal || 0;
  const s = streak();
  const cards = [
    { dot: "", label: "Solved", value: ps.length, sub: total ? `of ${total} on LeetCode` : "in your sheet", act: "all" },
    { dot: "easy", label: "Easy", value: count("Easy"), sub: "click to filter", act: "Easy" },
    { dot: "medium", label: "Medium", value: count("Medium"), sub: "click to filter", act: "Medium" },
    { dot: "hard", label: "Hard", value: count("Hard"), sub: "click to filter", act: "Hard" },
    { dot: "due", label: "Due now", value: due, sub: "need revision", act: "due" },
    { dot: "", label: "Revised", value: week, sub: `last 7 days · ${s}d streak`, act: "" },
  ];
  $("stats").innerHTML = cards.map((c) => `
    <button class="stat${c.act ? "" : " static"}" data-act="${c.act}">
      <div class="label">${c.dot ? `<i class="dot ${c.dot}"></i>` : ""}${c.label}</div>
      <div class="value">${c.value}</div>
      <div class="sub">${esc(c.sub)}</div>
    </button>`).join("");
}

function streak() {
  const days = new Set(data.log.map((l) => new Date(l.date * 1000).toDateString()));
  let n = 0;
  const d = new Date();
  if (!days.has(d.toDateString())) d.setDate(d.getDate() - 1); // today not done yet is fine
  while (days.has(d.toDateString())) { n++; d.setDate(d.getDate() - 1); }
  return n;
}

function renderTagOptions() {
  const tags = [...new Set(data.problems.flatMap(tagsOf))].sort();
  const sel = $("tag");
  sel.innerHTML = `<option value="">All topics</option>` + tags.map((t) => `<option ${t === f.tag ? "selected" : ""}>${esc(t)}</option>`).join("");
  if (f.tag && !tags.includes(f.tag)) f.tag = "";
}

function renderTable() {
  const list = sorted(filtered());
  $("count").textContent = `${list.length} of ${data.problems.length}`;
  document.querySelectorAll("th[data-sort]").forEach((th) => {
    const on = th.dataset.sort === sort.key;
    th.querySelector(".arrow")?.remove();
    if (on) th.insertAdjacentHTML("beforeend", `<span class="arrow">${sort.dir > 0 ? "▲" : "▼"}</span>`);
  });

  if (!list.length) {
    $("rows").innerHTML = `<tr class="empty-row"><td colspan="9">${data.problems.length ? "No problems match these filters." : "Nothing here yet – click “Sync LeetCode”."}</td></tr>`;
    return;
  }

  $("rows").innerHTML = list.map((p) => {
    const due = isDue(p);
    const next = p.nextReview
      ? (due ? `<span class="due-badge">Due</span><small>${rel(p.nextReview)}</small>` : `${rel(p.nextReview)}<small>${dateStr(p.nextReview)}</small>`)
      : (due ? `<span class="due-badge">Due</span><small>never revised</small>` : `<span class="muted">—</span>`);
    return `
      <tr class="${due ? "is-due" : ""}">
        <td class="num mono">${esc(p.id)}</td>
        <td class="title">
          <a href="${esc(p.url)}" target="_blank" rel="noopener">${esc(p.title || p.slug)}</a>
          ${p.notes ? `<div class="note-preview" title="${esc(p.notes)}">${esc(p.notes)}</div>` : ""}
        </td>
        <td>${diffTag(p.difficulty)}</td>
        <td class="col-tags"><div class="tags">${tagsOf(p).slice(0, 3).map((t) => `<button class="tag" data-tag="${esc(t)}">${esc(t)}</button>`).join("")}</div></td>
        <td class="when">${rel(p.lastSolved)}<small>${dateStr(p.lastSolved)}</small></td>
        <td class="when">${next}</td>
        <td class="num mono">${p.timesRevised || 0}×</td>
        <td class="col-conf">${stars(p.confidence)}</td>
        <td><div class="row-actions">
          <button class="btn sm ghost" data-note="${esc(p.slug)}" title="Notes">Notes</button>
          <button class="btn sm" data-revise="${esc(p.slug)}" title="Revise this now">Revise</button>
        </div></td>
      </tr>`;
  }).join("");
}

function renderPick() {
  const p = data.problems.find((x) => x.slug === pick);
  const el = $("pick");
  if (!p) {
    el.innerHTML = `
      <div class="pick-main">
        <div class="eyebrow mono">Practice</div>
        <h2>Pick a random problem to revise</h2>
        <p class="empty">Picks from what your filters show. Problems you haven't seen in a while, found hard, or that are due come up more often.</p>
      </div>
      <div class="pick-actions">
        <button class="btn primary lg" id="pick-btn">Pick random <kbd>R</kbd></button>
      </div>`;
    return;
  }
  el.innerHTML = `
    <div class="pick-main">
      <div class="eyebrow mono">Revise this one</div>
      <h2><a href="${esc(p.url)}" target="_blank" rel="noopener">${p.id ? `<span class="pid mono">${esc(p.id)}.</span> ` : ""}${esc(p.title || p.slug)} <span class="ext">↗</span></a></h2>
      <div class="meta">
        ${diffTag(p.difficulty)}
        ${tagsOf(p).map((t) => `<span class="tag static">${esc(t)}</span>`).join("")}
      </div>
      <div class="why mono">${esc(why(p))}</div>
      ${p.notes ? `<div class="pick-note">${esc(p.notes)}</div>` : ""}
    </div>
    <div class="pick-actions">
      <div class="pick-top">
        <a class="btn primary" href="${esc(p.url)}" target="_blank" rel="noopener" id="pick-open">Open problem <kbd>O</kbd></a>
        <button class="btn" id="pick-btn">Another <kbd>R</kbd></button>
      </div>
      <div class="rate-label mono">after solving it again</div>
      <div class="rate">
        <button class="btn r-again" data-rate="again" title="Couldn't do it – show again tomorrow"><i class="dot hard"></i>Again</button>
        <button class="btn r-hard" data-rate="hard" title="Got it with difficulty"><i class="dot medium"></i>Hard</button>
        <button class="btn r-good" data-rate="good" title="Solved it fine"><i class="dot good"></i>Good</button>
        <button class="btn r-easy" data-rate="easy" title="Trivial now"><i class="dot easy"></i>Easy</button>
      </div>
    </div>`;
}

function renderLog() {
  const items = [...data.log].reverse();
  $("log").innerHTML = items.length
    ? items.map((l) => `
        <div class="log-item">
          <span><a href="https://leetcode.com/problems/${esc(l.slug)}/" target="_blank" rel="noopener">${esc(l.title || l.slug)}</a></span>
          <span class="log-meta"><span class="rating ${esc(l.rating)}">${esc(l.rating)}</span><span class="muted mono">${rel(l.date)}</span></span>
        </div>`).join("")
    : `<p class="muted small">No revisions yet. Pick a random problem, solve it again, and rate how it went – it's logged in the “Revision Log” sheet.</p>`;
}

function renderTopics() {
  const counts = {};
  data.problems.forEach((p) => tagsOf(p).forEach((t) => (counts[t] = (counts[t] || 0) + 1)));
  const top = Object.entries(counts).sort((a, b) => b[1] - a[1]).slice(0, 8);
  const max = top.length ? top[0][1] : 1;
  $("topics").innerHTML = top.length
    ? top.map(([t, n]) => `
        <div class="topic-bar" data-tag="${esc(t)}">
          <span class="name">${esc(t)}</span>
          <span class="track"><span class="fill" style="width:${(n / max) * 100}%;display:block"></span></span>
          <span class="n mono">${n}</span>
        </div>`).join("")
    : `<p class="muted small">Topics show up after your first sync.</p>`;
}

function syncFilterUI() {
  $("q").value = f.q;
  $("range").value = f.range;
  document.querySelectorAll("#diff-chips .chip").forEach((c) => c.classList.toggle("on", f.diffs.includes(c.dataset.d)));
  $("f-due").classList.toggle("on", f.due);
  $("f-never").classList.toggle("on", f.never);
  $("f-weak").classList.toggle("on", f.weak);
}

function changed() {
  saveFilters();
  syncFilterUI();
  renderTagOptions();
  renderTable();
}

// ------------------------------------------------------------------ actions

async function rate(rating) {
  if (!pick) return;
  const title = (data.problems.find((p) => p.slug === pick) || {}).title;
  try {
    await load("revise", { slug: pick, rating });
    toast(`Saved “${title}” as ${rating} ✓`);
    pickRandom();
  } catch (e) {
    toast(e.message, true);
  }
}

async function sync(full = false) {
  const btn = $("btn-sync");
  btn.disabled = true;
  btn.textContent = data.authed ? "Syncing… (can take a minute)" : "Syncing…";
  try {
    await load("sync", { full });
  } catch (e) {
    toast(e.message, true);
  } finally {
    btn.disabled = false;
    btn.textContent = "Sync LeetCode";
  }
}

function openNote(slug) {
  const p = data.problems.find((x) => x.slug === slug);
  if (!p) return;
  noteSlug = slug;
  $("modal-title").textContent = `Notes · ${p.title}`;
  $("modal-text").value = p.notes || "";
  $("modal").classList.add("open");
  $("modal-text").focus();
}

function closeNote() {
  $("modal").classList.remove("open");
  noteSlug = null;
}

function exportCsv() {
  const cols = ["id", "title", "url", "difficulty", "tags", "lastSolved", "timesRevised", "nextReview", "confidence", "notes"];
  const cell = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const rows = sorted(filtered()).map((p) => cols.map((c) =>
    cell(c === "lastSolved" || c === "nextReview" ? (p[c] ? new Date(p[c] * 1000).toISOString().slice(0, 10) : "") : p[c])).join(","));
  const blob = new Blob([cols.join(",") + "\n" + rows.join("\n")], { type: "text/csv" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `leetvise-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

// ------------------------------------------------------------------ events

$("btn-sync").addEventListener("click", (e) => sync(e.shiftKey));
$("btn-csv").addEventListener("click", exportCsv);
$("btn-excel").addEventListener("click", () => api("open-excel", {}).then((r) => toast(r.message)).catch((e) => toast(e.message, true)));

$("q").addEventListener("input", (e) => { f.q = e.target.value; changed(); });
$("range").addEventListener("change", (e) => { f.range = e.target.value; changed(); });
$("tag").addEventListener("change", (e) => { f.tag = e.target.value; changed(); });
$("diff-chips").addEventListener("click", (e) => {
  const d = e.target.closest(".chip")?.dataset.d;
  if (!d) return;
  f.diffs = f.diffs.includes(d) ? f.diffs.filter((x) => x !== d) : [...f.diffs, d];
  changed();
});
["due", "never", "weak"].forEach((k) => $("f-" + k).addEventListener("click", () => { f[k] = !f[k]; changed(); }));
$("reset").addEventListener("click", () => {
  Object.assign(f, { q: "", diffs: ["Easy", "Medium", "Hard"], range: "any", tag: "", due: false, never: false, weak: false });
  changed();
});

$("stats").addEventListener("click", (e) => {
  const act = e.target.closest(".stat")?.dataset.act;
  if (!act) return;
  if (act === "all") $("reset").click();
  else if (act === "due") { f.due = !f.due; changed(); }
  else { f.diffs = f.diffs.length === 1 && f.diffs[0] === act ? ["Easy", "Medium", "Hard"] : [act]; changed(); }
});

document.querySelectorAll("th[data-sort]").forEach((th) => th.addEventListener("click", () => {
  const key = th.dataset.sort;
  sort = sort.key === key ? { key, dir: -sort.dir } : { key, dir: key === "title" || key === "id" || key === "nextReview" ? 1 : -1 };
  saveFilters();
  renderTable();
}));

document.addEventListener("click", (e) => {
  const t = e.target.closest("[data-tag],[data-note],[data-revise],[data-rate],#pick-btn");
  if (!t) return;
  if (t.id === "pick-btn") pickRandom();
  else if (t.dataset.rate) rate(t.dataset.rate);
  else if (t.dataset.note) openNote(t.dataset.note);
  else if (t.dataset.revise) { pick = t.dataset.revise; renderPick(); window.scrollTo({ top: 0, behavior: "smooth" }); }
  else if (t.dataset.tag) { f.tag = t.dataset.tag; changed(); }
});

$("add-btn").addEventListener("click", async () => {
  const v = $("add-input").value.trim();
  if (!v) return;
  try {
    await load("add", { slug: v });
    $("add-input").value = "";
  } catch (e) {
    toast(e.message, true);
  }
});
$("add-input").addEventListener("keydown", (e) => { if (e.key === "Enter") $("add-btn").click(); });

$("modal-cancel").addEventListener("click", closeNote);
$("modal").addEventListener("click", (e) => { if (e.target.id === "modal") closeNote(); });
$("modal-save").addEventListener("click", async () => {
  try {
    await load("note", { slug: noteSlug, notes: $("modal-text").value });
    closeNote();
  } catch (e) {
    toast(e.message, true);
  }
});

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") return closeNote();
  const typing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName);
  if (typing || e.metaKey || e.ctrlKey || e.altKey) return;
  if (e.key === "r" || e.key === "R") { e.preventDefault(); pickRandom(); }
  else if (e.key === "/") { e.preventDefault(); $("q").focus(); }
  else if ((e.key === "o" || e.key === "O") && pick) { $("pick-open")?.click(); }
  else if (pick && "1234".includes(e.key)) rate(["again", "hard", "good", "easy"][+e.key - 1]);
});

// refresh when you come back to the tab (picks up edits made in Excel)
document.addEventListener("visibilitychange", () => { if (!document.hidden) load().catch(() => {}); });

syncFilterUI();
load().catch((e) => toast(e.message, true));
