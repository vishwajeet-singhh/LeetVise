# LeetVise

Revise the LeetCode problems you've already solved, and see where you keep slipping.

Built by **Vishwajeet Pratap Singh** · [GitHub](https://github.com/vishwajeet-singhh) · [LinkedIn](https://www.linkedin.com/in/vishwajeetsage/) · [vishwajeet.me](https://vishwajeet.me)

- **Revise**: picks the right problem to redo and schedules the next review (spaced repetition).
- **Insights**: strong and weak topics, mistake patterns (wrong answers, TLEs, runtime errors), retry habits, best time of day, an activity heatmap.
- **Local**: runs on your machine, and your data is an Excel file you can open and edit.

## Run

Needs **Java 21+**. Maven is downloaded automatically.

```bash
git clone https://github.com/vishwajeet-singhh/LeetVise.git
cd LeetVise
./run.sh        # Windows: run.cmd
```

It opens http://localhost:8181 and asks for your LeetCode username. A downloaded ZIP works the same way.

## Load your full history (optional)

LeetCode only shares your ~20 latest solves publicly. To load everything and unlock the mistake analysis:

1. On leetcode.com, open DevTools → **Application → Cookies**.
2. Copy `LEETCODE_SESSION` and `csrftoken` into `.env`:
   ```properties
   LEETCODE_SESSION=...
   LEETCODE_CSRF=...
   ```
3. Restart and click **Sync**. The first sync takes about a minute; **Shift-click** forces a full re-scan.

Treat the cookie like a password. `.env` is git-ignored, and LeetVise only sends the cookie to leetcode.com.
It expires every few weeks, so paste a fresh one when sync asks.

Other `.env` settings: `LEETCODE_USERNAME`, `LEETVISE_EXCEL` (`data/leetvise.xlsx`), `LEETVISE_PORT` (`8181`), `LEETVISE_OPEN_BROWSER` (`true`).

## Good to know

- **Reviews**: after re-solving, rate it Again / Hard / Good / Easy. The next review is then tomorrow / ~3 days / 1 week+ / 2 weeks+.
- **Topic scores** mix accuracy, revision recall, freshness and difficulty. A topic is only rated once it holds 12.5% of your solves.
- **Excel**: edit it freely and the app picks up the changes. Close it before revising in the app. Daily backups go to `data/backups/`.
- **Shortcuts**: `R` pick · `O` open · `1–4` rate · `/` search · `G` switch view.

## Troubleshooting

| Problem | Fix |
|---|---|
| Needs Java 21 | Install JDK 21+ and open a new terminal |
| Sync: *cookie expired* / 403 | Paste fresh `LEETCODE_SESSION` and `LEETCODE_CSRF` into `.env`, then restart |
| Can't save the Excel file | Close it in Excel |
| Port 8181 in use | Set `LEETVISE_PORT=8282` in `.env` |

## Just for fun

This is a personal side project, **not affiliated with LeetCode**. It reads your own data through LeetCode's
unofficial endpoints, which can change at any time. Use it at your own risk.

---

**Vishwajeet Pratap Singh** · [GitHub](https://github.com/vishwajeet-singhh) · [LinkedIn](https://www.linkedin.com/in/vishwajeetsage/) · [vishwajeet.me](https://vishwajeet.me)
<br>Java 21 · Spring Boot · Apache POI · vanilla JS
