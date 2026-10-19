import java.io.ByteArrayOutputStream;
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
 * Appends issue rows to the existing user-maintained tracker workbook
 * (issue_tracker/SRI_Issue_Tracker.xlsx), preserving its Table1 structure.
 * Input is the same JSON shape as WriteIssueReport. No external dependencies.
 *
 * Usage: java scripts/AppendIssueToTracker.java <issue.json> [tracker.xlsx]
 */
public class AppendIssueToTracker {

    static final String[] COLUMNS = {
        "Issue Description", "Environment", "Reported Date", "Resolved Date",
        "Ticket", "Root Cause", "Impact", "Resolution", "Error Log"
    };

    static final String SHEET = "xl/worksheets/sheet1.xml";
    static final String TABLE = "xl/tables/table1.xml";

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java scripts/AppendIssueToTracker.java <issue.json> [tracker.xlsx]");
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
        List<Object> issues = (List<Object>) data.getOrDefault("issues", new ArrayList<>());
        if (issues.isEmpty()) {
            System.err.println("No issues in JSON; nothing appended.");
            System.exit(3);
        }

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
        if (!entries.containsKey(SHEET) || !entries.containsKey(TABLE)) {
            System.err.println("Unexpected workbook layout (missing sheet1 or table1).");
            System.exit(4);
        }

        String sheet = new String(entries.get(SHEET), StandardCharsets.UTF_8);
        String table = new String(entries.get(TABLE), StandardCharsets.UTF_8);

        int maxRow = maxRow(sheet);

        // Build new rows after the current last row.
        StringBuilder rows = new StringBuilder();
        int r = maxRow;
        for (Object o : issues) {
            @SuppressWarnings("unchecked")
            Map<String, Object> issue = (Map<String, Object>) o;
            r++;
            rows.append("<row r=\"").append(r).append("\">");
            for (int c = 0; c < COLUMNS.length; c++) {
                Object v = issue.get(COLUMNS[c]);
                String val = v == null ? "N/A" : String.valueOf(v);
                String ref = colLetter(c) + r;
                rows.append("<c r=\"").append(ref).append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">")
                    .append(xml(val)).append("</t></is></c>");
            }
            rows.append("</row>");
        }
        int newMax = r;

        // Insert rows before </sheetData>.
        int idx = sheet.lastIndexOf("</sheetData>");
        if (idx < 0) { System.err.println("No </sheetData> in sheet."); System.exit(5); }
        sheet = sheet.substring(0, idx) + rows + sheet.substring(idx);

        // Bump the sheet dimension's last row (keep its last column letter).
        sheet = bumpDimension(sheet, newMax);

        // Extend Table1 ref + autoFilter (A1:I<n>), and drop any stale sortState.
        table = table.replaceAll("(ref=\"A1:I)\\d+(\")", "$1" + newMax + "$2");
        table = table.replaceAll("<sortState[\\s\\S]*?</sortState>", "");

        entries.put(SHEET, sheet.getBytes(StandardCharsets.UTF_8));
        entries.put(TABLE, table.getBytes(StandardCharsets.UTF_8));

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
        System.out.println("Appended " + issues.size() + " row(s) to " + trackerPath + " (rows " + (maxRow + 1) + ".." + newMax + ")");
    }

    static int maxRow(String sheet) {
        Matcher m = Pattern.compile("<row r=\"(\\d+)\"").matcher(sheet);
        int max = 1;
        while (m.find()) max = Math.max(max, Integer.parseInt(m.group(1)));
        return max;
    }

    static String bumpDimension(String sheet, int newMax) {
        Matcher m = Pattern.compile("(<dimension ref=\"A1:[A-Z]+)(\\d+)(\"\\s*/>)").matcher(sheet);
        if (m.find()) return sheet.substring(0, m.start()) + m.group(1) + newMax + m.group(3) + sheet.substring(m.end());
        return sheet;
    }

    static String colLetter(int zeroBased) {
        return String.valueOf((char) ('A' + zeroBased)); // valid for the 9 tracker columns (A..I)
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
