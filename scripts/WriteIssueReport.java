import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Creates output/<YYYY-MM-DD>_<issue_name>.xlsx from a JSON issue file. No external dependencies. */
public class WriteIssueReport {

    static final String[] COLUMNS = {
        "Issue Description", "Environment", "Reported Date", "Resolved Date",
        "Ticket", "Root Cause", "Impact", "Resolution", "Error Log"
    };
    static final int[] WIDTHS = {40, 14, 14, 14, 14, 40, 30, 40, 80};

    public static void main(String[] args) throws IOException {
        if (args.length != 1) {
            System.err.println("Usage: java scripts/WriteIssueReport.java <issue.json>");
            System.exit(1);
        }
        String json = new String(Files.readAllBytes(Paths.get(args[0])), StandardCharsets.UTF_8)
                .replace("\uFEFF", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) new JsonParser(json).parse();

        String issueName = String.valueOf(data.getOrDefault("issue_name", "issue"))
                .replaceAll("[^A-Za-z0-9_-]+", "_").replaceAll("^_+|_+$", "");
        if (issueName.isEmpty()) issueName = "issue";

        @SuppressWarnings("unchecked")
        List<Object> issues = (List<Object>) data.getOrDefault("issues", new ArrayList<>());

        Path outDir = Paths.get(System.getProperty("user.dir"), "output");
        Files.createDirectories(outDir);
        Path out = outDir.resolve(LocalDate.now() + "_" + issueName + ".xlsx");

        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(out))) {
            put(zip, "[Content_Types].xml", CONTENT_TYPES);
            put(zip, "_rels/.rels", ROOT_RELS);
            put(zip, "xl/workbook.xml", WORKBOOK);
            put(zip, "xl/_rels/workbook.xml.rels", WORKBOOK_RELS);
            put(zip, "xl/styles.xml", STYLES);
            put(zip, "xl/worksheets/sheet1.xml", sheet(issues));
        }
        System.out.println(out);
    }

    static String sheet(List<Object> issues) {
        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>")
          .append("<worksheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">")
          .append("<sheetViews><sheetView workbookViewId=\"0\"><pane ySplit=\"1\" topLeftCell=\"A2\" activePane=\"bottomLeft\" state=\"frozen\"/></sheetView></sheetViews>")
          .append("<cols>");
        for (int i = 0; i < WIDTHS.length; i++) {
            sb.append("<col min=\"").append(i + 1).append("\" max=\"").append(i + 1)
              .append("\" width=\"").append(WIDTHS[i]).append("\" customWidth=\"1\"/>");
        }
        sb.append("</cols><sheetData>");
        row(sb, 1, COLUMNS, 1);
        int r = 2;
        for (Object o : issues) {
            @SuppressWarnings("unchecked")
            Map<String, Object> issue = (Map<String, Object>) o;
            String[] values = new String[COLUMNS.length];
            for (int i = 0; i < COLUMNS.length; i++) {
                Object v = issue.get(COLUMNS[i]);
                values[i] = v == null ? "N/A" : String.valueOf(v);
            }
            row(sb, r++, values, 2);
        }
        sb.append("</sheetData></worksheet>");
        return sb.toString();
    }

    static void row(StringBuilder sb, int r, String[] values, int style) {
        sb.append("<row r=\"").append(r).append("\">");
        for (int i = 0; i < values.length; i++) {
            sb.append("<c r=\"").append((char) ('A' + i)).append(r).append("\" s=\"").append(style)
              .append("\" t=\"inlineStr\"><is><t xml:space=\"preserve\">").append(xml(values[i]))
              .append("</t></is></c>");
        }
        sb.append("</row>");
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
                    // Drop control characters that are invalid in XML 1.0.
                    if (c >= 0x20 || c == '\n' || c == '\r' || c == '\t') sb.append(c);
            }
        }
        return sb.toString();
    }

    static void put(ZipOutputStream zip, String name, String content) throws IOException {
        zip.putNextEntry(new ZipEntry(name));
        OutputStream os = zip;
        os.write(content.getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    static final String CONTENT_TYPES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">"
        + "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>"
        + "<Default Extension=\"xml\" ContentType=\"application/xml\"/>"
        + "<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>"
        + "<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>"
        + "<Override PartName=\"/xl/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml\"/>"
        + "</Types>";

    static final String ROOT_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"xl/workbook.xml\"/>"
        + "</Relationships>";

    static final String WORKBOOK = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<workbook xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\">"
        + "<sheets><sheet name=\"Issues\" sheetId=\"1\" r:id=\"rId1\"/></sheets></workbook>";

    static final String WORKBOOK_RELS = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">"
        + "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>"
        + "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles\" Target=\"styles.xml\"/>"
        + "</Relationships>";

    // Style 1 = bold white header on dark blue, style 2 = wrapped, top-aligned body.
    static final String STYLES = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>"
        + "<styleSheet xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\">"
        + "<fonts count=\"2\"><font><sz val=\"11\"/><name val=\"Calibri\"/></font>"
        + "<font><b/><sz val=\"11\"/><color rgb=\"FFFFFFFF\"/><name val=\"Calibri\"/></font></fonts>"
        + "<fills count=\"3\"><fill><patternFill patternType=\"none\"/></fill><fill><patternFill patternType=\"gray125\"/></fill>"
        + "<fill><patternFill patternType=\"solid\"><fgColor rgb=\"FF1F4E78\"/><bgColor indexed=\"64\"/></patternFill></fill></fills>"
        + "<borders count=\"1\"><border><left/><right/><top/><bottom/><diagonal/></border></borders>"
        + "<cellStyleXfs count=\"1\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\"/></cellStyleXfs>"
        + "<cellXfs count=\"3\"><xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\"/>"
        + "<xf numFmtId=\"0\" fontId=\"1\" fillId=\"2\" borderId=\"0\" xfId=\"0\" applyFont=\"1\" applyFill=\"1\"/>"
        + "<xf numFmtId=\"0\" fontId=\"0\" fillId=\"0\" borderId=\"0\" xfId=\"0\" applyAlignment=\"1\"><alignment vertical=\"top\" wrapText=\"1\"/></xf></cellXfs>"
        + "</styleSheet>";

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
