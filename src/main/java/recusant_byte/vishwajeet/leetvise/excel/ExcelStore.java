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
import org.apache.poi.common.usermodel.HyperlinkType;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFFont;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * The Excel workbook is the database. Each sheet is a table (row 1 = headers):
 * <ul>
 *   <li><b>Problems</b> – one row per solved problem + its revision schedule</li>
 *   <li><b>Revision Log</b> – append-only history of every revision</li>
 *   <li><b>Submissions</b> – your LeetCode submission history (needs the session cookie), feeds the insights</li>
 *   <li><b>Info</b> – key/value pairs such as the last sync time</li>
 * </ul>
 * Columns are matched by header name, so you can reorder them in Excel. The file is re-read whenever it
 * changes on disk. Before the first save of each day the previous file is copied into {@code backups/}.
 */
@Component
public class ExcelStore {

    private static final Logger log = LoggerFactory.getLogger(ExcelStore.class);

    public static final String SHEET_PROBLEMS = "Problems";
    public static final String SHEET_LOG = "Revision Log";
    public static final String SHEET_SUBMISSIONS = "Submissions";
    public static final String SHEET_INFO = "Info";

    static final List<String> PROBLEM_COLS = List.of(
            "Slug", "#", "Title", "URL", "Difficulty", "Tags", "First Solved", "Last Solved",
            "Times Revised", "Last Revised", "Next Review", "Interval (days)", "Confidence", "Notes");
    static final List<String> LOG_COLS = List.of("Date", "#", "Title", "Difficulty", "Rating", "Slug");
    static final List<String> SUBMISSION_COLS = List.of("ID", "Date", "Slug", "Title", "Status", "Language", "Runtime", "Memory");
    static final List<String> INFO_COLS = List.of("Key", "Value");

    private static final Map<String, Integer> WIDTHS = Map.ofEntries(
            Map.entry("Slug", 30), Map.entry("#", 7), Map.entry("Title", 44), Map.entry("URL", 52),
            Map.entry("Difficulty", 11), Map.entry("Tags", 38), Map.entry("Notes", 60),
            Map.entry("Times Revised", 14), Map.entry("Interval (days)", 15), Map.entry("Confidence", 12),
            Map.entry("Rating", 10), Map.entry("Key", 26), Map.entry("Value", 40), Map.entry("ID", 13),
            Map.entry("Status", 22), Map.entry("Language", 12), Map.entry("Runtime", 10), Map.entry("Memory", 10));

    private static final DateTimeFormatter TEXT_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    /** Everything held in the workbook, kept in memory. */
    public static final class Data {
        public final Map<String, Problem> problems = new LinkedHashMap<>();
        public final List<RevisionEntry> log = new ArrayList<>();
        /** Keyed by LeetCode's submission id, so re-syncing never duplicates a row. */
        public final Map<Long, Submission> submissions = new HashMap<>();
        public final Map<String, String> info = new LinkedHashMap<>();
    }

    private final Path file;
    private final Path backupDir;
    private final DataFormatter formatter = new DataFormatter();
    private Data data = new Data();
    private long loadedMtime = -1;

    public ExcelStore(LeetViseProperties props) {
        this.file = props.excel();
        this.backupDir = file.resolveSibling("backups");
    }

    public Path file() {
        return file;
    }

    /** Current data, re-read from disk first if the file was changed (e.g. edited in Excel). */
    public synchronized Data data() {
        try {
            if (!Files.exists(file)) {
                if (loadedMtime != 0) { data = new Data(); loadedMtime = 0; }
                return data;
            }
            long mtime = Files.getLastModifiedTime(file).toMillis();
            if (mtime != loadedMtime) {
                data = read();
                loadedMtime = mtime;
                log.info("Loaded {} problems from {}", data.problems.size(), file);
            }
            return data;
        } catch (IOException | RuntimeException e) {
            throw new IllegalStateException("Could not read " + file.getFileName() + ": " + e.getMessage(), e);
        }
    }

    // ================================================================== read

