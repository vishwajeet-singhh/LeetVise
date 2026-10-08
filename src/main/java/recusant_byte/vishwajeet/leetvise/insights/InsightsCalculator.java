/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Copyright (c) 2026 Vishwajeet Pratap Singh
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.insights;

import recusant_byte.vishwajeet.leetvise.excel.ExcelStore;
import recusant_byte.vishwajeet.leetvise.insights.Insights.*;
import recusant_byte.vishwajeet.leetvise.model.Problem;
import recusant_byte.vishwajeet.leetvise.model.RevisionEntry;
import recusant_byte.vishwajeet.leetvise.model.Submission;

import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Turns the workbook (problems, revision log, submission history) into {@link Insights}.
 * Pure calculation – no I/O – so it is cheap to run on every request and easy to test.
 *
 * <p>Topic score (0..100) is a weighted mix of whatever is known for that topic:
 * <ul>
 *   <li>accuracy 40% – 60% first-try rate + 40% acceptance rate (needs submission history)</li>
 *   <li>recall 30% – share of revisions you rated Good or Easy</li>
 *   <li>freshness 20% – how recently you touched its problems (decays over ~6 weeks)</li>
 *   <li>depth 10% – how many of them are Medium/Hard</li>
 * </ul>
 * A topic is only analysed once it holds at least 12.5% of everything you've solved – with 238 solved that's
 * 30 problems – so a handful of lucky solves never makes a topic "strong". Smaller topics are left out entirely.
 *
 * <p>Each rate is first blended with your own overall average (a couple of "virtual" samples), so a topic
 * with two submissions can't swing to 0% or 100% – scores say how a topic compares to <i>you</i>.
 */
public final class InsightsCalculator {

    /** Share of your solved problems a topic needs before it is rated. */
    static final double MASTERY_SHARE = 0.125;
    static final int MIN_PROBLEMS = 3;
    static final long QUICK_RETRY_SECONDS = 120;
    static final long RETRY_WINDOW_SECONDS = 6 * 3600;
    private static final long DAY = 86_400;
    private static final int HEATMAP_WEEKS = 53;
    private static final List<String> LEVELS = List.of("Easy", "Medium", "Hard");
    private static final String WA = "Wrong Answer", TLE = "Time Limit Exceeded", RE = "Runtime Error",
            CE = "Compile Error", MLE = "Memory Limit Exceeded", OTHER = "Other";
    private static final List<String> ERROR_TYPES = List.of(WA, TLE, RE, CE, MLE, "Output Limit Exceeded");

    private final ExcelStore.Data data;
    private final long now;
    private final ZoneId zone;
    /** Problems a topic needs to be analysed: 12.5% of your solved problems, but never fewer than 3. */
    private final int minProblems;

    /** Submissions per problem, oldest first. */
    private final Map<String, List<Submission>> bySlug = new HashMap<>();
    private final Map<String, Attempts> attempts = new HashMap<>();
    /** Submissions until the first Accepted, for solved problems with history. */
    private final Map<String, Integer> toFirstAc = new HashMap<>();

    public InsightsCalculator(ExcelStore.Data data, long now, ZoneId zone) {
        this.data = data;
        this.now = now;
        this.zone = zone;
        this.minProblems = Math.max(MIN_PROBLEMS, (int) Math.ceil(MASTERY_SHARE * data.problems.size()));
    }

    public Insights compute() {
        indexSubmissions();
        Overview overview = overview();
        List<TopicStat> topics = topics(overview);
        // needs work: your lower half of rated topics, never a "strong" one; strongest: the best of the rest, never a "weak" one
        List<TopicStat> eligible = topics;
        List<String> weakest = eligible.reversed().stream().limit(Math.min(5, eligible.size() / 2))
                .filter(t -> !"strong".equals(t.level())).map(TopicStat::name).toList();
        List<String> strongest = eligible.stream().filter(t -> !"weak".equals(t.level()) && !weakest.contains(t.name()))
                .limit(5).map(TopicStat::name).toList();
        List<Count> mistakes = mistakes();
        List<HourStat> hours = hours();
        Retries retries = retries();
        List<Unsolved> unsolved = unsolved();
        List<DifficultyStat> difficulties = difficulties();
        List<Tip> tips = tips(overview, topics, weakest, strongest, mistakes, hours, retries, unsolved, difficulties);
        return new Insights(!data.submissions.isEmpty(), minProblems, overview, topics, strongest, weakest, mistakes, difficulties,
                hours, activity(), languages(), retries, struggles(), unsolved, tips, attempts);
    }

