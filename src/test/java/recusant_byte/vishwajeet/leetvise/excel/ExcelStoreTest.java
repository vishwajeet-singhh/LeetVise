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

package recusant_byte.vishwajeet.leetvise.excel;

import recusant_byte.vishwajeet.leetvise.config.LeetViseProperties;
import recusant_byte.vishwajeet.leetvise.model.Problem;
import recusant_byte.vishwajeet.leetvise.model.RevisionEntry;
import recusant_byte.vishwajeet.leetvise.model.Submission;
import org.apache.poi.ss.usermodel.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class ExcelStoreTest {

    @TempDir
    Path dir;

    private ExcelStore store(Path file) {
        return new ExcelStore(new LeetViseProperties("me", "", "", file, false));
    }

    @Test
    void roundTripsProblemsLogAndInfo() {
        Path file = dir.resolve("t.xlsx");
        ExcelStore a = store(file);
        Problem p = new Problem("pascals-triangle");
        p.setId("118");
        p.setTitle("Pascal's Triangle");
        p.setDifficulty("Easy");
        p.setTags("Array, Dynamic Programming");
        p.setNotes("row[i][j] = row[i-1][j-1] + row[i-1][j] <edge> & \"quotes\"");
        p.setLastSolved(1_790_601_240L);
        p.setFirstSolved(1_790_601_240L);
        p.setTimesRevised(2);
        p.setIntervalDays(14);
        p.setConfidence(3);
        a.data().problems.put(p.getSlug(), p);
        a.data().log.add(new RevisionEntry(1_790_601_300L, p.getSlug(), "118", p.getTitle(), "Easy", "good"));
        a.data().info.put("Last Sync", "2026-09-28 20:56");
        a.data().submissions.put(1_234_567_890L, new Submission(1_234_567_890L, 1_790_601_000L, p.getSlug(), p.getTitle(),
                "Wrong Answer", "Java", "N/A", "N/A"));
        a.save();

        ExcelStore.Data d = store(file).data();
        Problem q = d.problems.get("pascals-triangle");
        assertThat(q.getId()).isEqualTo("118");
        assertThat(q.getTitle()).isEqualTo("Pascal's Triangle");
        assertThat(q.getNotes()).isEqualTo(p.getNotes());
        assertThat(q.getLastSolved()).isEqualTo(1_790_601_240L);
        assertThat(q.getTimesRevised()).isEqualTo(2);
        assertThat(q.getIntervalDays()).isEqualTo(14);
        assertThat(q.getConfidence()).isEqualTo(3);
        assertThat(d.log).singleElement().satisfies(e -> {
            assertThat(e.rating()).isEqualTo("good");
            assertThat(e.date()).isEqualTo(1_790_601_300L);
        });
        assertThat(d.info).containsEntry("Last Sync", "2026-09-28 20:56");
        assertThat(d.submissions.get(1_234_567_890L)).satisfies(s -> {
            assertThat(s.status()).isEqualTo("Wrong Answer");
            assertThat(s.date()).isEqualTo(1_790_601_000L);
            assertThat(s.accepted()).isFalse();
        });
    }

    @Test
    void picksUpEditsMadeInExcel() throws Exception {
        Path file = dir.resolve("t.xlsx");
        ExcelStore app = store(file);
        app.data().problems.put("two-sum", new Problem("two-sum"));
        app.save();
        assertThat(app.data().problems.get("two-sum").getNotes()).isEmpty();

        // edit the file like a user would in Excel: add a note, a real date, and a new row with just a URL
        try (Workbook wb = WorkbookFactory.create(file.toFile())) {
            Sheet s = wb.getSheet(ExcelStore.SHEET_PROBLEMS);
            s.getRow(1).createCell(13).setCellValue("Edited in Excel");
            Cell next = s.getRow(1).createCell(10);
            next.setCellValue(LocalDateTime.of(2026, 9, 1, 9, 30));
            CellStyle ds = wb.createCellStyle();
            ds.setDataFormat(wb.createDataFormat().getFormat("dd/mm/yyyy hh:mm"));
            next.setCellStyle(ds);
            s.createRow(2).createCell(3).setCellValue("https://leetcode.com/problems/valid-anagram/description/");
            Path edited = dir.resolve("edited.xlsx");
            try (OutputStream out = Files.newOutputStream(edited)) {
                wb.write(out);
            }
            Files.move(edited, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        }
        Files.setLastModifiedTime(file, FileTime.fromMillis(System.currentTimeMillis() + 5_000));

        ExcelStore.Data d = app.data();
        assertThat(d.problems.get("two-sum").getNotes()).isEqualTo("Edited in Excel");
        assertThat(d.problems.get("two-sum").getNextReview())
                .isEqualTo(LocalDateTime.of(2026, 9, 1, 9, 30).atZone(ZoneId.systemDefault()).toEpochSecond());
        assertThat(d.problems).containsKey("valid-anagram");
    }

    @Test
    void keepsADailyBackup() {
        Path file = dir.resolve("t.xlsx");
        ExcelStore s = store(file);
        s.save();
        s.save();
        assertThat(dir.resolve("backups").toFile().list()).hasSize(1);
    }

    @Test
    void extractsSlugFromUrls() {
        assertThat(ExcelStore.slugFromUrl("https://leetcode.com/problems/two-sum/description/")).isEqualTo("two-sum");
        assertThat(ExcelStore.slugFromUrl("Two-Sum")).isEqualTo("two-sum");
        assertThat(ExcelStore.slugFromUrl("")).isEmpty();
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
