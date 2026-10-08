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

package recusant_byte.vishwajeet.leetvise.web;

import recusant_byte.vishwajeet.leetvise.config.LeetViseProperties;
import recusant_byte.vishwajeet.leetvise.excel.ExcelStore;
import recusant_byte.vishwajeet.leetvise.service.ProblemService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Locale;

/** Opens files/URLs with the OS default app, and prints a start-up summary. */
@Component
public class DesktopOpener {

    private static final Logger log = LoggerFactory.getLogger(DesktopOpener.class);

    private final LeetViseProperties props;
    private final ExcelStore store;
    private final ProblemService service;

    public DesktopOpener(LeetViseProperties props, ExcelStore store, ProblemService service) {
        this.props = props;
        this.store = store;
        this.service = service;
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        String port = event.getApplicationContext().getEnvironment().getProperty("local.server.port", "8080");
        String url = "http://localhost:" + port;
        log.info("""

                  LeetVise by Vishwajeet Pratap Singh  ·  https://vishwajeet.me
                  LeetVise is running  ->  {}
                  User:        {}
                  Excel file:  {}
                  Full sync:   {}
                  Press Ctrl+C to stop.
                """, url, username(), store.file(),
                props.hasSession() ? "ON" : "OFF (no LEETCODE_SESSION in .env – only ~20 recent solves, no mistake insights)");
        if (props.openBrowser()) open(url);
    }

    private String username() {
        try {
            String u = service.username();
            return u.isEmpty() ? "not set yet – enter it in the app or set LEETCODE_USERNAME in .env" : "@" + u;
        } catch (RuntimeException e) {
            return "? (" + e.getMessage() + ")";
        }
    }

    public void open(String target) {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        String[] cmd = os.contains("mac") ? new String[]{"open", target}
                : os.contains("win") ? new String[]{"cmd", "/c", "start", "", target}
                : new String[]{"xdg-open", target};
        try {
            new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            log.warn("Could not open {}: {}", target, e.getMessage());
        }
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
