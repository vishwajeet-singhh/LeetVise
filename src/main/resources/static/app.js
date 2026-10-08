/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

"use strict";

const DAY = 86400;
const $ = (id) => document.getElementById(id);
const LEVELS = ["Easy", "Medium", "Hard"];

let data = { problems: [], log: [], leetCodeSolved: {}, leetCodeTotal: {}, now: Date.now() / 1000 };
let ins = null;            // insights from /api/insights (all the maths happens in Java)
let pick = null;           // currently picked problem slug
let noteSlug = null;       // problem whose note is open
let view = viewFromHash();
const f = loadFilters();   // table filters (remembered in this browser)
let sort = f.sort || { key: "lastSolved", dir: -1 };
let topicSort = { key: "score", dir: -1 };
let topicLevel = "all";

// ================================================================== api

async function api(path, body) {
  const res = await fetch("/api/" + path, body === undefined ? {} : {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(body),
  });
  const json = await res.json().catch(() => ({ error: "Bad response from the server" }));
  if (!res.ok) throw new Error(json.error || "Request failed");
  return json;
}

async function load(path = "state", body) {
  const s = await api(path, body);
  if (s.problems) {
    data = s;
    try { ins = await api("insights"); } catch { ins = null; }
    render();
  }
  if (s.message) toast(s.message);
  return s;
}

// ================================================================== helpers

const esc = (s) => String(s ?? "").replace(/[&<>"']/g, (c) => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" }[c]));
const now = () => Date.now() / 1000;
const daysAgo = (ts) => (ts ? Math.floor((now() - ts) / DAY) : Infinity);
const pct = (x) => (x == null ? "—" : Math.round(x * 100) + "%");
const fmt = (n) => (n == null ? "—" : Number(n).toLocaleString());
const plural = (n, w) => `${n} ${n === 1 ? w : w.endsWith("y") && !/[aeiou]y$/.test(w) ? w.slice(0, -1) + "ies" : w + "s"}`;
const tagsOf = (p) => (p.tags ? p.tags.split(",").map((t) => t.trim()).filter(Boolean) : []);
const touched = (p) => Math.max(p.lastSolved || 0, p.lastRevised || 0);
const attemptsOf = (slug) => (ins && ins.attempts && ins.attempts[slug]) || null;
const lcUrl = (slug) => `https://leetcode.com/problems/${encodeURIComponent(slug)}/`;

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

function isDue(p) {
  if (p.nextReview) return p.nextReview <= now();
  // never revised: due once it's been a week since you solved it
  return p.lastSolved > 0 && now() - p.lastSolved > 7 * DAY;
}

function recallMeter(c) {
  if (!c) return '<span class="dash">—</span>';
  const names = ["", "Again", "Hard", "Good", "Easy"];
  let bars = "";
  for (let i = 1; i <= 4; i++) bars += `<i class="${i <= c ? "on c" + c : ""}"></i>`;
  return `<span class="meter" data-tip="Last rated ${names[c]}">${bars}</span>`;
}

const diffTag = (d) => (d ? `<span class="diff"><i class="dot ${esc(d).toLowerCase()}"></i>${esc(d)}</span>` : '<span class="dash">—</span>');

function toast(msg, err = false) {
  const t = $("toast");
  t.textContent = msg;
  t.className = "toast show" + (err ? " err" : "");
  clearTimeout(toast.timer);
  toast.timer = setTimeout(() => (t.className = "toast" + (err ? " err" : "")), err ? 7000 : 3200);
}

