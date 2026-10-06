package com.leetvise.web;

import com.leetvise.excel.ExcelStore;
import com.leetvise.service.ProblemService;
import com.leetvise.service.ProblemService.State;
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