    private Data read() throws IOException {
        Data d = new Data();
        try (Workbook wb = WorkbookFactory.create(file.toFile(), null, true)) {
            Sheet ps = wb.getSheet(SHEET_PROBLEMS);
            if (ps != null) {
                Map<String, Integer> h = headers(ps);
                for (Row r : rows(ps)) {
                    String slug = text(r, h, "Slug");
                    if (slug.isEmpty()) slug = slugFromUrl(text(r, h, "URL"));
                    if (slug.isEmpty()) continue;
                    Problem p = new Problem(slug);
                    p.setId(text(r, h, "#"));
                    p.setTitle(text(r, h, "Title"));
                    p.setDifficulty(text(r, h, "Difficulty"));
                    p.setTags(text(r, h, "Tags"));
                    p.setNotes(text(r, h, "Notes"));
                    p.setFirstSolved(epoch(r, h, "First Solved"));
                    p.setLastSolved(epoch(r, h, "Last Solved"));
                    p.setLastRevised(epoch(r, h, "Last Revised"));
                    p.setNextReview(epoch(r, h, "Next Review"));
                    p.setTimesRevised((int) integer(r, h, "Times Revised"));
                    p.setIntervalDays((int) integer(r, h, "Interval (days)"));
                    p.setConfidence((int) integer(r, h, "Confidence"));
                    d.problems.put(slug, p);
                }
            }
            Sheet ls = wb.getSheet(SHEET_LOG);
            if (ls != null) {
                Map<String, Integer> h = headers(ls);
                for (Row r : rows(ls)) {
                    d.log.add(new RevisionEntry(epoch(r, h, "Date"), text(r, h, "Slug"), text(r, h, "#"),
                            text(r, h, "Title"), text(r, h, "Difficulty"), text(r, h, "Rating")));
                }
            }
            Sheet ss = wb.getSheet(SHEET_SUBMISSIONS);
            if (ss != null) {
                Map<String, Integer> h = headers(ss);
                for (Row r : rows(ss)) {
                    String slug = text(r, h, "Slug");
                    if (slug.isEmpty()) continue;
                    Submission sub = new Submission(integer(r, h, "ID"), epoch(r, h, "Date"), slug, text(r, h, "Title"),
                            text(r, h, "Status"), text(r, h, "Language"), text(r, h, "Runtime"), text(r, h, "Memory"));
                    d.submissions.put(sub.id(), sub);
                }
            }
            Sheet is = wb.getSheet(SHEET_INFO);
            if (is != null) {
                Map<String, Integer> h = headers(is);
                for (Row r : rows(is)) {
                    String k = text(r, h, "Key");
                    if (!k.isEmpty()) d.info.put(k, text(r, h, "Value"));
                }
            }
        }
        return d;
    }

    private Map<String, Integer> headers(Sheet s) {
        Map<String, Integer> h = new HashMap<>();
        Row first = s.getRow(s.getFirstRowNum());
        if (first == null) return h;
        for (Cell c : first) {
            String name = formatter.formatCellValue(c).trim();
            if (!name.isEmpty()) h.putIfAbsent(name, c.getColumnIndex());
        }
        return h;
    }

    private List<Row> rows(Sheet s) {
        List<Row> out = new ArrayList<>();
        for (int i = s.getFirstRowNum() + 1; i <= s.getLastRowNum(); i++) {
            Row r = s.getRow(i);
            if (r != null) out.add(r);
        }
        return out;
    }