    // ================================================================== per-problem

    private void indexSubmissions() {
        for (Submission s : data.submissions.values()) bySlug.computeIfAbsent(s.slug(), k -> new ArrayList<>()).add(s);
        Comparator<Submission> order = Comparator.comparingLong(Submission::date).thenComparingLong(Submission::id);
        bySlug.forEach((slug, list) -> {
            list.sort(order);
            int failed = (int) list.stream().filter(s -> !s.accepted()).count();
            int firstAc = -1;
            for (int i = 0; i < list.size(); i++) {
                if (list.get(i).accepted()) { firstAc = i + 1; break; }
            }
            attempts.put(slug, new Attempts(list.size(), failed, firstAc == 1));
            if (firstAc > 0) toFirstAc.put(slug, firstAc);
        });
    }

    // ================================================================== overview

    private Overview overview() {
        int subs = data.submissions.size();
        int accepted = (int) data.submissions.values().stream().filter(Submission::accepted).count();
        List<Integer> solvedAttempts = data.problems.keySet().stream().map(toFirstAc::get).filter(Objects::nonNull).toList();
        Double firstTry = solvedAttempts.isEmpty() ? null : solvedAttempts.stream().filter(n -> n == 1).count() / (double) solvedAttempts.size();
        Double avgAttempts = solvedAttempts.isEmpty() ? null : solvedAttempts.stream().mapToInt(Integer::intValue).average().orElse(0);
        Set<LocalDate> active = new HashSet<>();
        LocalDate from = day(now).minusDays(29);
        data.submissions.values().forEach(s -> active.add(day(s.date())));
        data.log.forEach(e -> active.add(day(e.date())));
        active.removeIf(d -> d.isBefore(from) || d.isAfter(day(now)));
        int due = (int) data.problems.values().stream().filter(this::isDue).count();
        return new Overview(data.problems.size(), subs, accepted, subs - accepted, rate(accepted, subs), firstTry,
                avgAttempts, data.log.size(), recall(data.log), active.size(), due);
    }

    // ================================================================== topics

