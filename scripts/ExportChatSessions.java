import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Exports Copilot chat sessions from the local session-store.db (SQLite) into
 * one Markdown file per session under output/chat_history/. Content is read and
 * written disk-to-disk. Requires the sqlite-jdbc jar on the classpath.
 *
 * Usage: java -cp "scripts/lib/sqlite-jdbc.jar" scripts/ExportChatSessions.java <db> <outDir> [cwdFilter]
 */
public class ExportChatSessions {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: ExportChatSessions <db> <outDir> [cwdFilter]");
            System.exit(2);
        }
        String dbPath = args[0];
        Path outDir = Paths.get(args[1]);
        String cwdFilter = args.length > 2 ? args[2] : null;
        Files.createDirectories(outDir);

        String url = "jdbc:sqlite:" + dbPath;
        int sessionCount = 0, turnCount = 0;

        try (Connection conn = DriverManager.getConnection(url)) {
            String sessSql = "SELECT id, cwd, summary, agent_name, created_at, updated_at FROM sessions"
                    + (cwdFilter != null ? " WHERE cwd LIKE ?" : "")
                    + " ORDER BY created_at ASC";
            try (PreparedStatement ps = conn.prepareStatement(sessSql)) {
                if (cwdFilter != null) ps.setString(1, "%" + cwdFilter + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String cwd = nz(rs.getString("cwd"));
                        String summary = nz(rs.getString("summary"));
                        String agent = nz(rs.getString("agent_name"));
                        String created = nz(rs.getString("created_at"));
                        String updated = nz(rs.getString("updated_at"));

                        String day = created.length() >= 10 ? created.substring(0, 10) : "unknown";
                        String shortId = id.length() >= 8 ? id.substring(0, 8) : id;
                        String fileName = day + "_" + shortId + ".md";
                        Path outFile = outDir.resolve(fileName);

                        StringBuilder sb = new StringBuilder();
                        sb.append("# Chat session ").append(shortId).append("\n\n");
                        sb.append("- Session ID: `").append(id).append("`\n");
                        sb.append("- Created: ").append(created).append("\n");
                        sb.append("- Updated: ").append(updated).append("\n");
                        sb.append("- Agent: ").append(agent).append("\n");
                        sb.append("- Workspace: ").append(cwd).append("\n");
                        sb.append("- Summary: ").append(oneLine(summary)).append("\n\n");
                        sb.append("---\n\n");

                        int turns = 0;
                        try (PreparedStatement tp = conn.prepareStatement(
                                "SELECT turn_index, user_message, assistant_response, timestamp "
                                + "FROM turns WHERE session_id = ? ORDER BY turn_index ASC")) {
                            tp.setString(1, id);
                            try (ResultSet trs = tp.executeQuery()) {
                                while (trs.next()) {
                                    int idx = trs.getInt("turn_index");
                                    String user = nz(trs.getString("user_message"));
                                    String asst = nz(trs.getString("assistant_response"));
                                    String ts = nz(trs.getString("timestamp"));
                                    sb.append("## Turn ").append(idx + 1);
                                    if (!ts.isEmpty()) sb.append("  \n_").append(ts).append("_");
                                    sb.append("\n\n");
                                    sb.append("**User:**\n\n").append(user.isEmpty() ? "_(empty)_" : user).append("\n\n");
                                    sb.append("**Assistant:**\n\n").append(asst.isEmpty() ? "_(empty)_" : asst).append("\n\n");
                                    sb.append("---\n\n");
                                    turns++;
                                    turnCount++;
                                }
                            }
                        }
                        Files.writeString(outFile, sb.toString(), StandardCharsets.UTF_8);
                        sessionCount++;
                        System.out.println("Wrote " + fileName + " (" + turns + " turns)");
                    }
                }
            }

            // Write an index file.
            StringBuilder idx = new StringBuilder();
            idx.append("# Chat history export\n\n");
            idx.append("Exported ").append(sessionCount).append(" sessions, ")
               .append(turnCount).append(" turns, on ")
               .append(OffsetDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE)).append(".\n\n");
            idx.append("| Date | Short ID | Turns | Summary | File |\n");
            idx.append("|------|----------|-------|---------|------|\n");
            String listSql = "SELECT id, summary, created_at, "
                    + "(SELECT COUNT(*) FROM turns t WHERE t.session_id = s.id) AS turns FROM sessions s"
                    + (cwdFilter != null ? " WHERE cwd LIKE ?" : "")
                    + " ORDER BY created_at ASC";
            try (PreparedStatement ps = conn.prepareStatement(listSql)) {
                if (cwdFilter != null) ps.setString(1, "%" + cwdFilter + "%");
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        String id = rs.getString("id");
                        String day = nz(rs.getString("created_at"));
                        day = day.length() >= 10 ? day.substring(0, 10) : "unknown";
                        String shortId = id.length() >= 8 ? id.substring(0, 8) : id;
                        String fileName = day + "_" + shortId + ".md";
                        idx.append("| ").append(day).append(" | ").append(shortId)
                           .append(" | ").append(rs.getInt("turns"))
                           .append(" | ").append(oneLine(nz(rs.getString("summary"))))
                           .append(" | [").append(fileName).append("](").append(fileName).append(") |\n");
                    }
                }
            }
            Files.writeString(outDir.resolve("INDEX.md"), idx.toString(), StandardCharsets.UTF_8);
        }

        System.out.println("Done: " + sessionCount + " sessions, " + turnCount + " turns -> " + outDir);
    }

    static String nz(String s) { return s == null ? "" : s; }

    static String oneLine(String s) {
        String t = s.replaceAll("[\\r\\n]+", " ").trim();
        if (t.length() > 80) t = t.substring(0, 77) + "...";
        return t.replace("|", "\\|");
    }
}