function loadFilters() {
  const def = { q: "", diffs: [...LEVELS], range: "any", tag: "", due: false, never: false, weak: false, struggled: false };
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

function viewFromHash() {
  return location.hash === "#insights" ? "insights" : "revise";
}

// ================================================================== filtering & sorting

function filtered() {
  const q = f.q.trim().toLowerCase();
  return data.problems.filter((p) => {
    if (p.difficulty && !f.diffs.includes(p.difficulty)) return false;
    if (f.tag && !tagsOf(p).includes(f.tag)) return false;
    if (f.due && !isDue(p)) return false;
    if (f.never && p.timesRevised > 0) return false;
    if (f.weak && !(p.confidence === 1 || p.confidence === 2)) return false;
    if (f.struggled && !((attemptsOf(p.slug) || {}).failed >= 2)) return false;
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
    if (key === "title") return (p.title || p.slug).toLowerCase();
    if (key === "nextReview") return p.nextReview || (isDue(p) ? 1 : 9e12);
    if (key === "failed") { const a = attemptsOf(p.slug); return a ? a.failed * 1000 + a.submissions : -1; }
    return p[key] || 0;
  };
  return [...list].sort((a, b) => {
    const x = val(a), y = val(b);
    return (x < y ? -1 : x > y ? 1 : 0) * dir;
  });
}

// ================================================================== random pick (weighted)

function weight(p) {
  const days = Math.min(daysAgo(touched(p)), 365);
  let w = 1 + days / 5;                                   // longer untouched -> more likely
  w *= { 0: 1.5, 1: 2.5, 2: 1.7, 3: 1, 4: 0.5 }[p.confidence || 0];
  const a = attemptsOf(p.slug);
  if (a && a.failed >= 2) w *= 1.4;                       // took several tries on LeetCode
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
  renderTable();
}

function whyBits(p) {
  const bits = [];
  const d = daysAgo(touched(p));
  if (d !== Infinity) bits.push([d === 0 ? "touched today" : `untouched for ${plural(d, "day")}`, d > 30]);
  bits.push([p.timesRevised ? `revised ${p.timesRevised}×` : "never revised", false]);
  if (p.confidence === 1) bits.push(["rated Again last time", true]);
  if (p.confidence === 2) bits.push(["found it Hard last time", true]);
  const a = attemptsOf(p.slug);
  if (a && a.failed > 0) bits.push([`took ${a.submissions} tries on LeetCode`, a.failed >= 2]);
  else if (a && a.firstTry) bits.push(["solved first try", false]);
  if (isDue(p)) bits.push(["due for revision", true]);
  return bits;
}

// ================================================================== rendering: shell

function render() {
  const needUser = !data.username;
  $("onboard").hidden = !needUser;
  document.body.classList.toggle("no-user", needUser);
  document.querySelectorAll(".view").forEach((v) => (v.hidden = needUser || v.dataset.view !== view));
  document.querySelectorAll(".tab").forEach((t) => {
    const on = !needUser && t.dataset.view === view;
    t.classList.toggle("on", on);
    t.setAttribute("aria-selected", on);
  });
  renderTopbar();
  renderBanner();
  renderFooter();
  if (needUser) return;
  renderStats();
  renderTagOptions();
  renderTable();
  renderPick();
  renderLog();
  renderInsights();
}

function setView(v) {
  if (view === v) return;
  view = v;
  if (location.hash !== "#" + v) history.replaceState(null, "", "#" + v);
  render();
  window.scrollTo({ top: 0 });
}

function renderTopbar() {
  const chip = $("user-chip");
  $("user-name").textContent = data.username ? "@" + data.username : "No account yet";
  $("user-sub").textContent = !data.username ? "add your username"
    : (data.authed ? "full sync" : "public data") + " · " + (data.lastSync ? "synced " + data.lastSync.slice(5) : "never synced");
  $("sync-dot").className = "status-dot " + (!data.username ? "" : data.authed ? "ok" : "partial");
  chip.title = data.authed ? "Session cookie set – full sync" : "No session cookie – only the ~20 latest solves";
  $("btn-sync").hidden = !data.username;
}

function renderBanner() {
  const b = $("banner");
  const total = (data.leetCodeSolved || {}).All || 0;
  let html = "";
  if (data.username && !data.problems.length) {
    html = `<div><b>You're all set, @${esc(data.username)}.</b> Click <b>Sync</b> to pull your solved problems into the sheet.</div>`;
  } else if (data.username && !data.authed && total > data.problems.length) {
    html = `<div><b>${data.problems.length} of ${total}</b> solved problems loaded. LeetCode only shares your ~20 latest solves publicly –
      add <code>LEETCODE_SESSION</code> to <code>.env</code> (see README) and sync again to load all of them and unlock mistake insights.</div>`;
  } else if (data.authed && data.problems.length && !data.submissions) {
    html = `<div><b>One more sync</b> pulls your full submission history – wrong answers, TLEs, retries – which powers <a href="#insights">Insights</a>. The first one takes about a minute.</div>`;
  }
  b.hidden = !html;
  b.innerHTML = html;
}

function renderFooter() {
  const file = data.excelDisplay || data.excelPath || "";
  const backups = file.replace(/[^/\\]+$/, "") + "backups/";
  $("excel-hint").innerHTML = `Saved in <button class="path-btn" data-open-excel data-tip="${esc(data.excelPath)}\nClick to open in Excel">${esc(file)}</button>
    · daily backups in <code>${esc(backups)}</code> · edit it in Excel any time, the app picks up changes`;
}

// ================================================================== rendering: revise

// ------------------------------------------------------------------ small icons

const ICONS = {
  check: '<circle cx="12" cy="12" r="8.5"/><path d="M8.5 12.5l2.4 2.4 4.6-5"/>',
  clock: '<circle cx="12" cy="12" r="8.5"/><path d="M12 7.5V12l3 2"/>',
  flame: '<path d="M12 21a6.5 6.5 0 0 0 6.5-6.5c0-3.3-2.2-5.4-3.6-7.2-.4 1.6-1.3 2.7-2.6 3.1.4-2.9-.8-5.5-3.3-7.4.1 3.4-1.8 5.4-3.2 7.3A7.4 7.4 0 0 0 5.5 14.5 6.5 6.5 0 0 0 12 21z"/>',
  target: '<circle cx="12" cy="12" r="8.5"/><circle cx="12" cy="12" r="4.5"/><circle cx="12" cy="12" r=".6"/>',
  repeat: '<path d="M4 12a8 8 0 0 1 13.7-5.7M20 12a8 8 0 0 1-13.7 5.7M17.7 3v3.3h-3.3M6.3 21v-3.3h3.3"/>',
  x: '<circle cx="12" cy="12" r="8.5"/><path d="M9.3 9.3l5.4 5.4M14.7 9.3l-5.4 5.4"/>',
  recall: '<path d="M4 12a8 8 0 1 0 2.3-5.7"/><path d="M4 4.5V8h3.5"/><path d="M12 8v4l2.5 1.5"/>',
  calendar: '<rect x="4" y="5.5" width="16" height="14.5" rx="3"/><path d="M4 10h16M8.5 3.5v4M15.5 3.5v4"/>',
};
const ico = (k) => `<span class="tile-ico" aria-hidden="true"><svg class="ico" viewBox="0 0 24 24">${ICONS[k]}</svg></span>`;
/** Difficulty glyph: 1, 2 or 3 of three bars lit. */
const levelIco = (n) => `<span class="tile-ico" aria-hidden="true"><svg class="glyph" viewBox="0 0 16 16">${[[1.5, 9, 5.5], [6.5, 5.5, 9], [11.5, 2, 12.5]]
  .map(([x, y, h], i) => `<rect x="${x}" y="${y}" width="3" height="${h}" rx="1.2" class="${i < n ? "on" : ""}"/>`).join("")}</svg></span>`;

// ------------------------------------------------------------------ stat tiles

function renderStats() {
  const ps = data.problems;
  const count = (d) => ps.filter((p) => p.difficulty === d).length;
  const totals = data.leetCodeTotal || {};
  const only = f.diffs.length === 1 ? f.diffs[0] : null;

  // Solved: Easy/Medium/Hard split of what you solved; on hover, progress against all of LeetCode
  const counts = LEVELS.map(count);
  const allTotal = totals.All;
  const allPct = allTotal ? (ps.length / allTotal) * 100 : null;
  const solvedTile = `
    <button class="stat tile" data-act="all" style="--c:var(--teal-500)"
            aria-label="Solved: ${ps.length}${allTotal ? `, ${allPct.toFixed(1)}% of ${allTotal} on LeetCode` : ""}. Click to clear filters.">
      <div class="label">${ico("check")}Solved</div>
      <div class="tile-body">
        ${splitRing(counts, allPct, fmt(ps.length))}
        <div class="facts">
          <div class="legend-rows own-only">${LEVELS.map((d, i) => `<span><i class="dot ${d.toLowerCase()}"></i>${fmt(counts[i])}</span>`).join("")}</div>
          ${allTotal ? `<div class="lc-only"><div class="fact-v">${pctLabel(allPct)}</div><div class="fact-k">of ${fmt(allTotal)} total</div></div>` : ""}
        </div>
      </div>
    </button>`;

  // Easy / Medium / Hard: share of *your* solved; on hover, progress against LeetCode's total for that level
  const diffTile = (d, i) => {
    const n = counts[i], t = totals[d], key = d.toLowerCase();
    const own = ps.length ? (n / ps.length) * 100 : 0;
    const lc = t ? (n / t) * 100 : null;
    return `
      <button class="stat tile${only === d ? " on" : ""}" data-act="${d}" style="--c:var(--${key})"
              aria-label="${d}: ${n} solved, ${Math.round(own)}% of your solved${t ? `, ${lc.toFixed(1)}% of ${t} on LeetCode` : ""}. Click to show only ${d}.">
        <div class="label">${levelIco(i + 1)}${d}</div>
        <div class="tile-body">
          ${ring(own, lc ?? own, key, fmt(n), t ? `/ ${fmt(t)}` : "")}
          <div class="facts">
            <div class="own-only"><div class="fact-v">${Math.round(own)}%</div><div class="fact-k">of yours</div></div>
            ${t ? `<div class="lc-only"><div class="fact-v">${pctLabel(lc)}</div><div class="fact-k">of ${fmt(t)} total</div></div>` : ""}
          </div>
        </div>
      </button>`;
  };

  $("stats").innerHTML = solvedTile + LEVELS.map(diffTile).join("") + dueTile(ps) + streakTile();
}

const pctLabel = (x) => (x > 0 && x < 10 ? x.toFixed(1) : Math.round(x)) + "%";

/** Progress ring (gradient `key`) with `center` text: fills to `own`% and, while its tile is hovered, to `lc`% (both 0–100). */
function ring(own, lc, key, center, caption) {
  return `
    <span class="ring-wrap" style="--own:${own.toFixed(2)};--lc:${lc.toFixed(2)};--c:var(--${key})" aria-hidden="true">
      <svg class="ring" viewBox="0 0 44 44"><circle class="track" cx="22" cy="22" r="18" pathLength="100"/><circle class="arc" cx="22" cy="22" r="18" pathLength="100" style="stroke:url(#g-${key})"/></svg>
      <span class="ring-center">${center}${caption ? `<small>${caption}</small>` : ""}</span>
    </span>`;
}

/** Easy / Medium / Hard split of what you solved; while hovered: overall progress against LeetCode (`lc`%, may be null). */
function splitRing(counts, lc, center) {
  const total = counts.reduce((a, b) => a + b, 0);
  const keys = ["easy", "medium", "hard"];
  const gap = counts.filter(Boolean).length > 1 ? 4.5 : 0;     // room for the rounded ends + a little air
  let start = 0;
  const parts = counts.map((n, i) => {
    const len = total ? (n / total) * 100 : 0;
    const seg = len > gap + .5 ? `<circle class="part" cx="22" cy="22" r="18" pathLength="100" style="stroke:url(#g-${keys[i]});stroke-dasharray:${(len - gap).toFixed(2)} 100;stroke-dashoffset:${(-start - gap / 2).toFixed(2)}"/>` : "";
    start += len;
    return seg;
  }).join("");
  return `
    <span class="ring-wrap split${lc == null ? " no-lc" : ""}" style="--own:0;--lc:${(lc || 0).toFixed(2)};--c:var(--teal-500)" aria-hidden="true">
      <svg class="ring" viewBox="0 0 44 44"><circle class="track" cx="22" cy="22" r="18" pathLength="100"/><g class="parts">${parts}</g><circle class="arc" cx="22" cy="22" r="18" pathLength="100" style="stroke:url(#g-teal)"/></svg>
      <span class="ring-center">${center}<small>solved</small></span>
    </span>`;
}

/** Due now: how overdue the due problems are, as four slim columns (darker = longer overdue). */
function dueTile(ps) {
  const due = ps.filter(isDue);
  const buckets = [["<1d", 1, "by up to a day"], ["<1w", 7, "by up to a week"], ["<1m", 30, "by up to a month"], ["1m+", Infinity, "by over a month"]]
    .map(([label, max, text]) => ({ label, max, text, n: 0 }));
  for (const p of due) {
    const since = p.nextReview || p.lastSolved + 7 * DAY;           // when it became due
    const days = Math.max(0, (now() - since) / DAY);
    buckets.find((b) => days <= b.max).n++;
  }
  const max = Math.max(1, ...buckets.map((b) => b.n));
  return `
    <button class="stat tile${f.due ? " on" : ""}" data-act="due" style="--c:var(--medium)" aria-label="Due now: ${due.length} of ${ps.length}. Click to show what's due.">
      <div class="label">${ico("clock")}Due now</div>
      <div class="tile-body">
        <div class="minibars cols4">
          <div class="plot">${buckets.map((b, i) => `<i class="u${i}${b.n ? "" : " zero"}" style="height:${b.n ? Math.max(6, (b.n / max) * 100) : 0}%" data-tip="${b.n} due, overdue ${b.text}"></i>`).join("")}</div>
          <div class="axis">${buckets.map((b) => `<span>${b.label}</span>`).join("")}</div>
        </div>
        <div class="facts">
          <div class="fact-v">${fmt(due.length)}</div>
          <div class="fact-k">${due.length ? `of ${fmt(ps.length)}` : "all caught up"}</div>
        </div>
      </div>
    </button>`;
}

/** Streak: revisions per day over the last 14 days, today on the right. */
function streakTile() {
  const days = 14;
  const perDay = new Map();
  data.log.forEach((l) => {
    const k = new Date(l.date * 1000).toDateString();
    perDay.set(k, (perDay.get(k) || 0) + 1);
  });
  const cols = [];
  for (let i = days - 1; i >= 0; i--) {
    const d = new Date();
    d.setDate(d.getDate() - i);
    cols.push({ d, n: perDay.get(d.toDateString()) || 0 });
  }
  const max = Math.max(1, ...cols.map((c) => c.n));
  const week = cols.slice(-7).reduce((a, c) => a + c.n, 0);
  const s = streak();
  const day = (d) => d.toLocaleDateString(undefined, { day: "numeric", month: "short" });
  return `
    <div class="stat tile static" style="--c:var(--teal-500)">
      <div class="label">${ico("flame")}Streak</div>
      <div class="tile-body">
        <div class="minibars">
          <div class="plot">${cols.map((c, i) => `<i class="${i === days - 1 ? "today" : ""}${c.n ? "" : " zero"}" style="height:${c.n ? Math.max(8, (c.n / max) * 100) : 0}%" data-tip="${day(c.d)}${i === days - 1 ? " (today)" : ""}\n${plural(c.n, "revision")}"></i>`).join("")}</div>
          <div class="axis"><span>${day(cols[0].d)}</span><span>today</span></div>
        </div>
        <div class="facts">
          <div class="fact-v">${s}<small>${s === 1 ? "day" : "days"}</small></div>
          <div class="fact-k">${week} this week</div>
        </div>
      </div>
    </div>`;
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
  if (f.tag && !tags.includes(f.tag)) f.tag = "";
  $("tag").innerHTML = `<option value="">All topics</option>` + tags.map((t) => `<option ${t === f.tag ? "selected" : ""}>${esc(t)}</option>`).join("");
}

function renderTable() {
  const list = sorted(filtered());
  $("count").textContent = `${list.length} of ${data.problems.length}`;
  document.querySelectorAll("th[data-sort]").forEach((th) => {
    th.querySelector(".arrow")?.remove();
    if (th.dataset.sort === sort.key) th.insertAdjacentHTML("beforeend", `<span class="arrow">${sort.dir > 0 ? "▲" : "▼"}</span>`);
  });

  if (!list.length) {
    $("rows").innerHTML = `<tr class="empty-row"><td colspan="9">${data.problems.length ? "No problems match these filters." : "Nothing here yet – click Sync."}</td></tr>`;
    return;
  }

  $("rows").innerHTML = list.map((p) => {
    const due = isDue(p);
    const next = p.nextReview
      ? (due ? `<span class="due-badge">Due</span><small>${rel(p.nextReview)}</small>` : `${rel(p.nextReview)}<small>${dateStr(p.nextReview)}</small>`)
      : (due ? `<span class="due-badge">Due</span><small>never revised</small>` : `<span class="dash">—</span>`);
    const tags = tagsOf(p);
    const shown = fitTags(tags);
    const a = attemptsOf(p.slug);
    const tries = !a ? '<span class="dash">—</span>'
      : `${a.submissions}<small class="${a.failed ? "bad" : "ok"}">${a.failed ? a.failed + " failed" : "first try"}</small>`;
    return `
      <tr class="${due ? "is-due" : ""}${p.slug === pick ? " picked" : ""}">
        <td class="num r">${esc(p.id)}</td>
        <td class="title-cell">
          <a href="${esc(p.url)}" target="_blank" rel="noopener">${esc(p.title || p.slug)}</a>
          ${p.notes ? `<div class="note-preview" data-tip="${esc(p.notes)}">${esc(p.notes)}</div>` : ""}
        </td>
        <td>${diffTag(p.difficulty)}</td>
        <td class="col-tags"><div class="tags">${shown.map((t) => `<button class="tag" data-tag="${esc(t)}">${esc(t)}</button>`).join("")}${tags.length > shown.length ? `<span class="tag more" data-tip="${esc(tags.slice(shown.length).join("\n"))}">+${tags.length - shown.length}</span>` : ""}</div></td>
        <td class="col-tries when r">${tries}</td>
        <td class="when">${rel(p.lastSolved)}<small>${dateStr(p.lastSolved)}</small></td>
        <td class="when">${next}</td>
        <td class="col-conf when">${recallMeter(p.confidence)}<small>${p.timesRevised ? `revised ${p.timesRevised}×` : "not revised"}</small></td>
        <td><div class="row-actions">
          <button class="icon-btn${p.notes ? " has-note" : ""}" data-note="${esc(p.slug)}" data-tip="${p.notes ? "Edit notes" : "Add notes"}" aria-label="Notes">
            <svg class="ico" viewBox="0 0 24 24" aria-hidden="true"><path d="M5 19h4L19.5 8.5a2.1 2.1 0 0 0-3-3L6 16v3z"/><path d="M14.5 7.5l2 2"/></svg>
          </button>
          <button class="btn sm" data-revise="${esc(p.slug)}">Revise</button>
        </div></td>
      </tr>`;
  }).join("");
}

/** As many whole topic tags as fit on one line of the Topics column (the first one always shows). */
function fitTags(tags) {
  const BUDGET = 24;                    // roughly the column width, in characters
  const cost = (t) => t.length + 4;     // pill padding + gap
  const out = [];
  let used = 0;
  for (const t of tags) {
    const reserve = out.length + 1 < tags.length ? 5 : 0;   // room for "+N"
    if (out.length && used + cost(t) + reserve > BUDGET) break;
    out.push(t);
    used += cost(t);
  }
  return out;
}

function renderPick() {
  const p = data.problems.find((x) => x.slug === pick);
  const el = $("pick");
  if (!p) {
    el.innerHTML = `
      <div class="pick-main">
        <div class="eyebrow">Practice</div>
        <h2>Pick a problem to revise</h2>
        <p class="empty">Chosen at random from what the table shows. Problems you haven't seen for a while, found hard, took many tries, or that are due come up more often.</p>
      </div>
      <div class="pick-actions">
        <button class="btn primary lg pick-lg" id="pick-btn">Pick random <kbd>R</kbd></button>
      </div>`;
    return;
  }
  el.innerHTML = `
    <div class="pick-main">
      <div class="eyebrow">Revise this one</div>
      <h2><a href="${esc(p.url)}" target="_blank" rel="noopener">${p.id ? `<span class="pid">${esc(p.id)}.</span>` : ""}${esc(p.title || p.slug)}<span class="ext"> ↗</span></a></h2>
      <div class="meta">
        ${diffTag(p.difficulty)}
        ${tagsOf(p).map((t) => `<button class="tag" data-tag="${esc(t)}">${esc(t)}</button>`).join("")}
      </div>
      <div class="why">${whyBits(p).map(([t, hot]) => `<span class="${hot ? "hot" : ""}">${esc(t)}</span>`).join("")}</div>
      ${p.notes ? `<div class="pick-note">${esc(p.notes)}</div>` : ""}
    </div>
    <div class="pick-actions">
      <div class="pick-top">
        <a class="btn primary" href="${esc(p.url)}" target="_blank" rel="noopener" id="pick-open">Open problem <kbd>O</kbd></a>
        <button class="btn" id="pick-btn">Another <kbd>R</kbd></button>
      </div>
      <div class="rate-label">after solving it again</div>
      <div class="rate">
        <button class="btn r-again" data-rate="again" title="Couldn't do it – show again tomorrow"><span><i class="dot hard"></i>Again</span><kbd>1</kbd></button>
        <button class="btn r-hard" data-rate="hard" title="Got there, with difficulty"><span><i class="dot medium"></i>Hard</span><kbd>2</kbd></button>
        <button class="btn r-good" data-rate="good" title="Solved it fine"><span><i class="dot accent"></i>Good</span><kbd>3</kbd></button>
        <button class="btn r-easy" data-rate="easy" title="Trivial now"><span><i class="dot easy"></i>Easy</span><kbd>4</kbd></button>
      </div>
    </div>`;
}

function renderLog() {
  const items = [...data.log].reverse();
  $("log").innerHTML = items.length
    ? items.map((l) => `
        <div class="log-item">
          <a href="${lcUrl(l.slug)}" target="_blank" rel="noopener">${esc(l.title || l.slug)}</a>
          <span class="log-meta"><span class="rating ${esc(l.rating)}">${esc(l.rating)}</span><span class="muted">${rel(l.date)}</span></span>
        </div>`).join("")
    : `<p class="empty-state">No revisions yet. Pick a problem, solve it again, and rate how it went.</p>`;
}

// ================================================================== rendering: insights

function renderInsights() {
  if (!ins) return;
  const o = ins.overview;
  const has = ins.hasSubmissions;
  $("insights-sub").textContent = has
    ? `Based on ${fmt(o.submissions)} submissions, ${fmt(o.solved)} solved problems and ${fmt(o.revisions)} revisions.`
    : `Based on ${fmt(o.solved)} solved problems and ${fmt(o.revisions)} revisions. Add LEETCODE_SESSION to .env for submission analysis.`;
  renderKpis(o, has);
  renderTips();
  renderTopicLists();
  renderTopicTable();
  renderMistakes(o, has);
  renderDifficulties(has);
  renderHours(has);
  renderHeatmap();
  renderStruggles(has);
  renderRetries(has);
}

function renderKpis(o, has) {
  const top = ins.mistakes[0];
  const need = "needs submission history";
  const tiles = [
    ["check", "var(--teal-500)", "Acceptance rate", pct(o.acceptanceRate), has ? `${fmt(o.accepted)} of ${fmt(o.submissions)} submissions` : need],
    ["target", "var(--teal-500)", "Solved first try", pct(o.firstTryRate), has ? "accepted on attempt #1" : need],
    ["repeat", "var(--teal-500)", "Tries per problem", o.avgAttempts == null ? "—" : o.avgAttempts.toFixed(2), has ? "submissions until accepted" : need],
    ["x", "var(--hard)", "Failed submissions", has ? fmt(o.failed) : "—", has ? (top ? `mostly ${top.name}` : "none – nice") : need],
    ["recall", "var(--easy)", "Revision recall", pct(o.recallRate), o.revisions ? `Good/Easy, of ${plural(o.revisions, "revision")}` : "no revisions yet"],
    ["calendar", "var(--medium)", "Active days", `${o.activeDays30}<small>/ 30</small>`, "days with practice"],
  ];
  $("kpis").innerHTML = tiles.map(([icon, c, l, v, s]) => `
    <div class="stat static" style="--c:${c}">
      <div class="label">${ico(icon)}${l}</div>
      <div class="value">${v}</div>
      <div class="sub">${esc(s)}</div>
    </div>`).join("");
}

function renderTips() {
  const icons = { focus: "◎", warn: "!", good: "✓", info: "i" };
  $("tips").innerHTML = ins.tips.length
    ? ins.tips.map((t) => `
        <li class="tip">
          <span class="tip-ico ${esc(t.kind)}" aria-hidden="true">${icons[t.kind] || "·"}</span>
          <div>
            <div class="tip-title">${esc(t.title)}</div>
            <div class="tip-detail">${esc(t.detail)}</div>
          </div>
          ${t.tag ? `<button class="btn sm" data-goto-tag="${esc(t.tag)}">Revise →</button>` : "<span></span>"}
        </li>`).join("")
    : `<li class="empty-state">Solve and revise a few problems – suggestions show up here.</li>`;
}

function topicByName(name) {
  return ins.topics.find((t) => t.name === name);
}

function renderTopicLists() {
  const row = (name) => {
    const t = topicByName(name);
    return `<li data-goto-tag="${esc(name)}" data-tip="${esc(t.reasons.join("\n"))}">
      <span class="name">${esc(name)}</span>
      <span class="bar ${t.level}"><i style="width:${t.score}%"></i></span>
      <span class="n">${t.score}</span>
    </li>`;
  };
  const empty = (msg) => `<li class="empty">${ins.topics.length ? msg : `No topic has ${ins.minProblems}+ solved problems yet`}</li>`;
  $("mastery-note").textContent = `Only topics with ${ins.minProblems}+ solved problems are rated, so a few lucky solves never make a topic “strong”. `
    + "The score mixes accuracy (first-try + acceptance), revision recall, how recently you practised the topic and its difficulty – each compared with your own average.";
  $("strongest").innerHTML = ins.strongest.length ? ins.strongest.map(row).join("") : empty("No topic stands out yet");
  $("weakest").innerHTML = ins.weakest.length ? ins.weakest.map(row).join("") : empty("Nothing weak right now – nice");
}

function renderTopicTable() {
  const order = { weak: 0, steady: 1, strong: 2 };
  let list = ins.topics.filter((t) => topicLevel === "all" || t.level === topicLevel);
  const { key, dir } = topicSort;
  const val = (t) => key === "name" ? t.name.toLowerCase() : key === "level" ? order[t.level] : (t[key] ?? -1);
  list = [...list].sort((a, b) => {
    // topics too small to judge go last when ranking by score or level
    const x = val(a), y = val(b);
    return ((x < y ? -1 : x > y ? 1 : 0) * dir) || b.solved - a.solved;
  });
  $("mastery-sub").textContent = `topics with ${ins.minProblems}+ solved · click one to revise its problems`;
  document.querySelectorAll("th[data-tsort]").forEach((th) => {
    th.querySelector(".arrow")?.remove();
    if (th.dataset.tsort === key) th.insertAdjacentHTML("beforeend", `<span class="arrow">${dir > 0 ? "▲" : "▼"}</span>`);
  });
  document.querySelectorAll("#topic-filter .seg-btn").forEach((b) => b.classList.toggle("on", b.dataset.lvl === topicLevel));

  if (!list.length) {
    $("topic-rows").innerHTML = `<tr class="empty-row"><td colspan="9">${!ins.topics.length ? `Topics show up once one has ${ins.minProblems}+ solved problems.`
      : "No topics at this level."}</td></tr>`;
    return;
  }
  const label = { strong: "▲ Strong", steady: "● Steady", weak: "▼ Weak" };
  $("topic-rows").innerHTML = list.map((t) => {
    const errs = [["WA", t.wrongAnswer, "Wrong Answer"], ["TLE", t.timeLimit, "Time Limit Exceeded"], ["RE", t.runtimeError, "Runtime Error"], ["CE", t.compileError, "Compile Error"]]
      .filter((e) => e[1] > 0);
    const seg = (n, cls) => (n ? `<i class="${cls}" style="flex:${n}"></i>` : "");
    return `
      <tr data-goto-tag="${esc(t.name)}">
        <td><span class="topic-name" data-tip="${esc(t.reasons.join("\n") || t.name)}">${esc(t.name)}</span></td>
        <td><span class="lvl ${t.level}">${label[t.level]}</span></td>
        <td><div class="score"><span class="bar ${t.level}"><i style="width:${t.score}%"></i></span><span class="n">${t.score}</span></div></td>
        <td class="r"><div class="solved-cell"><span>${t.solved}</span>
          <span class="stack" data-tip="${t.easy} Easy\n${t.medium} Medium\n${t.hard} Hard">${seg(t.easy, "e")}${seg(t.medium, "m")}${seg(t.hard, "h")}</span></div></td>
        <td class="r num">${pct(t.firstTryRate)}</td>
        <td class="r num">${pct(t.acceptanceRate)}</td>
        <td>${errs.length ? `<div class="errs">${errs.map((e) => `<span data-tip="${e[2]}"><b>${e[1]}</b> ${e[0]}</span>`).join("")}</div>` : '<span class="dash">—</span>'}</td>
        <td class="r num">${t.recallRate == null ? '<span class="dash">—</span>' : pct(t.recallRate)}</td>
        <td class="r num">${t.daysSinceTouched == null ? "—" : t.daysSinceTouched + "d ago"}</td>
      </tr>`;
  }).join("");
}

function renderMistakes(o, has) {
  $("mistake-sub").textContent = has ? `${fmt(o.failed)} failed submissions` : "";
  if (!has) return void ($("mistakes").innerHTML = lockedNote("wrong answers, TLEs and runtime errors"));
  if (!ins.mistakes.length) return void ($("mistakes").innerHTML = `<p class="empty-state">No failed submissions. Impressive.</p>`);
  const max = ins.mistakes[0].count;
  $("mistakes").innerHTML = ins.mistakes.map((m) => `
    <div class="hbar" data-tip="${esc(m.name)}: ${m.count} (${pct(m.count / o.failed)} of failures)">
      <span class="name">${esc(m.name)}</span>
      <span class="v">${fmt(m.count)}<small>${pct(m.count / o.failed)}</small></span>
      <span class="track"><i style="width:${(m.count / max) * 100}%"></i></span>
    </div>`).join("");
}

function renderDifficulties(has) {
  $("difficulties").innerHTML = ins.difficulties.map((d) => `
    <div class="drow">
      <div class="drow-top">
        <span class="diff"><i class="dot ${d.name.toLowerCase()}"></i>${d.name}</span>
        <span class="v">${fmt(d.solved)}${d.total ? `<small> / ${fmt(d.total)}</small>` : ""}</span>
      </div>
      <div class="progress"><i style="width:${d.total ? Math.min(100, (d.solved / d.total) * 100) : 0}%;background:var(--${d.name.toLowerCase()})"></i></div>
      <div class="drow-meta">
        ${has ? `<span>1st try <b>${pct(d.firstTryRate)}</b></span><span>accept <b>${pct(d.acceptanceRate)}</b></span><span>tries <b>${d.avgAttempts == null ? "—" : d.avgAttempts.toFixed(1)}</b></span>`
              : `<span>${d.total ? ((d.solved / d.total) * 100).toFixed(1) + "% of LeetCode" : "sync to load LeetCode totals"}</span>`}
      </div>
    </div>`).join("");
}

function renderHours(has) {
  $("hours").classList.toggle("locked", !has);
  if (!has) return void ($("hours").innerHTML = lockedNote("when you submit best"));
  const hours = ins.hours;
  const enough = hours.filter((h) => h.submissions >= 10);
  const best = enough.length ? enough.reduce((a, b) => (b.acceptanceRate > a.acceptanceRate ? b : a)) : null;
  $("hours").innerHTML = hours.map((h) => {
    const r = h.acceptanceRate;
    const cls = !h.submissions ? "thin" : h.submissions < 10 ? "thin" : best && h.label === best.label ? "best" : "";
    return `
      <div class="col ${cls}" data-tip="${h.label}\n${pct(r)} accepted\n${h.accepted} of ${h.submissions} submissions${h.submissions && h.submissions < 10 ? "\n(few submissions)" : ""}">
        <span class="v">${h.submissions ? pct(r) : "—"}</span>
        <span class="plot"><i style="height:${r == null ? 0 : Math.max(2, r * 100)}%"></i></span>
        <span class="lbl">${h.label}</span>
        <span class="cnt">${h.submissions}</span>
      </div>`;
  }).join("");
}

function renderHeatmap() {
  const days = ins.activity;
  if (!days.length) return;
  const today = new Date(days[days.length - 1].date + "T00:00");
  // pad the last (current) week with future cells so every column has 7 days
  const pad = (7 - ((today.getDay() + 6) % 7) - 1);
  const cells = days.map((d) => d).concat(Array.from({ length: pad }, () => null));
  const weeks = Math.ceil(cells.length / 7);
  const lvl = (n) => (n <= 0 ? "" : n === 1 ? "l1" : n <= 3 ? "l2" : n <= 6 ? "l3" : "l4");
  const fmtDay = (s) => new Date(s + "T00:00").toLocaleDateString(undefined, { weekday: "short", day: "numeric", month: "short" });

  let months = "", prevMonth = -1;
  for (let w = 0; w < weeks; w++) {
    const d = cells[w * 7];
    const m = d ? new Date(d.date + "T00:00").getMonth() : prevMonth;
    months += `<span>${m !== prevMonth && w < weeks - 1 ? new Date(2000, m, 1).toLocaleDateString(undefined, { month: "short" }) : ""}</span>`;
    prevMonth = m;
  }
  const grid = cells.map((d) => {
    if (!d) return `<span class="hm-cell future"></span>`;
    const parts = [];
    if (ins.hasSubmissions) parts.push(`${plural(d.submissions, "submission")}${d.submissions ? ` (${d.accepted} accepted)` : ""}`);
    else parts.push(plural(d.firstSolves, "new solve"));
    if (d.revisions) parts.push(plural(d.revisions, "revision"));
    return `<span class="hm-cell ${lvl(d.total)}" data-tip="${fmtDay(d.date)}\n${parts.join("\n")}"></span>`;
  }).join("");

  const active = days.filter((d) => d.total > 0).length;
  let best = 0, run = 0;
  days.forEach((d) => { run = d.total > 0 ? run + 1 : 0; best = Math.max(best, run); });
  $("activity-sub").textContent = `${active} active days in the last year · longest run ${plural(best, "day")}`;
  $("heatmap").innerHTML = `
    <div class="heatmap">
      <div class="hm-days"><span></span><span>Mon</span><span></span><span>Wed</span><span></span><span>Fri</span><span></span><span>Sun</span></div>
      <div class="hm-body">
        <div class="hm-months">${months}</div>
        <div class="hm-grid">${grid}</div>
      </div>
    </div>
    <div class="hm-legend"><span>${ins.hasSubmissions ? "submissions + revisions" : "new solves + revisions"} per day</span>
      <span>less</span><i class="hm-cell"></i><i class="hm-cell l1"></i><i class="hm-cell l2"></i><i class="hm-cell l3"></i><i class="hm-cell l4"></i><span>more</span></div>`;
}

function renderStruggles(has) {
  if (!has) {
    $("struggles").innerHTML = `<li class="empty">${lockedNote("problems that took many tries")}</li>`;
    $("unsolved").innerHTML = `<li class="empty">${lockedNote("problems you tried but never solved")}</li>`;
    return;
  }
  $("struggles").innerHTML = ins.struggles.length
    ? ins.struggles.map((s) => `
        <li>
          <a href="${lcUrl(s.slug)}" target="_blank" rel="noopener">${esc(s.title || s.slug)}</a>
          <span class="count-pill" data-tip="${s.attempts} submissions, ${s.failed} failed">${s.failed} failed</span>
          <span class="meta">${diffTag(s.difficulty)}<span>mostly ${esc(s.mainError)}</span></span>
        </li>`).join("")
    : `<li class="empty">Nothing took more than one failed try.</li>`;
  $("unsolved").innerHTML = ins.unsolved.length
    ? ins.unsolved.map((u) => `
        <li>
          <a href="${lcUrl(u.slug)}" target="_blank" rel="noopener">${esc(u.title)}</a>
          <span class="count-pill">${plural(u.attempts, "try")}</span>
          <span class="meta"><span>last ${rel(u.lastTried)}</span><span>${esc(u.mainError)}</span></span>
        </li>`).join("")
    : `<li class="empty">Every problem you tried is solved.</li>`;
}

function renderRetries(has) {
  if (!has) {
    $("retries").innerHTML = lockedNote("how retries go");
    $("languages").innerHTML = "";
    $("languages-head").hidden = true;
    return;
  }
  $("languages-head").hidden = !ins.languages.length;
  const r = ins.retries;
  $("retries").innerHTML = `
    <div class="retry">
      <div><div class="k">Within 2 min</div><div class="v">${pct(r.quickRate)}</div><div class="s">passed · ${plural(r.quick, "retry")}</div></div>
      <div><div class="k">After a pause</div><div class="v">${pct(r.patientRate)}</div><div class="s">passed · ${plural(r.patient, "retry")}</div></div>
    </div>
    <p class="retry-note">${r.quick + r.patient === 0 ? "No retries yet."
      : r.quickRate != null && r.patientRate != null && r.patientRate - r.quickRate >= 0.1 ? "Pausing to debug pays off – your rushed resubmits fail more often."
      : "A quick resubmit is fine for typos – for logic bugs, trace the failing case first."}</p>`;
  const max = ins.languages.length ? ins.languages[0].submissions : 1;
  $("languages").innerHTML = ins.languages.slice(0, 5).map((l) => `
    <div class="hbar" data-tip="${esc(l.name)}: ${l.submissions} submissions, ${pct(l.acceptanceRate)} accepted">
      <span class="name">${esc(l.name)}</span>
      <span class="v">${fmt(l.submissions)}<small>${pct(l.acceptanceRate)} ok</small></span>
      <span class="track"><i style="width:${(l.submissions / max) * 100}%"></i></span>
    </div>`).join("");
}

function lockedNote(what) {
  return `<p class="empty-state">Shows ${what} once your submission history is synced – needs <code>LEETCODE_SESSION</code> in <code>.env</code>.</p>`;
}

// ================================================================== filter UI

function syncFilterUI() {
  $("q").value = f.q;
  $("range").value = f.range;
  document.querySelectorAll("#diff-chips .seg-btn").forEach((c) => c.classList.toggle("on", f.diffs.includes(c.dataset.d)));
  ["due", "never", "weak", "struggled"].forEach((k) => $("f-" + k).classList.toggle("on", f[k]));
}

function changed() {
  saveFilters();
  syncFilterUI();
  renderStats();
  renderTagOptions();
  renderTable();
}

function resetFilters() {
  Object.assign(f, { q: "", diffs: [...LEVELS], range: "any", tag: "", due: false, never: false, weak: false, struggled: false });
  changed();
}

function gotoTag(tag) {
  resetFilters();
  f.tag = tag;
  changed();
  setView("revise");
  document.querySelector(".table-card").scrollIntoView({ behavior: "smooth", block: "start" });
  toast(`Showing ${tag} – press R to pick one`);
}

// ================================================================== actions

async function rate(rating) {
  if (!pick) return;
  const title = (data.problems.find((p) => p.slug === pick) || {}).title;
  try {
    await load("revise", { slug: pick, rating });
    toast(`Saved “${title}” as ${rating}`);
    pickRandom();
  } catch (e) {
    toast(e.message, true);
  }
}

async function sync(full = false) {
  const btn = $("btn-sync");
  if (btn.disabled) return;
  btn.disabled = true;
  btn.classList.add("spinning");
  $("sync-label").textContent = "Syncing…";
  if (data.authed && (!data.submissions || full)) toast("Walking your full submission history – about a minute");
  try {
    await load("sync", { full });
  } catch (e) {
    toast(e.message, true);
  } finally {
    btn.disabled = false;
    btn.classList.remove("spinning");
    $("sync-label").textContent = "Sync";
  }
}

function openModal(id) {
  $(id).classList.add("open");
  const first = $(id).querySelector("textarea, input");
  if (first) setTimeout(() => first.focus(), 0);
}

function closeModals() {
  document.querySelectorAll(".modal-bg.open").forEach((m) => m.classList.remove("open"));
  noteSlug = null;
}

function openNote(slug) {
  const p = data.problems.find((x) => x.slug === slug);
  if (!p) return;
  noteSlug = slug;
  $("note-title").textContent = p.title || p.slug;
  $("note-text").value = p.notes || "";
  openModal("modal-note");
}

function openUser() {
  const src = { env: "from .env (LEETCODE_USERNAME)", session: "detected from your session cookie", sheet: "entered in the app", "": "not set" }[data.usernameSource || ""];
  $("user-info").innerHTML = `
    <dt>Username</dt><dd>${data.username ? `<a href="https://leetcode.com/u/${encodeURIComponent(data.username)}/" target="_blank" rel="noopener">@${esc(data.username)}</a>` : "—"}<br><span class="muted">${src}</span></dd>
    <dt>Full sync</dt><dd>${data.authed ? "on – session cookie set" : "off – add <code>LEETCODE_SESSION</code> to <code>.env</code>"}</dd>
    <dt>Submissions</dt><dd>${fmt(data.submissions || 0)} stored</dd>
    <dt>Last sync</dt><dd>${esc(data.lastSync || "never")}</dd>
    <dt>Excel file</dt><dd><code>${esc(data.excelPath)}</code></dd>`;
  const editable = !data.usernameSource || data.usernameSource === "sheet";
  $("user-form").hidden = !editable;
  $("user-input").value = data.username || "";
  openModal("modal-user");
}

function exportCsv() {
  const cols = ["id", "title", "url", "difficulty", "tags", "lastSolved", "timesRevised", "nextReview", "confidence", "submissions", "failed", "notes"];
  const cell = (v) => `"${String(v ?? "").replace(/"/g, '""')}"`;
  const rows = sorted(filtered()).map((p) => {
    const a = attemptsOf(p.slug) || {};
    return cols.map((c) => cell(
      c === "lastSolved" || c === "nextReview" ? (p[c] ? new Date(p[c] * 1000).toISOString().slice(0, 10) : "")
        : c === "submissions" || c === "failed" ? a[c] : p[c])).join(",");
  });
  const blob = new Blob([cols.join(",") + "\n" + rows.join("\n")], { type: "text/csv" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `leetvise-${new Date().toISOString().slice(0, 10)}.csv`;
  a.click();
  URL.revokeObjectURL(a.href);
}

// ================================================================== tooltip

const tip = $("tooltip");
document.addEventListener("mouseover", (e) => {
  const t = e.target.closest("[data-tip]");
  if (!t || !t.dataset.tip) return tip.classList.remove("show");
  tip.textContent = t.dataset.tip;
  tip.classList.add("show");
});
document.addEventListener("mousemove", (e) => {
  if (!tip.classList.contains("show")) return;
  const pad = 14, w = tip.offsetWidth, h = tip.offsetHeight;
  let x = e.clientX + pad, y = e.clientY + pad;
  if (x + w > innerWidth - 8) x = e.clientX - w - pad;
  if (y + h > innerHeight - 8) y = e.clientY - h - pad;
  tip.style.left = Math.max(8, x) + "px";
  tip.style.top = Math.max(8, y) + "px";
});
document.addEventListener("mouseout", (e) => {
  if (e.target.closest("[data-tip]") && !e.relatedTarget?.closest?.("[data-tip]")) tip.classList.remove("show");
});
window.addEventListener("scroll", () => tip.classList.remove("show"), { passive: true });

// ================================================================== events

window.addEventListener("hashchange", () => setView(viewFromHash()));

$("btn-sync").addEventListener("click", (e) => sync(e.shiftKey));
$("btn-csv").addEventListener("click", exportCsv);
$("btn-add").addEventListener("click", () => openModal("modal-add"));
$("btn-excel").addEventListener("click", () => api("open-excel", {}).then((r) => toast(r.message)).catch((e) => toast(e.message, true)));
$("user-chip").addEventListener("click", openUser);

$("q").addEventListener("input", (e) => { f.q = e.target.value; changed(); });
$("range").addEventListener("change", (e) => { f.range = e.target.value; changed(); });
$("tag").addEventListener("change", (e) => { f.tag = e.target.value; changed(); });
$("diff-chips").addEventListener("click", (e) => {
  const d = e.target.closest(".seg-btn")?.dataset.d;
  if (!d) return;
  f.diffs = f.diffs.includes(d) ? f.diffs.filter((x) => x !== d) : [...f.diffs, d];
  if (!f.diffs.length) f.diffs = [...LEVELS];
  changed();
});
["due", "never", "weak", "struggled"].forEach((k) => $("f-" + k).addEventListener("click", () => { f[k] = !f[k]; changed(); }));
$("reset").addEventListener("click", resetFilters);

$("stats").addEventListener("click", (e) => {
  const act = e.target.closest(".stat")?.dataset.act;
  if (!act) return;
  if (act === "all") resetFilters();
  else if (act === "due") { f.due = !f.due; changed(); }
  else { f.diffs = f.diffs.length === 1 && f.diffs[0] === act ? [...LEVELS] : [act]; changed(); }
});

document.querySelectorAll("th[data-sort]").forEach((th) => th.addEventListener("click", () => {
  const key = th.dataset.sort;
  sort = sort.key === key ? { key, dir: -sort.dir } : { key, dir: key === "title" || key === "id" || key === "nextReview" ? 1 : -1 };
  saveFilters();
  renderTable();
}));
document.querySelectorAll("th[data-tsort]").forEach((th) => th.addEventListener("click", () => {
  const key = th.dataset.tsort;
  topicSort = topicSort.key === key ? { key, dir: -topicSort.dir } : { key, dir: key === "name" || key === "daysSinceTouched" ? 1 : -1 };
  renderTopicTable();
}));
$("topic-filter").addEventListener("click", (e) => {
  const lvl = e.target.closest(".seg-btn")?.dataset.lvl;
  if (!lvl) return;
  topicLevel = lvl;
  renderTopicTable();
});

document.addEventListener("click", (e) => {
  if (e.target.closest("[data-close]")) return closeModals();
  if (e.target.closest("[data-open-excel]")) return void $("btn-excel").click();
  const t = e.target.closest("[data-tag],[data-note],[data-revise],[data-rate],[data-goto-tag],#pick-btn");
  if (!t) return;
  if (t.id === "pick-btn") pickRandom();
  else if (t.dataset.rate) rate(t.dataset.rate);
  else if (t.dataset.note) openNote(t.dataset.note);
  else if (t.dataset.revise) { pick = t.dataset.revise; renderPick(); renderTable(); window.scrollTo({ top: 0, behavior: "smooth" }); }
  else if (t.dataset.gotoTag) gotoTag(t.dataset.gotoTag);
  else if (t.dataset.tag) { f.tag = t.dataset.tag; changed(); }
});

document.querySelectorAll(".modal-bg").forEach((m) => m.addEventListener("mousedown", (e) => { if (e.target === m) closeModals(); }));

$("note-save").addEventListener("click", async () => {
  try {
    await load("note", { slug: noteSlug, notes: $("note-text").value });
    closeModals();
  } catch (e) {
    toast(e.message, true);
  }
});

$("add-btn").addEventListener("click", async () => {
  const v = $("add-input").value.trim();
  if (!v) return;
  try {
    await load("add", { slug: v });
    $("add-input").value = "";
    closeModals();
  } catch (e) {
    toast(e.message, true);
  }
});
$("add-input").addEventListener("keydown", (e) => { if (e.key === "Enter") $("add-btn").click(); });

async function submitUsername(input, button) {
  const v = input.value.trim();
  if (!v) return input.focus();
  button.disabled = true;
  try {
    await load("username", { username: v });
    closeModals();
    sync();
  } catch (e) {
    toast(e.message, true);
  } finally {
    button.disabled = false;
  }
}
$("onboard-form").addEventListener("submit", (e) => { e.preventDefault(); submitUsername($("onboard-input"), $("onboard-btn")); });
$("user-form").addEventListener("submit", (e) => { e.preventDefault(); submitUsername($("user-input"), e.submitter || e.target.querySelector("button")); });

document.addEventListener("keydown", (e) => {
  if (e.key === "Escape") return closeModals();
  const typing = /INPUT|TEXTAREA|SELECT/.test(document.activeElement.tagName);
  if (typing || e.metaKey || e.ctrlKey || e.altKey || !data.username || document.querySelector(".modal-bg.open")) return;
  const k = e.key.toLowerCase();
  if (k === "g") setView(view === "revise" ? "insights" : "revise");
  else if (k === "r") { e.preventDefault(); setView("revise"); pickRandom(); }
  else if (k === "/") { e.preventDefault(); setView("revise"); $("q").focus(); }
  else if (k === "o" && pick && view === "revise") $("pick-open")?.click();
  else if (pick && view === "revise" && "1234".includes(e.key)) rate(["again", "hard", "good", "easy"][+e.key - 1]);
});

// refresh when you come back to the tab (picks up edits made in Excel)
document.addEventListener("visibilitychange", () => {
  if (!document.hidden && !$("btn-sync").disabled) load().catch(() => {});
});

syncFilterUI();
load().catch((e) => toast(e.message, true));

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
