package com.leetvise.service;

import com.leetvise.config.LeetViseProperties;
import com.leetvise.excel.ExcelStore;
import com.leetvise.leetcode.LeetCodeClient;
import com.leetvise.leetcode.LeetCodeClient.Solved;
import com.leetvise.model.Problem;
import com.leetvise.model.Rating;
import com.leetvise.model.RevisionEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.*;

/**
 * All reads/writes go through here. Methods are synchronized so a long sync and a
 * revision never interleave while touching the workbook.
 */
@Service
public class ProblemService {

    private static final Logger log = LoggerFactory.getLogger(ProblemService.class);
    private static final long DAY = 86_400;
    private static final int MAX_DETAIL_LOOKUPS_PER_SYNC = 80;

    /** Everything the UI needs in one response. */
    public record State(String username, boolean authed, String excelPath, String lastSync, String profileTotal,
                        Collection<Problem> problems, List<RevisionEntry> log, long now, String message) { }

    private final ExcelStore store;
    private final LeetCodeClient leetCode;
    private final LeetViseProperties props;
    private final Clock clock;

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
        List<RevisionEntry> recent = d.log.subList(Math.max(0, d.log.size() - 40), d.log.size());
        return new State(props.username(), props.hasSession(), store.file().toString(),
                d.info.getOrDefault("Last Sync", ""), d.info.getOrDefault("LeetCode Solved (All)", ""),
                List.copyOf(d.problems.values()), List.copyOf(recent), now(), message);
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
        ExcelStore.Data d = store.data();
        int before = d.problems.size();
        List<String> notes = new ArrayList<>();

        try {
            leetCode.profileCounts().forEach((k, v) -> d.info.put("LeetCode Solved (" + k + ")", String.valueOf(v)));
        } catch (RuntimeException e) {
            notes.add("profile: " + e.getMessage());
        }

        if (leetCode.authed()) {
            try {
                for (Solved s : leetCode.allSolved()) applyMeta(d.problems.computeIfAbsent(s.slug(), Problem::new), s);
                long lastFull = ExcelStore.parse(d.info.get("Last Full Sync"));
                long stopBefore = full || lastFull == 0 ? 0 : lastFull - 2 * DAY;
                leetCode.acceptedTimes(stopBefore).forEach((slug, t) ->
                        d.problems.computeIfAbsent(slug, Problem::new).recordSolved(t[0], t[1]));
                d.info.put("Last Full Sync", ExcelStore.format(now()));
            } catch (RuntimeException e) {
                log.warn("Full sync failed", e);
                notes.add("full sync failed (" + e.getMessage() + ") – used public data instead");
            }
        }

        for (Solved s : leetCode.recentAccepted()) {
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

        d.info.put("Username", props.username());
        d.info.put("Last Sync", ExcelStore.format(now()));
        store.save();

        int added = d.problems.size() - before;
        String msg = "Synced – " + (added > 0 ? added + " new problem" + (added == 1 ? "" : "s") : "no new problems");
        if (!notes.isEmpty()) msg += " (" + String.join("; ", notes) + ")";
        return state(msg);
    }

    // ------------------------------------------------------------------ helpers

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
