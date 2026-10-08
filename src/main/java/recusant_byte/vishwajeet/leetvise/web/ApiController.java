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

import recusant_byte.vishwajeet.leetvise.excel.ExcelStore;
import recusant_byte.vishwajeet.leetvise.insights.Insights;
import recusant_byte.vishwajeet.leetvise.service.ProblemService;
import recusant_byte.vishwajeet.leetvise.service.ProblemService.State;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Files;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    public record SyncRequest(boolean full) { }
    public record ReviseRequest(@NotBlank String slug, @NotBlank String rating) { }
    public record NoteRequest(@NotBlank String slug, String notes) { }
    public record AddRequest(@NotBlank(message = "Paste a LeetCode URL or a slug like two-sum") String slug) { }
    public record UsernameRequest(@NotBlank(message = "Enter your LeetCode username") String username) { }

    private final ProblemService service;
    private final ExcelStore store;
    private final DesktopOpener opener;

    public ApiController(ProblemService service, ExcelStore store, DesktopOpener opener) {
        this.service = service;
        this.store = store;
        this.opener = opener;
    }

    @GetMapping("/state")
    public State state() {
        return service.state(null);
    }

    @GetMapping("/insights")
    public Insights insights() {
        return service.insights();
    }

    @PostMapping("/username")
    public State username(@Valid @RequestBody UsernameRequest req) {
        return service.setUsername(req.username());
    }

    @PostMapping("/sync")
    public State sync(@RequestBody(required = false) SyncRequest req) {
        return service.sync(req != null && req.full());
    }

    @PostMapping("/revise")
    public State revise(@Valid @RequestBody ReviseRequest req) {
        return service.revise(req.slug(), req.rating());
    }

    @PostMapping("/note")
    public State note(@Valid @RequestBody NoteRequest req) {
        return service.saveNote(req.slug(), req.notes());
    }

    @PostMapping("/add")
    public State add(@Valid @RequestBody AddRequest req) {
        return service.add(req.slug());
    }

    @PostMapping("/open-excel")
    public Map<String, String> openExcel() {
        if (!Files.exists(store.file())) store.save();
        opener.open(store.file().toString());
        return Map.of("message", "Opening " + store.file().getFileName());
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
