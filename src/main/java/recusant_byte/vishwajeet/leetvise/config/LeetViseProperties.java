/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Settings under {@code leetvise.*}. They are filled from the {@code .env} file in the project folder
 * (or real environment variables) – see application.properties for the mapping.
 *
 * @param username    LeetCode username – optional: found from the session cookie, or entered in the UI
 * @param session     LEETCODE_SESSION cookie – optional, unlocks the full solved list and submission insights
 * @param csrftoken   csrftoken cookie – optional, sent along with the session
 * @param excel       the workbook that stores everything (relative paths resolve against the project folder)
 * @param openBrowser open the UI in the browser on startup
 */
@ConfigurationProperties("leetvise")
public record LeetViseProperties(
        @DefaultValue("") String username,
        @DefaultValue("") String session,
        @DefaultValue("") String csrftoken,
        Path excel,
        @DefaultValue("true") boolean openBrowser) {

    public LeetViseProperties {
        username = clean(username);
        session = clean(session);
        csrftoken = clean(csrftoken);
        String home = System.getProperty("user.home");
        if (excel == null || excel.toString().isBlank()) excel = Path.of("data", "leetvise.xlsx");
        else if (excel.toString().startsWith("~")) excel = Path.of(home + excel.toString().substring(1));
        excel = excel.toAbsolutePath();
    }

    public boolean hasSession() {
        return !session.isEmpty();
    }

    /** Trims and drops surrounding quotes, so both {@code KEY=abc} and {@code KEY="abc"} work in .env. */
    private static String clean(String s) {
        if (s == null) return "";
        s = s.trim();
        if (s.length() >= 2 && (s.startsWith("\"") && s.endsWith("\"") || s.startsWith("'") && s.endsWith("'"))) {
            s = s.substring(1, s.length() - 1).trim();
        }
        return s;
    }
}

// LeetVise · by Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