    private List<TopicStat> topics(Overview overall) {
        Map<String, List<Problem>> byTag = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        for (Problem p : data.problems.values()) for (String t : tags(p)) byTag.computeIfAbsent(t, k -> new ArrayList<>()).add(p);
        Map<String, List<RevisionEntry>> logBySlug = data.log.stream().collect(Collectors.groupingBy(RevisionEntry::slug));

        List<TopicStat> out = new ArrayList<>();
        byTag.forEach((tag, problems) -> {
            int n = problems.size();
            if (n < minProblems) return; // too small a share of your solves to say anything about
            int[] diff = new int[3];
            int subs = 0, failed = 0, wa = 0, tle = 0, re = 0, ce = 0, other = 0, firstTries = 0, withHistory = 0, due = 0;
            long accepted = 0;
            double attemptSum = 0, freshness = 0, depth = 0;
            List<Integer> touchedDays = new ArrayList<>();
            List<RevisionEntry> revisions = new ArrayList<>();
            for (Problem p : problems) {
                int level = LEVELS.indexOf(p.getDifficulty());
                if (level >= 0) diff[level]++;
                depth += level == 0 ? 0.4 : level == 1 ? 0.75 : level == 2 ? 1.0 : 0.6;
                for (Submission s : bySlug.getOrDefault(p.getSlug(), List.of())) {
                    subs++;
                    if (s.accepted()) { accepted++; continue; }
                    failed++;
                    switch (errorType(s.status())) {
                        case WA -> wa++;
                        case TLE -> tle++;
                        case RE -> re++;
                        case CE -> ce++;
                        default -> other++;
                    }
                }
                Integer first = toFirstAc.get(p.getSlug());
                if (first != null) {
                    withHistory++;
                    attemptSum += first;
                    if (first == 1) firstTries++;
                }
                long touched = Math.max(p.getLastSolved(), p.getLastRevised());
                if (touched > 0) {
                    int days = (int) Math.max(0, (now - touched) / DAY);
                    touchedDays.add(days);
                    freshness += Math.exp(-days / 45.0);
                }
                if (isDue(p)) due++;
                revisions.addAll(logBySlug.getOrDefault(p.getSlug(), List.of()));
            }
            Double firstTry = withHistory == 0 ? null : firstTries / (double) withHistory;
            Double acceptance = rate(accepted, subs);
            Double avgAttempts = withHistory == 0 ? null : attemptSum / withHistory;
            Double recall = recall(revisions);
            Integer median = median(touchedDays);

            long goodRevisions = revisions.stream().filter(InsightsCalculator::wentWell).count();
            double weights = 0, sum = 0;
            if (firstTry != null && acceptance != null) {
                double ft = blend(firstTries, withHistory, overall.firstTryRate(), 2);
                double ac = blend(accepted, subs, overall.acceptanceRate(), 3);
                weights += 0.4;
                sum += 0.4 * (0.6 * ft + 0.4 * ac);
            }
            if (recall != null) { weights += 0.3; sum += 0.3 * blend(goodRevisions, revisions.size(), overall.recallRate(), 2); }
            if (!touchedDays.isEmpty()) { weights += 0.2; sum += 0.2 * freshness / touchedDays.size(); }
            weights += 0.1;
            sum += 0.1 * depth / n;
            double raw = sum / weights * 100;
            int score = (int) Math.round((n * raw + 50.0) / (n + 1));
            String level = score >= 70 ? "strong" : score < 50 ? "weak" : "steady";

            List<String> reasons = new ArrayList<>();
            if (firstTry != null) {
                Double avg = overall.firstTryRate();
                String vs = avg == null || Math.abs(firstTry - avg) < 0.1 ? "" : " (you avg " + pct(avg) + ")";
                reasons.add(pct(firstTry) + " solved first try" + vs);
            }
            if (failed > 0) {
                List<String> errs = new ArrayList<>();
                if (wa > 0) errs.add(plural(wa, "wrong answer"));
                if (tle > 0) errs.add(tle + " TLE");
                if (re > 0) errs.add(plural(re, "runtime error"));
                if (ce > 0) errs.add(plural(ce, "compile error"));
                if (other > 0) errs.add(other + " other");
                reasons.add(String.join(", ", errs));
            }
            if (recall != null) reasons.add(pct(recall) + " of " + plural(revisions.size(), "revision") + " went Good/Easy");
            if (median != null && median >= 30) reasons.add("untouched for " + median + " days");
            if (n >= MIN_PROBLEMS && diff[1] + diff[2] == 0 && diff[0] > 0) reasons.add("only Easy problems so far");
            if (due > 0) reasons.add(due + " due for revision");

            out.add(new TopicStat(tag, n, diff[0], diff[1], diff[2], subs, failed, wa, tle, re, ce, other, firstTry,
                    acceptance, avgAttempts, revisions.size(), recall, due, median, score, level, reasons));
        });
        out.sort(Comparator.comparingInt(TopicStat::score).reversed()
                .thenComparing(Comparator.comparingInt(TopicStat::solved).reversed()));
        return out;
    }

    // ================================================================== mistakes, difficulty, time

