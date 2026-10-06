package com.leetvise.web;

import com.leetvise.config.LeetViseProperties;
import com.leetvise.excel.ExcelStore;
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

    public DesktopOpener(LeetViseProperties props, ExcelStore store) {
        this.props = props;
        this.store = store;
    }

    @EventListener
    public void onReady(ApplicationReadyEvent event) {
        String port = event.getApplicationContext().getEnvironment().getProperty("local.server.port", "8080");
        String url = "http://localhost:" + port;
        log.info("""

                  LeetVise is running  ->  {}
                  User:        {}
                  Excel file:  {}
                  Full sync:   {}
                  Press Ctrl+C to stop.
                """, url, props.username(), store.file(),
                props.hasSession() ? "ON" : "OFF (no LEETCODE_SESSION set – only ~20 recent solves)");
        if (props.openBrowser()) open(url);
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
