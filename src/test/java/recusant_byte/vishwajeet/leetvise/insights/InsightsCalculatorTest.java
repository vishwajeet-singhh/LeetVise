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

import recusant_byte.vishwajeet.leetvise.excel.ExcelStore;
import recusant_byte.vishwajeet.leetvise.insights.Insights.TopicStat;
import recusant_byte.vishwajeet.leetvise.model.Problem;
import recusant_byte.vishwajeet.leetvise.model.RevisionEntry;
import recusant_byte.vishwajeet.leetvise.model.Submission;
import org.junit.jupiter.api.Test;

import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class InsightsCalculatorTest {

    static final long NOW = 1_790_000_000L;
    static final long DAY = 86_400;
    final ExcelStore.Data data = new ExcelStore.Data();
    final AtomicLong ids = new AtomicLong(1);

    private Problem problem(String slug, String difficulty, String tags, long lastSolvedDaysAgo) {
        Problem p = new Problem(slug);
        p.setTitle(slug);
        p.setDifficulty(difficulty);
        p.setTags(tags);
        p.recordSolved(NOW - lastSolvedDaysAgo * DAY, NOW - lastSolvedDaysAgo * DAY);
        data.problems.put(slug, p);
        return p;
    }

    private void submit(String slug, String status, long at) {
        long id = ids.getAndIncrement();
        data.submissions.put(id, new Submission(id, at, slug, slug, status, "Java", "", ""));
    }

    private Insights compute() {
        return new InsightsCalculator(data, NOW, ZoneOffset.UTC).compute();
    }

    @Test
    void countsAttemptsMistakesAndRetries() {
        problem("two-sum", "Easy", "Array, Hash Table", 1);
        problem("3sum", "Medium", "Array, Two Pointers", 2);
        problem("climbing-stairs", "Easy", "Array, Dynamic Programming", 3);
        long t = NOW - 10 * DAY;
        submit("two-sum", "Accepted", t);
        submit("3sum", "Wrong Answer", t + 100);
        submit("3sum", "Wrong Answer", t + 110);   // quick retry, failed
        submit("3sum", "Accepted", t + 170);       // quick retry, passed
        submit("climbing-stairs", "Time Limit Exceeded", t + 1000);
        submit("climbing-stairs", "Accepted", t + 1000 + 3600); // patient retry, passed
        submit("never-solved", "Wrong Answer", t + 5000);

        Insights in = compute();

        assertThat(in.hasSubmissions()).isTrue();
        assertThat(in.overview().submissions()).isEqualTo(7);
        assertThat(in.overview().accepted()).isEqualTo(3);
        assertThat(in.overview().failed()).isEqualTo(4);
        assertThat(in.overview().firstTryRate()).isCloseTo(1 / 3.0, within(1e-9));
        assertThat(in.overview().avgAttempts()).isCloseTo(2.0, within(1e-9)); // (1 + 3 + 2) / 3

        assertThat(in.mistakes()).extracting(Insights.Count::name).containsExactly("Wrong Answer", "Time Limit Exceeded");
        assertThat(in.mistakes().getFirst().count()).isEqualTo(3);

        assertThat(in.retries().quick()).isEqualTo(2);
        assertThat(in.retries().quickAccepted()).isEqualTo(1);
        assertThat(in.retries().patient()).isEqualTo(1);
        assertThat(in.retries().patientAccepted()).isEqualTo(1);

        assertThat(in.attempts().get("3sum")).isEqualTo(new Insights.Attempts(3, 2, false));
        assertThat(in.attempts().get("two-sum").firstTry()).isTrue();
        assertThat(in.unsolved()).extracting(Insights.Unsolved::slug).containsExactly("never-solved");
        assertThat(in.struggles()).extracting(Insights.Struggle::slug).containsExactly("3sum");

        TopicStat array = in.topics().stream().filter(x -> x.name().equals("Array")).findFirst().orElseThrow();
        assertThat(array.solved()).isEqualTo(3);
        assertThat(array.submissions()).isEqualTo(6);
        assertThat(array.wrongAnswer()).isEqualTo(2);
        assertThat(array.timeLimit()).isEqualTo(1);
        assertThat(in.topics()).extracting(TopicStat::name).containsExactly("Array"); // the others have < 3 problems
    }

    @Test
    void separatesStrongFromWeakTopics() {
        for (int i = 0; i < 4; i++) {
            String good = "good-" + i, bad = "bad-" + i;
            problem(good, "Medium", "Two Pointers", 2);
            problem(bad, "Medium", "Graph", 120);
            submit(good, "Accepted", NOW - 5 * DAY);
            for (int k = 0; k < 4; k++) submit(bad, "Time Limit Exceeded", NOW - 130 * DAY + k * 600);
            submit(bad, "Accepted", NOW - 120 * DAY);
            data.log.add(new RevisionEntry(NOW - DAY, good, "", good, "Medium", "easy"));
            data.log.add(new RevisionEntry(NOW - 100 * DAY, bad, "", bad, "Medium", "again"));
        }

        Insights in = compute();

        TopicStat strong = in.topics().getFirst(), weak = in.topics().get(1);
        assertThat(strong.name()).isEqualTo("Two Pointers");
        assertThat(strong.level()).isEqualTo("strong");
        assertThat(weak.name()).isEqualTo("Graph");
        assertThat(weak.level()).isEqualTo("weak");
        assertThat(weak.timeLimit()).isEqualTo(16);
        assertThat(weak.reasons()).anyMatch(r -> r.contains("16 TLE")).anyMatch(r -> r.contains("untouched for 120 days"));
        assertThat(in.strongest()).containsExactly("Two Pointers");
        assertThat(in.weakest()).containsExactly("Graph");
        assertThat(in.tips()).extracting(Insights.Tip::title)
                .contains("Focus on Graph", "Strong in Two Pointers")
                .anyMatch(title -> title.contains("Time Limit Exceeded"));
    }

    @Test
    void onlyAnalysesTopicsHoldingAnEighthOfYourSolves() {
        for (int i = 0; i < 40; i++) problem("p" + i, "Medium", i < 5 ? "Array, Graph" : "Array", 1);
        Insights in = compute();
        assertThat(in.minProblems()).isEqualTo(5); // 12.5% of 40
        assertThat(in.topics()).extracting(TopicStat::name).containsExactlyInAnyOrder("Array", "Graph");

        problem("p40", "Medium", "Array", 1); // 41 solved -> 12.5% is 5.1 -> Graph's 5 is no longer enough
        Insights after = compute();
        assertThat(after.minProblems()).isEqualTo(6);
        assertThat(after.topics()).extracting(TopicStat::name).containsExactly("Array");
        assertThat(after.strongest()).doesNotContain("Graph");
        assertThat(after.weakest()).doesNotContain("Graph");
    }

    @Test
    void worksWithoutSubmissionHistory() {
        problem("two-sum", "Easy", "Array", 3);
        Insights in = compute();
        assertThat(in.hasSubmissions()).isFalse();
        assertThat(in.overview().firstTryRate()).isNull();
        assertThat(in.activity()).isNotEmpty().anyMatch(d -> d.firstSolves() == 1 && d.total() == 1);
        assertThat(in.tips()).extracting(Insights.Tip::title).contains("Unlock mistake analysis");
    }

    @Test
    void mapsLeetCodeStatuses() {
        assertThat(InsightsCalculator.errorType("Wrong Answer")).isEqualTo("Wrong Answer");
        assertThat(InsightsCalculator.errorType("time limit exceeded")).isEqualTo("Time Limit Exceeded");
        assertThat(InsightsCalculator.errorType("Internal Error")).isEqualTo("Other");
        assertThat(InsightsCalculator.errorType(null)).isEqualTo("Other");
    }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