    private List<Count> mistakes() {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Submission s : data.submissions.values()) if (!s.accepted()) counts.merge(errorType(s.status()), 1, Integer::sum);
        return counts.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .map(e -> new Count(e.getKey(), e.getValue())).toList();
    }

    private List<DifficultyStat> difficulties() {
        List<DifficultyStat> out = new ArrayList<>();
        for (String level : LEVELS) {
            List<Problem> ps = data.problems.values().stream().filter(p -> level.equals(p.getDifficulty())).toList();
            int subs = 0;
            long accepted = 0;
            List<Integer> firsts = new ArrayList<>();
            for (Problem p : ps) {
                List<Submission> list = bySlug.getOrDefault(p.getSlug(), List.of());
                subs += list.size();
                accepted += list.stream().filter(Submission::accepted).count();
                Integer f = toFirstAc.get(p.getSlug());
                if (f != null) firsts.add(f);
            }
            Long total = null;
            try {
                String t = data.info.get("LeetCode Total (" + level + ")");
                if (t != null && !t.isBlank()) total = Long.parseLong(t.trim());
            } catch (NumberFormatException ignored) {
                // hand-edited Info sheet
            }
            out.add(new DifficultyStat(level, ps.size(), total, subs,
                    firsts.isEmpty() ? null : firsts.stream().filter(n -> n == 1).count() / (double) firsts.size(),
                    rate(accepted, subs),
                    firsts.isEmpty() ? null : firsts.stream().mapToInt(Integer::intValue).average().orElse(0)));
        }
        return out;
    }

    private List<HourStat> hours() {
        int[] subs = new int[6], acc = new int[6];
        for (Submission s : data.submissions.values()) {
            int b = Instant.ofEpochSecond(s.date()).atZone(zone).getHour() / 4;
            subs[b]++;
            if (s.accepted()) acc[b]++;
        }
        List<HourStat> out = new ArrayList<>();
        for (int b = 0; b < 6; b++) {
            out.add(new HourStat(String.format("%02d–%02d", b * 4, b * 4 + 4), subs[b], acc[b], rate(acc[b], subs[b])));
        }
        return out;
    }

    private List<DayActivity> activity() {
        LocalDate today = day(now);
        LocalDate start = today.minusWeeks(HEATMAP_WEEKS - 1).with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        Map<LocalDate, int[]> days = new TreeMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) days.put(d, new int[4]);
        Function<Long, int[]> slot = ts -> days.get(day(ts));
        for (Submission s : data.submissions.values()) {
            int[] c = slot.apply(s.date());
            if (c == null) continue;
            c[0]++;
            if (s.accepted()) c[1]++;
        }
        for (RevisionEntry e : data.log) {
            int[] c = slot.apply(e.date());
            if (c != null) c[2]++;
        }
        for (Problem p : data.problems.values()) {
            int[] c = p.getFirstSolved() > 0 ? slot.apply(p.getFirstSolved()) : null;
            if (c != null) c[3]++;
        }
        boolean history = !data.submissions.isEmpty();
        List<DayActivity> out = new ArrayList<>();
        days.forEach((d, c) -> out.add(new DayActivity(d.toString(), c[0], c[1], c[2], c[3], (history ? c[0] : c[3]) + c[2])));
        return out;
    }

    private List<LanguageStat> languages() {
        Map<String, int[]> m = new HashMap<>();
        for (Submission s : data.submissions.values()) {
            int[] c = m.computeIfAbsent(s.language().isEmpty() ? "Unknown" : s.language(), k -> new int[2]);
            c[0]++;
            if (s.accepted()) c[1]++;
        }
        return m.entrySet().stream().sorted((a, b) -> b.getValue()[0] - a.getValue()[0])
                .map(e -> new LanguageStat(e.getKey(), e.getValue()[0], rate(e.getValue()[1], e.getValue()[0]))).toList();
    }

    private Retries retries() {
        int quick = 0, quickOk = 0, patient = 0, patientOk = 0;
        for (List<Submission> list : bySlug.values()) {
            for (int i = 1; i < list.size(); i++) {
                Submission prev = list.get(i - 1), next = list.get(i);
                if (prev.accepted()) continue;
                long gap = next.date() - prev.date();
                if (gap <= QUICK_RETRY_SECONDS) {
                    quick++;
                    if (next.accepted()) quickOk++;
                } else if (gap <= RETRY_WINDOW_SECONDS) {
                    patient++;
                    if (next.accepted()) patientOk++;
                }
            }
        }
        return new Retries(quick, quickOk, rate(quickOk, quick), patient, patientOk, rate(patientOk, patient));
    }

    private List<Struggle> struggles() {
        return data.problems.values().stream()
                .filter(p -> attempts.containsKey(p.getSlug()) && attempts.get(p.getSlug()).failed() >= 2)
                .sorted(Comparator.comparingInt((Problem p) -> attempts.get(p.getSlug()).failed()).reversed())
                .limit(10)
                .map(p -> {
                    Attempts a = attempts.get(p.getSlug());
                    return new Struggle(p.getSlug(), p.getId(), p.getTitle(), p.getDifficulty(), p.getTags(),
                            a.submissions(), a.failed(), mainError(bySlug.get(p.getSlug())));
                }).toList();
    }

    private List<Unsolved> unsolved() {
        List<Unsolved> out = new ArrayList<>();
        bySlug.forEach((slug, list) -> {
            if (data.problems.containsKey(slug) || list.stream().anyMatch(Submission::accepted)) return;
            Submission last = list.getLast();
            out.add(new Unsolved(slug, last.title().isEmpty() ? slug : last.title(), list.size(), last.date(), mainError(list)));
        });
        out.sort(Comparator.comparingLong(Unsolved::lastTried).reversed());
        return out.size() > 15 ? out.subList(0, 15) : out;
    }

    // ================================================================== tips

    private List<Tip> tips(Overview o, List<TopicStat> topics, List<String> weakest, List<String> strongest,
                           List<Count> mistakes, List<HourStat> hours, Retries r, List<Unsolved> unsolved,
                           List<DifficultyStat> difficulties) {
        List<Tip> tips = new ArrayList<>();
        Map<String, TopicStat> byName = topics.stream().collect(Collectors.toMap(TopicStat::name, t -> t, (a, b) -> a));
        Set<String> mentioned = new HashSet<>();

        if (o.submissions() == 0 && o.solved() > 0) {
            tips.add(new Tip("info", "Unlock mistake analysis",
                    "Add LEETCODE_SESSION to .env and sync. LeetVise will then break down wrong answers, TLEs and retries per topic.", null));
        }

        if (!weakest.isEmpty()) {
            TopicStat w = byName.get(weakest.getFirst());
            mentioned.add(w.name());
            tips.add(new Tip("focus", "Focus on " + w.name(),
                    "Your weakest topic (score " + w.score() + "): " + String.join(" · ", w.reasons())
                            + ". Filter the table by it and revise its " + plural(w.solved(), "problem") + ".", w.name()));
        }

        int failed = o.failed();
        if (failed >= 5 && !mistakes.isEmpty()) {
            Count top = mistakes.getFirst();
            tips.add(new Tip("warn", pct(top.count() / (double) failed) + " of your failed submissions are " + top.name(),
                    advice(top.name()), null));
            mistakes.stream().filter(c -> c.name().equals(CE) && c != top && c.count() >= 5 && c.count() >= failed * 0.15)
                    .findFirst().ifPresent(c -> tips.add(new Tip("warn", plural(c.count(), "compile error") + " – free points lost",
                            advice(CE), null)));
        }

        topics.stream().filter(t -> t.timeLimit() >= 3 && !mentioned.contains(t.name()))
                .max(Comparator.comparingDouble(t -> t.timeLimit() / (double) t.solved())).ifPresent(t -> {
                    mentioned.add(t.name());
                    tips.add(new Tip("warn", "TLE hotspot: " + t.name(), t.timeLimit() + " Time Limit Exceeded across "
                            + plural(t.solved(), "problem") + ". Read the constraints before coding – n ≤ 10⁵ usually needs O(n log n) or better.", t.name()));
                });

        topics.stream().filter(t -> t.solved() >= minProblems && t.failed() >= 4 && !mentioned.contains(t.name()))
                .max(Comparator.comparingDouble(t -> t.failed() / (double) t.solved())).ifPresent(t -> {
                    double perProblem = t.failed() / (double) t.solved();
                    if (perProblem < 1) return;
                    mentioned.add(t.name());
                    tips.add(new Tip("warn", "Error-prone: " + t.name(), String.format(Locale.ROOT,
                            "%.1f failed submissions per problem (%s). Dry-run one example by hand before you submit.",
                            perProblem, errorsOnly(t)), t.name()));
                });

        if (r.quick() >= 5 && r.quickRate() != null && r.patientRate() != null && r.quickRate() + 0.1 < r.patientRate()) {
            tips.add(new Tip("warn", "Slow down after a failed submit",
                    "You resubmitted within 2 minutes " + r.quick() + " times and only " + pct(r.quickRate())
                            + " passed. After a pause, " + pct(r.patientRate()) + " passed. Find the bug before resubmitting.", null));
        }

        List<HourStat> busy = hours.stream().filter(h -> h.submissions() >= 15).toList();
        if (busy.size() >= 2) {
            HourStat best = busy.stream().max(Comparator.comparingDouble(HourStat::acceptanceRate)).get();
            HourStat worst = busy.stream().min(Comparator.comparingDouble(HourStat::acceptanceRate)).get();
            if (best.acceptanceRate() - worst.acceptanceRate() >= 0.12) {
                tips.add(new Tip("info", "You're sharpest at " + best.label(),
                        pct(best.acceptanceRate()) + " of submissions pass at " + best.label() + " vs "
                                + pct(worst.acceptanceRate()) + " at " + worst.label() + ". Save hard problems for your best hours.", null));
            }
        }

        if (o.due() >= 10) {
            tips.add(new Tip("warn", o.due() + " problems are due for revision",
                    "Five a day clears the backlog in about " + (o.due() + 4) / 5 + " days. Turn on “Due” and use Pick random.", null));
        }

        topics.stream().filter(t -> t.solved() >= minProblems && t.daysSinceTouched() != null && t.daysSinceTouched() >= 45
                        && !mentioned.contains(t.name()))
                .max(Comparator.comparingInt(TopicStat::solved)).ifPresent(t -> {
                    mentioned.add(t.name());
                    tips.add(new Tip("warn", t.name() + " is fading", "You haven't touched its " + plural(t.solved(), "problem")
                            + " for " + t.daysSinceTouched() + " days (median). Revise two or three this week.", t.name()));
                });

        if (o.solved() >= 25) {
            DifficultyStat hard = difficulties.get(2), medium = difficulties.get(1);
            double hardShare = hard.solved() / (double) o.solved(), mediumShare = medium.solved() / (double) o.solved();
            if (mediumShare < 0.3) {
                tips.add(new Tip("info", "Move up to Medium", "Only " + pct(mediumShare)
                        + " of your solved problems are Medium – that's where most interview questions sit.", null));
            } else if (hardShare < 0.08) {
                tips.add(new Tip("info", "Try more Hard problems", "Only " + pct(hardShare)
                        + " of your solved problems are Hard. One a week builds depth.", null));
            }
        }

        if (o.revisions() >= 8 && o.recallRate() != null) {
            if (o.recallRate() < 0.5) {
                tips.add(new Tip("warn", "Low recall when revising", "Only " + pct(o.recallRate())
                        + " of revisions went Good/Easy. Write the key idea in Notes right after you solve – it makes the next revision stick.", null));
            } else if (o.recallRate() >= 0.8) {
                tips.add(new Tip("good", "Revisions are sticking", pct(o.recallRate())
                        + " of your revisions went Good/Easy. Mix in harder problems to keep it challenging.", null));
            }
        }

        if (!unsolved.isEmpty()) {
            tips.add(new Tip("info", plural(unsolved.size(), "problem") + " tried but never solved",
                    "Unfinished problems point straight at what you don't know yet. Finish one: " + unsolved.getFirst().title() + ".", null));
        }

        if (o.submissions() > 0 && o.activeDays30() > 0 && o.activeDays30() < 8) {
            tips.add(new Tip("info", "Practice a little every day", "You were active on " + o.activeDays30()
                    + " of the last 30 days. Short daily sessions beat weekend marathons.", null));
        }

        if (!strongest.isEmpty()) {
            TopicStat s = byName.get(strongest.getFirst());
            tips.add(new Tip("good", "Strong in " + s.name(), "Score " + s.score() + ": "
                    + String.join(" · ", s.reasons().subList(0, Math.min(2, s.reasons().size())))
                    + ". Keep it fresh with a revision every few weeks.", s.name()));
        }
        return tips;
    }

    private static String errorsOnly(TopicStat t) {
        return t.reasons().stream().filter(s -> s.contains("wrong answer") || s.contains("TLE") || s.contains("error"))
                .findFirst().orElse(plural(t.failed(), "failure"));
    }

    private static String advice(String error) {
        return switch (error) {
            case WA -> "Before submitting, dry-run the edge cases: empty input, one element, duplicates, negatives, overflow.";
            case TLE -> "Estimate the complexity from the constraints before coding – n ≤ 10⁵ usually needs O(n log n) or better.";
            case RE -> "Check index bounds, null/empty inputs, integer overflow and recursion depth.";
            case CE -> "Run the code once before you submit – compile errors cost an attempt and teach nothing.";
            case MLE -> "Avoid copying arrays in recursion and big memo tables – try in-place updates or rolling arrays.";
            default -> "Read the failing test case carefully and trace your code on it by hand.";
        };
    }

    // ================================================================== helpers

    static String errorType(String status) {
        if (status == null) return OTHER;
        for (String t : ERROR_TYPES) if (t.equalsIgnoreCase(status.trim())) return t;
        return OTHER;
    }

    private static String mainError(List<Submission> list) {
        return list.stream().filter(s -> !s.accepted()).collect(Collectors.groupingBy(s -> errorType(s.status()), Collectors.counting()))
                .entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse("");
    }

    private boolean isDue(Problem p) {
        if (p.getNextReview() > 0) return p.getNextReview() <= now;
        return p.getLastSolved() > 0 && now - p.getLastSolved() > 7 * DAY;
    }

    private static Double recall(List<RevisionEntry> log) {
        if (log.isEmpty()) return null;
        return log.stream().filter(InsightsCalculator::wentWell).count() / (double) log.size();
    }

    private static boolean wentWell(RevisionEntry e) {
        return "good".equalsIgnoreCase(e.rating()) || "easy".equalsIgnoreCase(e.rating());
    }

    /** part/whole, nudged toward {@code prior} as if {@code k} extra samples sat exactly at it. */
    private static double blend(long part, long whole, Double prior, int k) {
        double p = prior == null ? 0.6 : prior;
        return (part + k * p) / (whole + k);
    }

    private static List<String> tags(Problem p) {
        if (p.getTags() == null || p.getTags().isBlank()) return List.of();
        return Arrays.stream(p.getTags().split(",")).map(String::trim).filter(s -> !s.isEmpty()).distinct().toList();
    }

    private static Integer median(List<Integer> xs) {
        if (xs.isEmpty()) return null;
        List<Integer> s = xs.stream().sorted().toList();
        return s.get(s.size() / 2);
    }

    private static Double rate(long part, long whole) {
        return whole == 0 ? null : part / (double) whole;
    }

    private LocalDate day(long epoch) {
        return Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate();
    }

    static String pct(double x) {
        return Math.round(x * 100) + "%";
    }

    private static String plural(int n, String word) {
        return n + " " + word + (n == 1 ? "" : "s");
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
