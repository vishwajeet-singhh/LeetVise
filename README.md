# LeetVise

Revise the LeetCode problems you've already solved, so you stop forgetting them.
Spring Boot 3.5 + Java 21. No database: your data lives in an **Excel file** (Apache POI).

## Run

```bash
mvn spring-boot:run
```

It opens http://localhost:8181 in your browser. From IntelliJ, run `LeetViseApplication`.
Tests: `mvn test`.

## Project layout

```
src/main/java/com/leetvise/
  LeetViseApplication.java      Spring Boot entry point
  config/LeetViseProperties     leetvise.* settings
  excel/ExcelStore              reads/writes the workbook (the "database")
  leetcode/LeetCodeClient       LeetCode GraphQL + submissions API (RestClient)
  model/                        Problem, RevisionEntry, Rating (spaced repetition)
  service/ProblemService        sync, revise, notes, add
  web/                          REST controller (/api/*), error handler, localhost-only guard
src/main/resources/
  application.properties        defaults (port, username, Excel path)
  static/                       the UI: index.html, style.css, app.js
```

## Load *all* your solved problems

Without logging in, LeetCode only shares your ~20 most recent solves. To pull every problem:

1. Log in on leetcode.com, open DevTools (F12) → Application → Cookies → `https://leetcode.com`.
2. Copy `LEETCODE_SESSION` (and `csrftoken`).
3. `cp config.properties.example config.properties` and paste them into `leetvise.session=` / `leetvise.csrftoken=`
   (or `export LEETCODE_SESSION=...` before `mvn spring-boot:run`).
4. Click **Sync LeetCode**. The first full sync walks your submission history, so it can take a minute.
   Later syncs only fetch what's new. Hold **Shift** while clicking Sync to force a full re-scan.

The cookie expires every few weeks. If sync says "session cookie expired", copy a fresh one.

## The Excel file

Default: `data/leetvise.xlsx` in the project folder. You can change it with `leetvise.excel=` in `config.properties`.

| Sheet | What's in it |
|---|---|
| **Problems** | One row per problem: #, title, clickable URL, difficulty, tags, first/last solved, times revised, next review, confidence, notes |
| **Revision Log** | Every revision you do (date, problem, rating). Only ever appended to. |
| **Info** | Last sync time, your LeetCode totals |

- You can edit it in Excel (notes, dates, add rows with just a `Slug` or `URL`). The app reloads it when it changes.
  **Close Excel before you revise in the app**, or saving will fail while the file is locked.
- Before the first save each day, the previous version is copied to `data/backups/` (git-ignored).
- Saves are atomic (written to a temp file, then swapped in), so a crash can't corrupt it.

## How revision works

- **🎲 Pick random** (or press `R`) picks from whatever the filters show. Problems you haven't touched in a long time,
  rated Again/Hard, or that are due get picked more often.
- After solving it again, rate it (`1`–`4`): **Again** → tomorrow · **Hard** → ~3 days · **Good** → 1 week, doubling · **Easy** → 2 weeks, tripling (max 6 months).
- A problem you've never revised becomes **due** a week after you solved it.

Filters: search, Easy/Medium/Hard, solved in the last / more than 7·15·30·90 days ago, topic, Due now, Never revised, Weak.
Click a stat card or a topic tag to filter by it. **⬇ CSV** exports what the table currently shows.

Shortcuts: `R` random · `O` open the pick · `1`–`4` rate · `/` search.
