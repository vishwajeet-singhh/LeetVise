/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.service;

import recusant_byte.vishwajeet.leetvise.config.LeetViseProperties;
import recusant_byte.vishwajeet.leetvise.excel.ExcelStore;
import recusant_byte.vishwajeet.leetvise.insights.Insights;
import recusant_byte.vishwajeet.leetvise.insights.InsightsCalculator;
import recusant_byte.vishwajeet.leetvise.leetcode.LeetCodeClient;
import recusant_byte.vishwajeet.leetvise.leetcode.LeetCodeClient.Profile;
import recusant_byte.vishwajeet.leetvise.leetcode.LeetCodeClient.Solved;
import recusant_byte.vishwajeet.leetvise.model.Problem;
import recusant_byte.vishwajeet.leetvise.model.Rating;
import recusant_byte.vishwajeet.leetvise.model.RevisionEntry;
import recusant_byte.vishwajeet.leetvise.model.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.time.Clock;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * All reads/writes go through here. Methods are synchronized so a long sync and a
 * revision never interleave while touching the workbook.
 */
@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);
    private static final long DAY = 86_400;
    private static final int MAX_DETAIL_LOOKUPS_PER_SYNC = 80;
    private static final String INFO_USERNAME = "Username";
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9._-]{1,50}");
    private static final Pattern PROFILE_URL = Pattern.compile("leetcode\\.com/(?:u/)?([A-Za-z0-9._-]+)");
    private static final List<String> DIFFICULTIES = List.of("All", "Easy", "Medium", "Hard");

    /**
     * Everything the UI needs in one response.
     *
     * @param usernameSource where the username came from: "env" (.env), "session" (the cookie), "sheet" (typed in the app) or ""
     * @param excelPath      absolute path of the workbook
     * @param excelDisplay   the same path relative to the folder LeetVise runs in (e.g. data/leetvise.xlsx), if it's inside it
     */
    public record State(String username, String usernameSource, boolean authed, String excelPath, String excelDisplay, String lastSync,
                        Map<String, Long> leetCodeSolved, Map<String, Long> leetCodeTotal, int submissions,
                        Collection<Problem> problems, List<RevisionEntry> log, long now, String message) { }

    private record Account(String name, String source) { }

    private final ExcelStore store;
    private final LeetCodeClient leetCode;
    private final LeetViseProperties props;
    private final Clock clock;

    private String sessionUser;
    private long sessionUserCheckedAt = Long.MIN_VALUE / 2;

    @Autowired
    public ProblemService(ExcelStore store, LeetCodeClient leetCode, LeetViseProperties props) {
        this(store, leetCode, props, Clock.systemDefaultZone());
    }

    ProblemService(ExcelStore store, LeetCodeClient leetCode, LeetViseProperties props, Clock clock) {
        this.store = store;
        this.leetCode = leetCode;
        this.props = props;
        this.clock = clock;
    }

    public synchronized State state(String message) {
        ExcelStore.Data d = store.data();
        Account a = account();
        List<RevisionEntry> recent = d.log.subList(Math.max(0, d.log.size() - 60), d.log.size());
        return new State(a.name(), a.source(), props.hasSession(), store.file().toString(), excelDisplay(),
                d.info.getOrDefault("Last Sync", ""), infoCounts(d, "LeetCode Solved"), infoCounts(d, "LeetCode Total"),
                d.submissions.size(), List.copyOf(d.problems.values()), List.copyOf(recent), now(), message);
    }

    public synchronized Insights insights() {
        return new InsightsCalculator(store.data(), now(), clock.getZone()).compute();
    }

    // ------------------------------------------------------------------ username

    public synchronized String username() {
        return account().name();
    }

    /** .env wins, then the session cookie's owner, then whatever was typed in the app (kept in the Info sheet). */
    private Account account() {
        if (!props.username().isEmpty()) return new Account(props.username(), "env");
        String fromSession = sessionUser();
        if (!fromSession.isEmpty()) return new Account(fromSession, "session");
        String saved = store.data().info.getOrDefault(INFO_USERNAME, "");
        return new Account(saved, saved.isEmpty() ? "" : "sheet");
    }

    /** Asks LeetCode who the cookie belongs to; remembered, and retried at most every 5 minutes on failure. */
    private String sessionUser() {
        if (!props.hasSession()) return "";
        if (sessionUser == null && now() - sessionUserCheckedAt > 300) {
            sessionUserCheckedAt = now();
            try {
                String u = leetCode.signedInUsername();
                if (!u.isEmpty()) sessionUser = u;
                else log.warn("LEETCODE_SESSION is set but LeetCode says you're not signed in – the cookie has probably expired");
            } catch (RuntimeException e) {
                log.warn("Could not look up the username for your session cookie: {}", e.getMessage());
            }
        }
        return sessionUser == null ? "" : sessionUser;
    }

    public synchronized State setUsername(String input) {
        Account current = account();
        if ("env".equals(current.source())) {
            throw new IllegalArgumentException("Your username is set in .env (LEETCODE_USERNAME) – change it there and restart");
        }
        if ("session".equals(current.source())) {
            throw new IllegalArgumentException("Your username comes from the session cookie (@" + current.name() + ")");
        }
        String u = input == null ? "" : input.trim();
        Matcher m = PROFILE_URL.matcher(u);
        if (m.find()) u = m.group(1);
        u = u.replaceFirst("^@", "");
        if (!USERNAME.matcher(u).matches()) throw new IllegalArgumentException("That doesn't look like a LeetCode username");
        Profile profile = leetCode.profile(u); // throws if the user doesn't exist
        ExcelStore.Data d = store.data();
        boolean switched = !current.name().isEmpty() && !current.name().equalsIgnoreCase(u) && !d.problems.isEmpty();
        d.info.put(INFO_USERNAME, u);
        putProfile(d, profile);
        store.save();
        return state(switched ? "Switched to @" + u + " – your sheet still holds @" + current.name() + "'s problems"
                : "Hi @" + u + "!");
    }

    // ------------------------------------------------------------------ revision

    public synchronized State revise(String slug, String ratingText) {
        Rating rating = Rating.parse(ratingText);
        ExcelStore.Data d = store.data();
        Problem p = require(d, slug);
        long now = now();
        p.setIntervalDays(rating.nextInterval(p.getIntervalDays()));
        p.setConfidence(rating.confidence());
        p.setTimesRevised(p.getTimesRevised() + 1);
        p.setLastRevised(now);
        p.setNextReview(now + p.getIntervalDays() * DAY);
        d.log.add(new RevisionEntry(now, p.getSlug(), p.getId(), p.getTitle(), p.getDifficulty(), rating.label()));
        store.save();
        return state("Saved to Excel");
    }

    public synchronized State saveNote(String slug, String notes) {
        ExcelStore.Data d = store.data();
        require(d, slug).setNotes(notes == null ? "" : notes.strip());
        store.save();
        return state("Note saved");
    }

    public synchronized State add(String slugOrUrl) {
        String slug = ExcelStore.slugFromUrl(slugOrUrl);
        if (slug.isEmpty()) throw new IllegalArgumentException("Paste a LeetCode URL or a slug like two-sum");
        Solved s = leetCode.questionDetails(slug);
        ExcelStore.Data d = store.data();
        boolean existed = d.problems.containsKey(s.slug());
        Problem p = d.problems.computeIfAbsent(s.slug(), Problem::new);
        applyMeta(p, s);
        if (p.getLastSolved() == 0) p.recordSolved(now(), now());
        store.save();
        return state(existed ? s.title() + " is already in your list" : "Added " + s.title());
    }

    // ------------------------------------------------------------------ sync

    public synchronized State sync(boolean full) {
        String user = username();
        if (user.isEmpty()) throw new IllegalArgumentException("Tell LeetVise your LeetCode username first");
        ExcelStore.Data d = store.data();
        int before = d.problems.size();
        int newSubmissions = 0;
        List<String> notes = new ArrayList<>();

        try {
            putProfile(d, leetCode.profile(user));
        } catch (RuntimeException e) {
            notes.add("profile: " + e.getMessage());
        }

        if (leetCode.authed()) {
            try {
                for (Solved s : leetCode.allSolved()) applyMeta(d.problems.computeIfAbsent(s.slug(), Problem::new), s);
                long lastFull = ExcelStore.parse(d.info.get("Last Full Sync"));
                // no submissions stored yet (e.g. first sync after upgrading) -> walk the whole history once
                long stopBefore = full || lastFull == 0 || d.submissions.isEmpty() ? 0 : lastFull - 2 * DAY;
                for (Submission s : leetCode.submissions(stopBefore)) {
                    if (d.submissions.put(s.id(), s) == null) newSubmissions++;
                    if (s.accepted()) d.problems.computeIfAbsent(s.slug(), Problem::new).recordSolved(s.date(), s.date());
                }
                d.info.put("Last Full Sync", ExcelStore.format(now()));
            } catch (RuntimeException e) {
                log.warn("Full sync failed", e);
                notes.add("full sync failed (" + e.getMessage() + ") – used public data instead");
            }
        }

        for (Solved s : leetCode.recentAccepted(user)) {
            Problem p = d.problems.computeIfAbsent(s.slug(), Problem::new);
            if (p.getTitle().isEmpty()) p.setTitle(s.title());
            p.recordSolved(s.firstTs(), s.lastTs());
        }

        // fill in number / difficulty / tags for rows that are missing them
        int lookups = 0;
        for (Problem p : d.problems.values()) {
            if (!p.getDifficulty().isEmpty() && !p.getId().isEmpty()) continue;
            if (lookups++ >= MAX_DETAIL_LOOKUPS_PER_SYNC) {
                notes.add("more details will load on the next sync");
                break;
            }
            try {
                applyMeta(p, leetCode.questionDetails(p.getSlug()));
                Thread.sleep(120);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (RuntimeException e) {
                notes.add(p.getSlug() + ": " + e.getMessage());
            }
        }

        d.info.put(INFO_USERNAME, user);
        d.info.put("Last Sync", ExcelStore.format(now()));
        store.save();

        int added = d.problems.size() - before;
        String msg = "Synced – " + (added > 0 ? added + " new problem" + (added == 1 ? "" : "s") : "no new problems");
        if (newSubmissions > 0) msg += ", " + newSubmissions + " new submission" + (newSubmissions == 1 ? "" : "s");
        if (!notes.isEmpty()) msg += " (" + String.join("; ", notes) + ")";
        return state(msg);
    }

    // ------------------------------------------------------------------ helpers

    /** Where the workbook lives, as you'd type it: relative to the folder LeetVise was started from when it's inside it. */
    private String excelDisplay() {
        Path file = store.file();
        Path cwd = Path.of("").toAbsolutePath();
        return file.startsWith(cwd) ? cwd.relativize(file).toString() : file.toString();
    }

    private static void putProfile(ExcelStore.Data d, Profile p) {
        p.solved().forEach((k, v) -> d.info.put("LeetCode Solved (" + k + ")", String.valueOf(v)));
        p.totals().forEach((k, v) -> d.info.put("LeetCode Total (" + k + ")", String.valueOf(v)));
    }

    private static Map<String, Long> infoCounts(ExcelStore.Data d, String prefix) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String k : DIFFICULTIES) {
            String v = d.info.get(prefix + " (" + k + ")");
            if (v == null || v.isBlank()) continue;
            try {
                out.put(k, Long.parseLong(v.trim()));
            } catch (NumberFormatException ignored) {
                // hand-edited cell – skip it
            }
        }
        return out;
    }

    private static Problem require(ExcelStore.Data d, String slug) {
        Problem p = d.problems.get(slug);
        if (p == null) throw new NoSuchElementException("Unknown problem: " + slug);
        return p;
    }

    private static void applyMeta(Problem p, Solved s) {
        if (!s.title().isEmpty()) p.setTitle(s.title());
        if (!s.id().isEmpty()) p.setId(s.id());
        if (!s.difficulty().isEmpty()) p.setDifficulty(s.difficulty());
        if (!s.tags().isEmpty()) p.setTags(s.tags());
    }

    private long now() {
        return clock.instant().getEpochSecond();
    }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
