/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.insights;

import java.util.List;
import java.util.Map;

/**
 * Self-check analysis of your LeetCode history, computed by {@link InsightsCalculator}.
 * Rates are 0..1 and {@code null} when there is no data to compute them from.
 *
 * @param hasSubmissions false until a sync with LEETCODE_SESSION has pulled your submission history
 * @param minProblems    problems a topic needs to be analysed at all (12.5% of everything you solved, at least 3);
 *                       smaller topics are left out of {@code topics}, {@code strongest}, {@code weakest} and the tips
 */
public record Insights(
        boolean hasSubmissions,
        int minProblems,
        Overview overview,
        List<TopicStat> topics,
        List<String> strongest,
        List<String> weakest,
        List<Count> mistakes,
        List<DifficultyStat> difficulties,
        List<HourStat> hours,
        List<DayActivity> activity,
        List<LanguageStat> languages,
        Retries retries,
        List<Struggle> struggles,
        List<Unsolved> unsolved,
        List<Tip> tips,
        Map<String, Attempts> attempts) {

    public record Overview(int solved, int submissions, int accepted, int failed, Double acceptanceRate,
                           Double firstTryRate, Double avgAttempts, int revisions, Double recallRate,
                           int activeDays30, int due) { }

    /**
     * How you're doing in one topic.
     *
     * @param score   0..100 mastery (accuracy, revision recall, freshness, difficulty), pulled toward 50 for small topics
     * @param level   "strong", "steady" or "weak"
     * @param reasons short, human explanations of the score
     */
    public record TopicStat(String name, int solved, int easy, int medium, int hard, int submissions, int failed,
                            int wrongAnswer, int timeLimit, int runtimeError, int compileError, int otherErrors,
                            Double firstTryRate, Double acceptanceRate, Double avgAttempts, int revisions,
                            Double recallRate, int due, Integer daysSinceTouched, int score, String level,
                            List<String> reasons) { }

    public record Count(String name, int count) { }

    public record DifficultyStat(String name, int solved, Long total, int submissions, Double firstTryRate,
                                 Double acceptanceRate, Double avgAttempts) { }

    public record HourStat(String label, int submissions, int accepted, Double acceptanceRate) { }

    /** One day of the activity heatmap. {@code date} is yyyy-MM-dd in your time zone. */
    public record DayActivity(String date, int submissions, int accepted, int revisions, int firstSolves, int total) { }

    public record LanguageStat(String name, int submissions, Double acceptanceRate) { }

    /** What happens after a failed submission: resubmitting within 2 minutes ("quick") vs. after a pause. */
    public record Retries(int quick, int quickAccepted, Double quickRate, int patient, int patientAccepted,
                          Double patientRate) { }

    /** A solved problem that took many attempts. */
    public record Struggle(String slug, String id, String title, String difficulty, String tags, int attempts,
                           int failed, String mainError) { }

    /** Attempted on LeetCode but never accepted. */
    public record Unsolved(String slug, String title, int attempts, long lastTried, String mainError) { }

    /**
     * One actionable suggestion.
     *
     * @param kind "focus", "warn", "good" or "info"
     * @param tag  topic the tip is about (the UI links it to the table filter), may be null
     */
    public record Tip(String kind, String title, String detail, String tag) { }

    /** Per-problem submission summary for the table. */
    public record Attempts(int submissions, int failed, boolean firstTry) { }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
