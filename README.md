# LeetVise

Made by **Vishwajeet Pratap Singh** · [GitHub](https://github.com/vishwajeet-singhh) · [LinkedIn](https://www.linkedin.com/in/vishwajeetsage/) · [vishwajeet.me](https://vishwajeet.me)

**Stop forgetting the LeetCode problems you've already solved.**

LeetVise pulls your solved problems into an Excel sheet on your own computer, picks the right one to revise
next (spaced repetition), and analyses your submission history so you can see where you keep slipping:
weak topics, wrong-answer habits, TLE hotspots, rushed resubmits, and the time of day you code best.

- **Runs locally.** It's a small Java app with a web UI at `http://localhost:8181`. No account, no cloud, no Docker.
- **Your data is an Excel file** (`data/leetvise.xlsx`) that you can open, edit and back up like any other file.
- **The analysis is plain Java code.** It's computed from your own history, with no AI and no external service.

---

## Quick start

You need **Java 21 or newer** ([Adoptium](https://adoptium.net) is a good free build). You don't need Maven:
the `mvnw` wrapper downloads it the first time.

```bash
git clone https://github.com/vishwajeet-singhh/LeetVise.git
cd LeetVise
./run.sh
```

On **Windows**, double-click `run.cmd` instead. If you downloaded the **ZIP**, unzip it and do the same thing
in the unzipped folder. On macOS/Linux, `sh run.sh` works if the file lost its execute permission.

The first start downloads dependencies, which takes about a minute. LeetVise then opens in your browser
and asks for your LeetCode username. Press `Ctrl+C` in the terminal to stop it.

> Prefer the raw commands? `./mvnw spring-boot:run` (Windows: `mvnw.cmd spring-boot:run`).
> Run the tests with `./mvnw test`.

## Load everything (recommended)

Without logging in, LeetCode only shares your **~20 most recent** solves publicly. Give LeetVise your session
cookie and it loads **every** solved problem plus your **full submission history**. The history powers the
Insights page.

1. Log in on [leetcode.com](https://leetcode.com) and open DevTools (`F12`) → **Application** → **Cookies** → `https://leetcode.com`.
2. Copy the values of `LEETCODE_SESSION` and `csrftoken`.
3. Paste them into `.env` in the project folder. `run.sh`/`run.cmd` create it from `.env.example` on the
   first start, or you can copy it yourself:

   ```properties
   LEETCODE_SESSION=eyJ0eXAiOiJKV1Qi...
   LEETCODE_CSRF=AbCdEf123...
   ```

4. Restart LeetVise and click **Sync**. The first sync walks your whole submission history. LeetCode allows
   about one page of 20 submissions per second, so a few hundred submissions take about a minute. Later syncs
   only fetch what's new; **Shift-click Sync** forces a full re-scan.

Your username is detected from the cookie automatically. The cookie expires every few weeks. When sync says
*"session cookie expired"*, copy fresh values into `.env`.

> 🔒 **Treat the cookie like a password.** `.env` is git-ignored, so never paste the cookie anywhere else.
> LeetVise only ever sends it to `leetcode.com`.

### All settings (`.env`)

| Key | Default | What it does |
|---|---|---|
| `LEETCODE_USERNAME` | *(detected)* | Your username. Optional: it's detected from the cookie, or you type it in the app. |
| `LEETCODE_SESSION` | | Session cookie that unlocks everything (see above). |
| `LEETCODE_CSRF` | | The `csrftoken` cookie. Sent along with the session. |
| `LEETVISE_EXCEL` | `data/leetvise.xlsx` | Where the workbook lives (relative to the project folder). |
| `LEETVISE_PORT` | `8181` | Port of the local web UI. |
| `LEETVISE_OPEN_BROWSER` | `true` | Open the browser on start. |

Real environment variables with the same names override `.env`.

## What you get

### Revise

- **Pick random** (`R`) chooses a problem from whatever the table currently shows. Problems you haven't
  touched for a while, rated Again/Hard, needed several tries on LeetCode, or that are due come up more often.
- After solving it again, rate it `1`–`4`. The rating sets the next review:
  **Again** → tomorrow · **Hard** → ~3 days · **Good** → 1 week, then doubling · **Easy** → 2 weeks, then tripling (max 6 months).
  A problem you've never revised becomes due a week after you solved it.
- You can filter by search, difficulty, solved date, topic, *Due*, *Never revised*, *Weak* (last rated Again/Hard)
  and *Struggled* (2+ failed submissions). Click a stat tile or a topic tag to filter by it.
- Notes per problem, **Add** a problem by URL, **CSV** export of the current view, and **Open Excel**.

### Insights

All of this is computed in Java (`InsightsCalculator`) from your problems, submissions and revisions:

| Section | What it tells you |
|---|---|
| **Headline numbers** | Acceptance rate, share solved on the first try, tries per problem, failed submissions, revision recall, active days. |
| **What to work on** | Ranked, concrete suggestions: your focus topic, your most common mistake and how to avoid it, TLE hotspots, error-prone topics, rushed resubmits, best time of day, revision backlog, fading topics, difficulty balance, unfinished problems. |
| **Topic mastery** | A 0–100 score per topic with the reasons behind it, plus first-try rate, acceptance, mistakes by type (WA / TLE / RE / CE), recall and when you last practised it. Click a topic to revise its problems. |
| **Mistake types** | Wrong Answer vs Time Limit vs Runtime/Compile errors across all your failed submissions. |
| **By difficulty** | Solved out of LeetCode's total, with first-try and acceptance rates per level. |
| **Time of day** | Acceptance rate in 4-hour windows. |
| **Activity** | A year-long heatmap of submissions and revisions. |
| **Took the most tries / Tried, never solved** | The problems worth revisiting first. |
| **After a failed submit** | Whether resubmitting within 2 minutes works as well as pausing to debug. |

**How the topic score works.** It's a weighted mix of whatever is known about the topic:
accuracy 40% (first-try and acceptance rate), revision recall 30% (share rated Good/Easy), freshness 20%
(decays over ~6 weeks without practice) and depth 10% (share of Medium/Hard). Each rate is blended with your
own overall average, so a topic with two submissions can't swing to 0% or 100%. The score tells you how a
topic compares to *you*. Strong ≥ 70, Weak < 50.

**A topic is only analysed once it holds at least 12.5% of everything you've solved** (with 238 solved that's 30+
problems), so a handful of lucky solves never makes a topic "strong". Smaller topics don't appear in Insights at all
(you can still filter the problem table by them).

Without the session cookie, Insights still shows topic scores, difficulty progress, activity and revision
stats. The mistake analysis needs the submission history.

## The Excel file

`data/leetvise.xlsx` is the database. Each sheet is a table, and columns are matched by header name, so you
can reorder them.

| Sheet | What's in it |
|---|---|
| **Problems** | One row per solved problem: #, title, link, difficulty, topics, first/last solved, revisions, next review, interval, confidence, notes |
| **Revision Log** | Every revision you do (date, problem, rating). It is only ever appended to. |
| **Submissions** | Your LeetCode submission history (date, status, language, runtime, memory). It feeds Insights. |
| **Info** | Username, last sync time, your LeetCode totals |

- You can edit the file in Excel: change notes or dates, or add rows with just a `Slug` or `URL`. The app reloads it when it changes.
  **Close Excel before you revise in the app**, because saving fails while Excel locks the file.
- Before the first save each day, the previous version is copied to `data/backups/`.
- Saves are atomic: the app writes a temp file and then swaps it in, so a crash can't corrupt the workbook.
- `data/` is git-ignored, so your progress never ends up in a commit. Back it up however you like.

## Keyboard shortcuts

`R` random pick · `O` open the pick · `1`–`4` rate it · `/` search · `G` switch between Revise and Insights · `Esc` close dialogs

## Troubleshooting

| Problem | Fix |
|---|---|
| `java: command not found`, or "needs Java 21" | Install JDK 21+ and open a new terminal. |
| Sync says *session cookie expired* / HTTP 403 | Copy fresh `LEETCODE_SESSION` **and** `LEETCODE_CSRF` values into `.env` and restart. |
| *Could not save leetvise.xlsx – is it open in Excel?* | Close the file in Excel and try again. |
| Port 8181 already in use | Set `LEETVISE_PORT=8282` in `.env`. |
| Only ~20 problems loaded | Add the session cookie (see [Load everything](#load-everything-recommended)). |

## Project layout

```
src/main/java/recusant_byte/vishwajeet/leetvise/
  LeetViseApplication.java       Spring Boot entry point
  config/LeetViseProperties      settings (filled from .env)
  excel/ExcelStore               reads/writes the workbook (the "database")
  leetcode/LeetCodeClient        LeetCode GraphQL + submission history
  insights/InsightsCalculator    all the analysis: topic scores, mistakes, retries, tips…
  model/                         Problem, Submission, RevisionEntry, Rating (spaced repetition)
  service/ProblemService         sync, revise, notes, username
  web/                           REST API (/api/*), error handler, localhost-only guard
src/main/resources/
  application.properties         defaults + how .env maps onto settings
  static/                        the UI: index.html, style.css, app.js (no build step)
```

Spring Boot 3.5 · Java 21 · Apache POI · vanilla JS. The server only listens on `127.0.0.1`, and API calls from
other websites are rejected.

## Contributing

Issues and pull requests are welcome. Please run `./mvnw test` before opening a PR.

## Author

**Vishwajeet Pratap Singh**, who designed and built LeetVise.

- GitHub: [github.com/vishwajeet-singhh](https://github.com/vishwajeet-singhh)
- LinkedIn: [linkedin.com/in/vishwajeetsage](https://www.linkedin.com/in/vishwajeetsage/)
- Portfolio: [vishwajeet.me](https://vishwajeet.me)

If you fork it or build on it, please keep this credit and the copyright notice.

## License

[MIT](LICENSE) © 2026 Vishwajeet Pratap Singh
