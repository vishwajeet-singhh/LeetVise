package com.leetvise.leetcode;

import com.leetvise.config.LeetViseProperties;
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
 *   <li>With LEETCODE_SESSION: every solved problem + full submission history (for dates).</li>
 * </ul>
 */
@Component
public class LeetCodeClient {

    /** A solved problem as reported by LeetCode. Times are epoch seconds (0 = unknown). */
    public record Solved(String slug, String id, String title, String difficulty, String tags, long firstTs, long lastTs) { }

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
                .defaultHeader(HttpHeaders.REFERER, "https://leetcode.com/")
                .defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (Macintosh; Intel Mac OS X) LeetVise/1.0")
                .build();
    }

    public boolean authed() {
        return props.hasSession();
    }

    // ------------------------------------------------------------------ public data

    /** Solved counts from the public profile: {All, Easy, Medium, Hard}. */
    public Map<String, Long> profileCounts() {
        Object r = graphql("query($u:String!){ matchedUser(username:$u){ submitStatsGlobal{ acSubmissionNum{ difficulty count } } } }",
                Map.of("u", props.username()), false);
        Map<String, Long> out = new LinkedHashMap<>();
        for (Object o : list(get(r, "data", "matchedUser", "submitStatsGlobal", "acSubmissionNum"))) {
            out.put(str(get(o, "difficulty")), num(get(o, "count")));
        }
        if (out.isEmpty()) throw new IllegalStateException("LeetCode user '" + props.username() + "' not found");
        return out;
    }

    /** Most recent accepted submissions (LeetCode caps this at ~20 publicly). */
    public List<Solved> recentAccepted() {
        Object r = graphql("query($u:String!,$n:Int!){ recentAcSubmissionList(username:$u, limit:$n){ title titleSlug timestamp } }",
                Map.of("u", props.username(), "n", 50), false);
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

    /**
     * Walks your submission history (newest first) and returns {first, last} accepted time per problem slug.
     * Stops once it reaches submissions older than {@code stopBefore} (so later syncs are quick).
     */
    public Map<String, long[]> acceptedTimes(long stopBefore) {
        Map<String, long[]> out = new HashMap<>();
        String lastKey = "";
        int retries = 0;
        for (int page = 0, offset = 0; page < 400; page++, offset += 20) {
            Map<String, Object> r;
            try {
                String uri = "/api/submissions/?offset=" + offset + "&limit=20&lastkey=" + lastKey;
                r = http.get().uri(uri).headers(this::auth).retrieve().body(JSON);
            } catch (HttpClientErrorException e) {
                if (e.getStatusCode().value() == 429 && retries++ < 5) {
                    sleep(3000);
                    page--;
                    offset -= 20;
                    continue;
                }
                int code = e.getStatusCode().value();
                throw new IllegalStateException("Submission history returned HTTP " + code
                        + (code == 401 || code == 403 ? " – session cookie expired? Copy a fresh LEETCODE_SESSION." : ""));
            }
            boolean reachedOld = false;
            for (Object o : list(get(r, "submissions_dump"))) {
                long ts = num(get(o, "timestamp"));
                if (ts < stopBefore) reachedOld = true;
                if (!"Accepted".equals(get(o, "status_display"))) continue;
                long[] t = out.computeIfAbsent(str(get(o, "title_slug")), k -> new long[]{Long.MAX_VALUE, 0});
                t[0] = Math.min(t[0], ts);
                t[1] = Math.max(t[1], ts);
            }
            if (reachedOld || !Boolean.TRUE.equals(get(r, "has_next"))) break;
            lastKey = Objects.toString(get(r, "last_key"), "");
            sleep(350); // be polite to LeetCode
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