    private Cell cell(Row r, Map<String, Integer> h, String col) {
        Integer idx = h.get(col);
        return idx == null ? null : r.getCell(idx, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
    }

    private String text(Row r, Map<String, Integer> h, String col) {
        Cell c = cell(r, h, col);
        if (c == null) return "";
        if (c.getCellType() == CellType.FORMULA) {
            // e.g. =HYPERLINK("…") – use the cached result
            return c.getCachedFormulaResultType() == CellType.STRING ? c.getStringCellValue().trim() : formatter.formatCellValue(c).trim();
        }
        return formatter.formatCellValue(c).trim();
    }

    private long integer(Row r, Map<String, Integer> h, String col) {
        Cell c = cell(r, h, col);
        if (c == null) return 0;
        if (c.getCellType() == CellType.NUMERIC) return (long) c.getNumericCellValue();
        try {
            return (long) Double.parseDouble(text(r, h, col));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /** Accepts real Excel dates, Excel date serials, "yyyy-MM-dd HH:mm" or "yyyy-MM-dd". */
    private long epoch(Row r, Map<String, Integer> h, String col) {
        Cell c = cell(r, h, col);
        if (c == null) return 0;
        try {
            if (c.getCellType() == CellType.NUMERIC) {
                LocalDateTime t = DateUtil.isCellDateFormatted(c) ? c.getLocalDateTimeCellValue()
                        : DateUtil.getLocalDateTime(c.getNumericCellValue());
                return toEpoch(t);
            }
            String s = text(r, h, col);
            if (s.isEmpty()) return 0;
            if (s.length() == 10) return toEpoch(LocalDate.parse(s).atStartOfDay());
            return toEpoch(LocalDateTime.parse(s, TEXT_DATE));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    // ================================================================== write

    public synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            backupToday();
            Path tmp = file.resolveSibling("." + file.getFileName() + ".tmp");
            try (XSSFWorkbook wb = new XSSFWorkbook(); OutputStream out = Files.newOutputStream(tmp)) {
                Styles st = new Styles(wb);
                writeProblems(wb, st);
                writeLog(wb, st);
                writeSubmissions(wb, st);
                writeInfo(wb, st);
                wb.write(out);
            }
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException e) {
                Files.deleteIfExists(tmp);
                throw new IOException("is it open in Excel? Close it and try again.", e);
            }
            loadedMtime = Files.getLastModifiedTime(file).toMillis();
        } catch (IOException e) {
            throw new IllegalStateException("Could not save " + file.getFileName() + " – " + e.getMessage(), e);
        }
    }

    private void writeProblems(XSSFWorkbook wb, Styles st) {
        Sheet s = sheet(wb, st, SHEET_PROBLEMS, PROBLEM_COLS);
        List<Problem> sorted = new ArrayList<>(data.problems.values());
        sorted.sort(Comparator.comparingLong(Problem::getLastSolved).reversed());
        int i = 1;
        for (Problem p : sorted) {
            Row r = s.createRow(i++);
            str(r, 0, p.getSlug());
            num(r, 1, p.getId());
            str(r, 2, p.getTitle());
            link(r, 3, p.getUrl(), wb, st);
            str(r, 4, p.getDifficulty());
            str(r, 5, p.getTags());
            date(r, 6, p.getFirstSolved(), st);
            date(r, 7, p.getLastSolved(), st);
            r.createCell(8).setCellValue(p.getTimesRevised());
            date(r, 9, p.getLastRevised(), st);
            date(r, 10, p.getNextReview(), st);
            if (p.getIntervalDays() > 0) r.createCell(11).setCellValue(p.getIntervalDays());
            if (p.getConfidence() > 0) r.createCell(12).setCellValue(p.getConfidence());
            str(r, 13, p.getNotes());
        }
        finish(s, PROBLEM_COLS, i);
    }

    private void writeLog(XSSFWorkbook wb, Styles st) {
        Sheet s = sheet(wb, st, SHEET_LOG, LOG_COLS);
        int i = 1;
        for (RevisionEntry e : data.log) {
            Row r = s.createRow(i++);
            date(r, 0, e.date(), st);
            num(r, 1, e.id());
            str(r, 2, e.title());
            str(r, 3, e.difficulty());
            str(r, 4, e.rating());
            str(r, 5, e.slug());
        }
        finish(s, LOG_COLS, i);
    }

    private void writeSubmissions(XSSFWorkbook wb, Styles st) {
        Sheet s = sheet(wb, st, SHEET_SUBMISSIONS, SUBMISSION_COLS);
        List<Submission> sorted = new ArrayList<>(data.submissions.values());
        sorted.sort(Comparator.comparingLong(Submission::date).reversed());
        int i = 1;
        for (Submission sub : sorted) {
            Row r = s.createRow(i++);
            r.createCell(0).setCellValue(sub.id());
            date(r, 1, sub.date(), st);
            str(r, 2, sub.slug());
            str(r, 3, sub.title());
            str(r, 4, sub.status());
            str(r, 5, sub.language());
            str(r, 6, sub.runtime());
            str(r, 7, sub.memory());
        }
        finish(s, SUBMISSION_COLS, i);
    }

    private void writeInfo(XSSFWorkbook wb, Styles st) {
        Sheet s = sheet(wb, st, SHEET_INFO, INFO_COLS);
        int i = 1;
        for (Map.Entry<String, String> e : data.info.entrySet()) {
            Row r = s.createRow(i++);
            str(r, 0, e.getKey());
            str(r, 1, e.getValue());
        }
        finish(s, INFO_COLS, i);
    }

    private Sheet sheet(XSSFWorkbook wb, Styles st, String name, List<String> cols) {
        Sheet s = wb.createSheet(name);
        Row h = s.createRow(0);
        for (int c = 0; c < cols.size(); c++) {
            Cell cell = h.createCell(c);
            cell.setCellValue(cols.get(c));
            cell.setCellStyle(st.header);
            s.setColumnWidth(c, WIDTHS.getOrDefault(cols.get(c), 18) * 256);
        }
        s.createFreezePane(0, 1);
        return s;
    }

    private void finish(Sheet s, List<String> cols, int rowCount) {
        s.setAutoFilter(new CellRangeAddress(0, Math.max(rowCount - 1, 1), 0, cols.size() - 1));
    }

    private static void str(Row r, int c, String v) {
        if (v != null && !v.isEmpty()) r.createCell(c).setCellValue(v);
    }

    private static void num(Row r, int c, String v) {
        if (v == null || v.isEmpty()) return;
        try {
            r.createCell(c).setCellValue(Integer.parseInt(v));
        } catch (NumberFormatException e) {
            r.getCell(c).setCellValue(v);
        }
    }

    private static void date(Row r, int c, long epoch, Styles st) {
        if (epoch <= 0) return;
        Cell cell = r.createCell(c);
        cell.setCellValue(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()));
        cell.setCellStyle(st.date);
    }

    private static void link(Row r, int c, String url, XSSFWorkbook wb, Styles st) {
        Cell cell = r.createCell(c);
        cell.setCellValue(url);
        Hyperlink h = wb.getCreationHelper().createHyperlink(HyperlinkType.URL);
        h.setAddress(url);
        cell.setHyperlink(h);
        cell.setCellStyle(st.link);
    }

    private void backupToday() throws IOException {
        if (!Files.exists(file)) return;
        Files.createDirectories(backupDir);
        String base = file.getFileName().toString().replaceFirst("\\.xlsx$", "");
        Path b = backupDir.resolve(base + "-" + LocalDate.now() + ".xlsx");
        if (!Files.exists(b)) Files.copy(file, b);
    }

    /** Teal header with yellow bold text, date format, teal underlined links. */
    private static final class Styles {
        final CellStyle header;
        final CellStyle date;
        final CellStyle link;

        Styles(XSSFWorkbook wb) {
            XSSFFont hf = wb.createFont();
            hf.setBold(true);
            hf.setColor(new XSSFColor(new byte[]{(byte) 0xFF, (byte) 0xD8, 0x4D}, null));
            XSSFCellStyle h = wb.createCellStyle();
            h.setFont(hf);
            h.setFillForegroundColor(new XSSFColor(new byte[]{0x0B, 0x5C, 0x59}, null));
            h.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header = h;

            date = wb.createCellStyle();
            date.setDataFormat(wb.createDataFormat().getFormat("yyyy-mm-dd hh:mm"));
            date.setAlignment(HorizontalAlignment.LEFT);

            XSSFFont lf = wb.createFont();
            lf.setUnderline(Font.U_SINGLE);
            lf.setColor(new XSSFColor(new byte[]{0x0B, 0x7A, 0x75}, null));
            link = wb.createCellStyle();
            link.setFont(lf);
        }
    }

    // ================================================================== helpers

    public static String slugFromUrl(String s) {
        if (s == null) return "";
        s = s.trim();
        int i = s.indexOf("/problems/");
        if (i >= 0) {
            s = s.substring(i + "/problems/".length());
            int j = s.indexOf('/');
            if (j >= 0) s = s.substring(0, j);
        }
        return s.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9-]", "");
    }

    public static String format(long epoch) {
        return epoch <= 0 ? "" : TEXT_DATE.format(LocalDateTime.ofInstant(Instant.ofEpochSecond(epoch), ZoneId.systemDefault()));
    }

    public static long parse(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return toEpoch(LocalDateTime.parse(s.trim(), TEXT_DATE));
        } catch (RuntimeException e) {
            return 0;
        }
    }

    private static long toEpoch(LocalDateTime t) {
        return t.atZone(ZoneId.systemDefault()).toEpochSecond();
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
