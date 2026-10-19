import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Updates an EXISTING issue row in the user-maintained tracker workbook
 * (issue_tracker/SRI_Issue_Tracker.xlsx) in place, without appending or
 * reordering any rows. Use this when an already-recorded issue changes state
 * (e.g. in-progress -> resolved, add ticket, refine root cause/resolution).
 *
 * The row is located by a strict multi-field match: EVERY field in "match"
 * must equal the row's value for that column. Exactly one row must match,
 * otherwise nothing is written (ambiguous or not-found both abort).
 *
 * Reads both shared-string cells (user-created rows) and inline-string cells
 * (rows added by AppendIssueToTracker). Writes updated cells as inline strings.
 * No external dependencies.
 *
 * Input JSON shape:
 * {
 *   "match":  { "Issue Description": "...", "Environment": "...", "Reported Date": "..." },
 *   "update": { "Resolved Date": "2026-10-16", "Resolution": "...", "Root Cause": "..." }
 * }
 *
 * Usage: java scripts/UpdateIssueInTracker.java <update.json> [tracker.xlsx]
 */
public class UpdateIssueInTracker {

    static final String[] COLUMNS = {
        "Issue Description", "Environment", "Reported Date", "Resolved Date",
        "Ticket", "Root Cause", "Impact", "Resolution", "Error Log"
    };

