import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generates an elaborated plain-text issue report in the output/ folder from the
 * SAME issue JSON used for the tracker (so the two stay in sync). The report
 * contains the nine tracker fields PLUS any extra elaboration keys present in the
 * issue object (e.g. "Analysis", "Investigation Steps", "Raw Logs", "Evidence",
 * "Recommendations") - each rendered as its own section, in JSON order.
 *
 * Extra keys are optional and are ignored by AppendIssueToTracker/UpdateIssueInTracker
 * (which only read the nine columns), so one enriched JSON drives both artifacts.
 *
 * Output: output/<issue_name>.txt (stable name, overwritten on regeneration so an
 * updated issue refreshes the same file - mirroring the in-place tracker update).
 *
 * Usage: java scripts/WriteIssueDetail.java <issue.json> [outputDir]
 */
public class WriteIssueDetail {

    // Nine tracker columns rendered first, in a readable order.
    static final String[] HEADER_FIELDS = { "Environment", "Reported Date", "Resolved Date", "Ticket" };
    static final String[] BLOCK_FIELDS  = { "Issue Description", "Root Cause", "Impact", "Resolution", "Error Log" };

    static final java.util.Set<String> KNOWN = new java.util.LinkedHashSet<>();
    static {
        for (String s : HEADER_FIELDS) KNOWN.add(s);
        for (String s : BLOCK_FIELDS) KNOWN.add(s);
    }

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            System.err.println("Usage: java scripts/WriteIssueDetail.java <issue.json> [outputDir]");
            System.exit(1);
        }
        Path outDir = args.length >= 2 ? Paths.get(args[1]) : Paths.get(System.getProperty("user.dir"), "output");
        Files.createDirectories(outDir);

        String json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8).replace("\uFEFF", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) new JsonParser(json).parse();
        String issueName = String.valueOf(data.getOrDefault("issue_name", "issue"));
        @SuppressWarnings("unchecked")
        List<Object> issues = (List<Object>) data.getOrDefault("issues", new ArrayList<>());
        if (issues.isEmpty()) { System.err.println("No issues in JSON; nothing written."); System.exit(3); }

        int idx = 0;
        for (Object o : issues) {
            idx++;
            @SuppressWarnings("unchecked")
            Map<String, Object> issue = (Map<String, Object>) o;
            String name = issues.size() == 1 ? issueName : issueName + "_" + idx;
            Path out = outDir.resolve(name + ".txt");
            Files.write(out, render(issue, name).getBytes(StandardCharsets.UTF_8));
            System.out.println("Wrote " + out);
        }
    }

    static String render(Map<String, Object> issue, String name) {
        String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
        StringBuilder sb = new StringBuilder();
        String bar = "=".repeat(80);
        String dash = "-".repeat(80);
        sb.append(bar).append("\n");
        sb.append("SRI ISSUE REPORT  —  ").append(name).append("\n");
        sb.append("Generated: ").append(ts).append("\n");
        sb.append(bar).append("\n\n");

        for (String f : HEADER_FIELDS) {
            sb.append(pad(f, 15)).append(": ").append(oneLine(scalar(issue.get(f)))).append("\n");
        }
        sb.append("\n");

        for (String f : BLOCK_FIELDS) {
            if (!issue.containsKey(f)) continue;
            section(sb, dash, f.toUpperCase(), issue.get(f));
        }

        // Any extra elaboration keys, in JSON order, rendered as their own sections.
        for (Map.Entry<String, Object> en : issue.entrySet()) {
            if (KNOWN.contains(en.getKey()) || en.getKey().equals("issue_name")) continue;
            section(sb, dash, en.getKey().toUpperCase(), en.getValue());
        }
        return sb.toString();
    }

    static void section(StringBuilder sb, String dash, String title, Object value) {
        sb.append(dash).append("\n").append(title).append("\n").append(dash).append("\n");
        sb.append(renderValue(value, 0)).append("\n\n");
    }

    /** Renders a string as-is, a list as bullet lines (recursively), a map as key: value lines. */
    static String renderValue(Object value, int depth) {
        String indent = "  ".repeat(depth);
        if (value == null) return indent + "N/A";
        if (value instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> list = (List<Object>) value;
            if (list.isEmpty()) return indent + "N/A";
            StringBuilder sb = new StringBuilder();
            for (Object item : list) {
                if (item instanceof List || item instanceof Map) {
                    sb.append(indent).append("-\n").append(renderValue(item, depth + 1)).append("\n");
                } else {
                    sb.append(indent).append("- ").append(oneToMulti(String.valueOf(item), indent)).append("\n");
                }
            }
            return trimTrailingNewlines(sb.toString());
        }
        if (value instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = (Map<String, Object>) value;
            StringBuilder sb = new StringBuilder();
            for (Map.Entry<String, Object> en : m.entrySet()) {
                Object v = en.getValue();
                if (v instanceof List || v instanceof Map) {
                    sb.append(indent).append(en.getKey()).append(":\n").append(renderValue(v, depth + 1)).append("\n");
                } else {
                    sb.append(indent).append(en.getKey()).append(": ").append(String.valueOf(v)).append("\n");
                }
            }
            return trimTrailingNewlines(sb.toString());
        }
        return indent + String.valueOf(value);
    }

    static String scalar(Object v) { return v == null ? "N/A" : String.valueOf(v); }

    static String oneLine(String s) { return s.replace("\r", " ").replace("\n", " ").trim(); }

    /** For a bullet item, keep multi-line content aligned under the bullet. */
    static String oneToMulti(String s, String indent) {
        return s.replace("\n", "\n" + indent + "  ");
    }

    static String pad(String s, int n) {
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < n) sb.append(' ');
        return sb.toString();
    }

    static String trimTrailingNewlines(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
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
