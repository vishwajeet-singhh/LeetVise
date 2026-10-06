package com.leetvise.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.nio.file.Path;

/**
 * Settings under {@code leetvise.*} (application.properties, overridden by config.properties).
 *
 * @param username    LeetCode username
 * @param session     LEETCODE_SESSION cookie – optional, unlocks the full solved list
 * @param csrftoken   csrftoken cookie – optional, sent along with the session
 * @param excel       the workbook that stores everything
 * @param openBrowser open the UI in the browser on startup
 */
@ConfigurationProperties("leetvise")
public record LeetViseProperties(
        @DefaultValue("recusant_byte") String username,
        @DefaultValue("") String session,
        @DefaultValue("") String csrftoken,
        Path excel,
        @DefaultValue("true") boolean openBrowser) {

    public LeetViseProperties {
        session = session == null ? "" : session.trim();
        csrftoken = csrftoken == null ? "" : csrftoken.trim();
        String home = System.getProperty("user.home");
        if (excel == null) excel = Path.of("data", "leetvise.xlsx");
        else if (excel.toString().startsWith("~")) excel = Path.of(home + excel.toString().substring(1));
        excel = excel.toAbsolutePath();
    }

    public boolean hasSession() {
        return !session.isEmpty();
    }
}