    static final String SHEET = "xl/worksheets/sheet1.xml";
    static final String SHARED = "xl/sharedStrings.xml";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java scripts/UpdateIssueInTracker.java <update.json> [tracker.xlsx]");
            System.exit(1);
        }
        Path trackerPath = args.length >= 2
            ? Paths.get(args[1])
            : Paths.get(System.getProperty("user.dir"), "issue_tracker", "SRI_Issue_Tracker.xlsx");
        if (!Files.exists(trackerPath)) {
            System.err.println("Tracker not found: " + trackerPath);
            System.exit(2);
        }

        String json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8)
                .replace("\uFEFF", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) new JsonParser(json).parse();
        @SuppressWarnings("unchecked")
        Map<String, Object> match = (Map<String, Object>) data.get("match");
        @SuppressWarnings("unchecked")
        Map<String, Object> update = (Map<String, Object>) data.get("update");
        if (match == null || match.isEmpty()) {
            System.err.println("JSON 'match' is required and must contain at least one field."); System.exit(3);
        }
        if (update == null || update.isEmpty()) {
            System.err.println("JSON 'update' is required and must contain at least one field."); System.exit(3);
        }
        validateColumns(match.keySet());
        validateColumns(update.keySet());

        // Read all zip entries into memory, preserving order.
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipFile zf = new ZipFile(trackerPath.toFile())) {
            var e = zf.entries();
            while (e.hasMoreElements()) {
                ZipEntry ze = e.nextElement();
                try (InputStream is = zf.getInputStream(ze)) {
                    entries.put(ze.getName(), is.readAllBytes());
                }
            }
        }
        if (!entries.containsKey(SHEET)) {
            System.err.println("Unexpected workbook layout (missing sheet1)."); System.exit(4);
        }

        String sheet = new String(entries.get(SHEET), StandardCharsets.UTF_8);
        List<String> shared = entries.containsKey(SHARED)
            ? parseSharedStrings(new String(entries.get(SHARED), StandardCharsets.UTF_8))
            : new ArrayList<>();

        // Scan data rows (r >= 2), find the one(s) matching all criteria.
        Matcher rowM = Pattern.compile("<row\\b[^>]*?\\br=\"(\\d+)\"[^>]*?>(.*?)</row>", Pattern.DOTALL).matcher(sheet);
        List<int[]> matchedSpans = new ArrayList<>();
        List<Integer> matchedRowNums = new ArrayList<>();
        List<String> matchedRowElems = new ArrayList<>();
        int scanned = 0;
        while (rowM.find()) {
            int rowNum = Integer.parseInt(rowM.group(1));
            if (rowNum < 2) continue; // skip header
            scanned++;
            Map<Integer, String> cells = parseRowCells(rowM.group(2), shared);
            if (rowMatches(cells, match)) {
                matchedSpans.add(new int[]{rowM.start(), rowM.end()});
                matchedRowNums.add(rowNum);
                matchedRowElems.add(rowM.group(0));
            }
        }

        if (matchedRowNums.isEmpty()) {
            System.err.println("No row matched the given criteria (scanned " + scanned + " data rows). Nothing updated.");
            System.exit(5);
        }
        if (matchedRowNums.size() > 1) {
            System.err.println("Ambiguous match: " + matchedRowNums.size() + " rows matched (rows " + matchedRowNums
                + "). Add more match fields to target exactly one row. Nothing updated.");
            System.exit(6);
        }

        int[] span = matchedSpans.get(0);
        int rowNum = matchedRowNums.get(0);
        String rowElem = matchedRowElems.get(0);
        String newRow = rowElem;

        List<String> changed = new ArrayList<>();
        for (Map.Entry<String, Object> en : update.entrySet()) {
            int colIdx = colIndex(en.getKey());
            String ref = colLetter(colIdx) + rowNum;
            String newVal = en.getValue() == null ? "N/A" : String.valueOf(en.getValue());
            String cellRegex = "<c\\b[^>]*?\\br=\"" + Pattern.quote(ref) + "\"[^>]*?(?:/>|>.*?</c>)";
            Matcher cm = Pattern.compile(cellRegex, Pattern.DOTALL).matcher(newRow);
            String newCell = "<c r=\"" + ref + "\" t=\"inlineStr\"><is><t xml:space=\"preserve\">"
                + xml(newVal) + "</t></is></c>";
            if (cm.find()) {
                newRow = newRow.substring(0, cm.start()) + newCell + newRow.substring(cm.end());
            } else {
                // Cell not present in the row (sparse) - insert before </row> to keep the row complete.
                int close = newRow.lastIndexOf("</row>");
                newRow = newRow.substring(0, close) + newCell + newRow.substring(close);
            }
            changed.add(en.getKey() + " -> " + newVal);
        }

        sheet = sheet.substring(0, span[0]) + newRow + sheet.substring(span[1]);
        entries.put(SHEET, sheet.getBytes(StandardCharsets.UTF_8));

        // Write to a temp file, then atomically replace the tracker.
        Path tmp = trackerPath.resolveSibling(trackerPath.getFileName() + ".tmp");
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(tmp))) {
            for (Map.Entry<String, byte[]> en : entries.entrySet()) {
                zos.putNextEntry(new ZipEntry(en.getKey()));
                zos.write(en.getValue());
                zos.closeEntry();
            }
        }
        Files.move(tmp, trackerPath, StandardCopyOption.REPLACE_EXISTING);
        System.out.println("Updated row " + rowNum + " in " + trackerPath + "; fields: " + changed);
    }

    static void validateColumns(Iterable<String> keys) {
        for (String k : keys) {
            boolean ok = false;
            for (String c : COLUMNS) if (c.equals(k)) { ok = true; break; }
            if (!ok) { System.err.println("Unknown column: '" + k + "'. Valid columns: " + String.join(", ", COLUMNS)); System.exit(7); }
        }
    }

    static int colIndex(String name) {
        for (int i = 0; i < COLUMNS.length; i++) if (COLUMNS[i].equals(name)) return i;
        throw new IllegalArgumentException("Unknown column: " + name);
    }

    static boolean rowMatches(Map<Integer, String> cells, Map<String, Object> match) {
        for (Map.Entry<String, Object> en : match.entrySet()) {
            int colIdx = colIndex(en.getKey());
            String want = en.getValue() == null ? "" : String.valueOf(en.getValue());
            String have = cells.getOrDefault(colIdx, "");
            if (!norm(have).equals(norm(want))) return false;
        }
        return true;
    }

    /** Normalize for comparison: unescape already done; collapse CRLF and trim edges. */
    static String norm(String s) {
        return s.replace("\r\n", "\n").trim();
    }

    /** colLetters -> value for one row body, resolving shared/inline/number cells. */
    static Map<Integer, String> parseRowCells(String body, List<String> shared) {
        Map<Integer, String> out = new LinkedHashMap<>();
        Matcher cm = Pattern.compile("<c\\b([^>]*?)(?:/>|>(.*?)</c>)", Pattern.DOTALL).matcher(body);
        while (cm.find()) {
            String attrs = cm.group(1);
            String cbody = cm.group(2);
            Matcher rm = Pattern.compile("r=\"([A-Z]+)\\d+\"").matcher(attrs);
            if (!rm.find()) continue;
            int colIdx = colFromLetters(rm.group(1));
            if (colIdx < 0 || colIdx >= COLUMNS.length) continue;
            out.put(colIdx, cellValue(attrType(attrs), cbody, shared));
        }
        return out;
    }

    static String attrType(String attrs) {
        Matcher m = Pattern.compile("t=\"([^\"]*)\"").matcher(attrs);
        return m.find() ? m.group(1) : null;
    }

    static String cellValue(String t, String cbody, List<String> shared) {
        if (cbody == null) return "";
        if ("inlineStr".equals(t)) {
            StringBuilder sb = new StringBuilder();
            Matcher tm = Pattern.compile("<t\\b[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(cbody);
            while (tm.find()) sb.append(unxml(tm.group(1)));
            return sb.toString();
        }
        if ("s".equals(t)) {
            Matcher vm = Pattern.compile("<v>(\\d+)</v>").matcher(cbody);
            if (vm.find()) {
                int idx = Integer.parseInt(vm.group(1));
                if (idx >= 0 && idx < shared.size()) return shared.get(idx);
            }
            return "";
        }
        // "str" or number or other: take <v> literally.
        Matcher vm = Pattern.compile("<v>(.*?)</v>", Pattern.DOTALL).matcher(cbody);
        return vm.find() ? unxml(vm.group(1)) : "";
    }

    static List<String> parseSharedStrings(String xml) {
        List<String> list = new ArrayList<>();
        Matcher si = Pattern.compile("<si\\b[^>]*>(.*?)</si>", Pattern.DOTALL).matcher(xml);
        while (si.find()) {
            StringBuilder sb = new StringBuilder();
            Matcher tm = Pattern.compile("<t\\b[^>]*>(.*?)</t>", Pattern.DOTALL).matcher(si.group(1));
            while (tm.find()) sb.append(unxml(tm.group(1)));
            list.add(sb.toString());
        }
        return list;
    }

    static int colFromLetters(String letters) {
        int n = 0;
        for (char c : letters.toCharArray()) n = n * 26 + (c - 'A' + 1);
        return n - 1;
    }

    static String colLetter(int zeroBased) {
        StringBuilder sb = new StringBuilder();
        int n = zeroBased + 1;
        while (n > 0) { int rem = (n - 1) % 26; sb.insert(0, (char) ('A' + rem)); n = (n - 1) / 26; }
        return sb.toString();
    }

    static String xml(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            switch (c) {
                case '&': sb.append("&amp;"); break;
                case '<': sb.append("&lt;"); break;
                case '>': sb.append("&gt;"); break;
                case '"': sb.append("&quot;"); break;
                default:
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') sb.append(c);
            }
        }
        return sb.toString();
    }

    static String unxml(String s) {
        return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"")
                .replace("&apos;", "'").replace("&amp;", "&");
    }

    /** Minimal JSON parser: objects, arrays, strings, numbers, true/false/null. */
    static class JsonParser {
        private final String s;
        private int i;

        JsonParser(String s) { this.s = s; }

        Object parse() {
            ws();
            char c = s.charAt(i);
            if (c == '{') return obj();
            if (c == '[') return arr();
            if (c == '"') return str();
            if (s.startsWith("true", i)) { i += 4; return true; }
            if (s.startsWith("false", i)) { i += 5; return false; }
            if (s.startsWith("null", i)) { i += 4; return null; }
            int start = i;
            while (i < s.length() && "+-0123456789.eE".indexOf(s.charAt(i)) >= 0) i++;
            if (start == i) throw new IllegalArgumentException("Unexpected character at " + i);
            return s.substring(start, i);
        }

        Map<String, Object> obj() {
            Map<String, Object> m = new LinkedHashMap<>();
            i++; ws();
            if (s.charAt(i) == '}') { i++; return m; }
            while (true) {
                ws(); String k = str(); ws(); expect(':');
                m.put(k, parse()); ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect('}'); return m;
            }
        }

        List<Object> arr() {
            List<Object> l = new ArrayList<>();
            i++; ws();
            if (s.charAt(i) == ']') { i++; return l; }
            while (true) {
                l.add(parse()); ws();
                if (s.charAt(i) == ',') { i++; continue; }
                expect(']'); return l;
            }
        }

        String str() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (true) {
                char c = s.charAt(i++);
                if (c == '"') return sb.toString();
                if (c != '\\') { sb.append(c); continue; }
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': sb.append('\n'); break;
                    case 't': sb.append('\t'); break;
                    case 'r': sb.append('\r'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u': sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16)); i += 4; break;
                    default: sb.append(e);
                }
            }
        }

        void ws() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }

        void expect(char c) {
            if (s.charAt(i) != c) throw new IllegalArgumentException("Expected '" + c + "' at " + i);
            i++;
        }
    }
}
