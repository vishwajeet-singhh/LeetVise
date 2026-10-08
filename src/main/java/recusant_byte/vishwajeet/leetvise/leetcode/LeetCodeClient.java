/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.leetcode;

import recusant_byte.vishwajeet.leetvise.config.LeetViseProperties;
import recusant_byte.vishwajeet.leetvise.model.Submission;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Talks to leetcode.com.
 * <ul>
 *   <li>Without a session cookie: public profile + ~20 most recent accepted submissions.</li>
 *   <li>With LEETCODE_SESSION: every solved problem + full submission history (dates, wrong answers, TLEs…).</li>
 * </ul>
 */
@Component
public class LeetCodeClient {

    /** A solved problem as reported by LeetCode. Times are epoch seconds (0 = unknown). */
    public record Solved(String slug, String id, String title, String difficulty, String tags, long firstTs, long lastTs) { }

    /** Public profile numbers, keyed by "All" / "Easy" / "Medium" / "Hard". */
    public record Profile(Map<String, Long> solved, Map<String, Long> totals) { }

    private static final ParameterizedTypeReference<Map<String, Object>> JSON = new ParameterizedTypeReference<>() { };

    private final LeetViseProperties props;
    private final RestClient http;

    public LeetCodeClient(LeetViseProperties props, RestClient.Builder builder) {
        this.props = props;
        SimpleClientHttpRequestFactory rf = new SimpleClientHttpRequestFactory();
        rf.setConnectTimeout(15_000);
        rf.setReadTimeout(30_000);
        this.http = builder
                .baseUrl("https://leetcode.com")
                .requestFactory(rf)
                // without an explicit JSON Accept header the submissions API answers 403
                .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.REFERER, "https://leetcode.com/")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X) LeetVise/1.0")
                .build();
    }

    public boolean authed() {
        return props.hasSession();
    }

    // ------------------------------------------------------------------ public data

    /** Solved counts from the public profile, plus how many problems LeetCode has per difficulty. */
    public Profile profile(String username) {
        Object r = graphql("query($u:String!){ allQuestionsCount{ difficulty count } "
                        + "matchedUser(username:$u){ submitStatsGlobal{ acSubmissionNum{ difficulty count } } } }",
                Map.of("u", username), false);
        if (get(r, "data", "matchedUser") == null) {
            throw new IllegalArgumentException("LeetCode user '" + username + "' not found");
        }
        return new Profile(counts(get(r, "data", "matchedUser", "submitStatsGlobal", "acSubmissionNum")),
                counts(get(r, "data", "allQuestionsCount")));
    }

    /** Most recent accepted submissions (LeetCode caps this at ~20 publicly). */
    public List<Solved> recentAccepted(String username) {
        Object r = graphql("query($u:String!,$n:Int!){ recentAcSubmissionList(username:$u, limit:$n){ title titleSlug timestamp } }",
                Map.of("u", username, "n", 50), false);
        List<Solved> out = new ArrayList<>();
        for (Object o : list(get(r, "data", "recentAcSubmissionList"))) {
            long ts = num(get(o, "timestamp"));
            out.add(new Solved(str(get(o, "titleSlug")), "", str(get(o, "title")), "", "", ts, ts));
        }
        return out;
    }

    /** Number, title, difficulty and tags for one problem. */
    public Solved questionDetails(String slug) {
        Object r = graphql("query($s:String!){ question(titleSlug:$s){ questionFrontendId title titleSlug difficulty topicTags{ name } } }",
                Map.of("s", slug), false);
        Object q = get(r, "data", "question");
        if (q == null) throw new IllegalArgumentException("No LeetCode problem called '" + slug + "'");
        return meta(q);
    }

    // ------------------------------------------------------------------ needs session cookie

    /** Every problem the logged-in user has solved (status = AC). */
    public List<Solved> allSolved() {
        String q = "query($skip:Int!,$limit:Int!,$f:QuestionListFilterInput){ questionList(categorySlug:\"\", limit:$limit, skip:$skip, filters:$f)"
                + "{ totalNum data{ questionFrontendId title titleSlug difficulty status topicTags{ name } } } }";
        List<Solved> out = new ArrayList<>();
        for (int skip = 0; skip < 5000; skip += 100) {
            Object r = graphql(q, Map.of("skip", skip, "limit", 100, "f", Map.of("status", "AC")), true);
            List<Object> data = list(get(r, "data", "questionList", "data"));
            for (Object o : data) {
                if ("ac".equalsIgnoreCase(str(get(o, "status")))) out.add(meta(o));
            }
            if (data.size() < 100) break;
        }
        return out;
    }

    /** The username the session cookie belongs to, or "" if the cookie is missing or expired. */
    public String signedInUsername() {
        if (!props.hasSession()) return "";
        Object r = graphql("query{ userStatus{ isSignedIn username } }", Map.of(), true);
        return Boolean.TRUE.equals(get(r, "data", "userStatus", "isSignedIn")) ? str(get(r, "data", "userStatus", "username")) : "";
    }

    /**
     * Walks your submission history (newest first) – accepted and failed ones.
     * Stops once it reaches submissions older than {@code stopBefore} (so later syncs are quick).
     * LeetCode serves 20 per page at about one page per second, so the first full walk takes a while.
     */
    public List<Submission> submissions(long stopBefore) {
        List<Submission> out = new ArrayList<>();
        String lastKey = "";
        int retries = 0;
        for (int page = 0, offset = 0; page < 400; page++, offset += 20) {
            Map<String, Object> r;
            try {
                String uri = "/api/submissions/?offset=" + offset + "&limit=20&lastkey=" + lastKey;
                r = http.get().uri(uri).headers(this::auth).retrieve().body(JSON);
            } catch (HttpClientErrorException e) {
                int code = e.getStatusCode().value();
                // LeetCode throttles fast paging with 429 – or with 403 on a page after the first
                boolean throttled = code == 429 || code == 403 && page > 0;
                if (throttled && retries++ < 6) {
                    sleep(2000L * retries);
                    page--;
                    offset -= 20;
                    continue;
                }
                String hint = code != 401 && code != 403 ? ""
                        : props.csrftoken().isEmpty() ? " – set LEETCODE_CSRF (the csrftoken cookie) in .env too"
                        : " – session cookie expired? Copy fresh LEETCODE_SESSION and LEETCODE_CSRF values into .env";
                throw new IllegalStateException("Submission history returned HTTP " + code + hint);
            }
            retries = 0;
            boolean reachedOld = false;
            for (Object o : list(get(r, "submissions_dump"))) {
                long ts = num(get(o, "timestamp"));
                if (ts < stopBefore) reachedOld = true;
                String lang = str(get(o, "lang_name"));
                out.add(new Submission(num(get(o, "id")), ts, str(get(o, "title_slug")), str(get(o, "title")),
                        str(get(o, "status_display")), lang.isEmpty() ? str(get(o, "lang")) : lang,
                        str(get(o, "runtime")), str(get(o, "memory"))));
            }
            if (reachedOld || !Boolean.TRUE.equals(get(r, "has_next"))) break;
            lastKey = Objects.toString(get(r, "last_key"), "");
            sleep(1100); // LeetCode allows roughly one history page per second
        }
        return out;
    }

    // ------------------------------------------------------------------ plumbing

    private Object graphql(String query, Map<String, Object> variables, boolean withAuth) {
        Map<String, Object> r;
        try {
            r = http.post().uri("/graphql/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .headers(h -> { if (withAuth) auth(h); })
                    .body(Map.of("query", query, "variables", variables))
                    .retrieve()
                    .body(JSON);
        } catch (HttpClientErrorException e) {
            throw new IllegalStateException("LeetCode returned HTTP " + e.getStatusCode().value());
        }
        if (r != null && r.get("errors") != null && r.get("data") == null) {
            throw new IllegalStateException("LeetCode error: " + r.get("errors"));
        }
        return r;
    }

    private void auth(HttpHeaders h) {
        if (!props.hasSession()) return;
        String cookie = "LEETCODE_SESSION=" + props.session();
        if (!props.csrftoken().isEmpty()) {
            cookie += "; csrftoken=" + props.csrftoken();
            h.set("x-csrftoken", props.csrftoken());
        }
        h.set(HttpHeaders.COOKIE, cookie);
    }

    private static Solved meta(Object q) {
        StringJoiner tags = new StringJoiner(", ");
        for (Object t : list(get(q, "topicTags"))) tags.add(str(get(t, "name")));
        return new Solved(str(get(q, "titleSlug")), str(get(q, "questionFrontendId")), str(get(q, "title")),
                str(get(q, "difficulty")), tags.toString(), 0, 0);
    }

    private static Map<String, Long> counts(Object rows) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object o : list(rows)) out.put(str(get(o, "difficulty")), num(get(o, "count")));
        return out;
    }

    /** Walk a path through nested JSON maps; null if any step is missing. */
    private static Object get(Object root, String... path) {
        Object cur = root;
        for (String p : path) {
            if (!(cur instanceof Map<?, ?> m)) return null;
            cur = m.get(p);
        }
        return cur;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Object o) {
        return o instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString();
    }

    private static long num(Object o) {
        if (o instanceof Number n) return n.longValue();
        try {
            return o == null ? 0 : Long.parseLong(o.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Sync interrupted");
        }
    }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
