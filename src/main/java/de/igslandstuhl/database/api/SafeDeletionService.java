package de.igslandstuhl.database.api;

import de.igslandstuhl.database.server.sql.SQLiteConnection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Schema-aware, fail-closed preflight and transactional deletion for admin-managed objects. */
public final class SafeDeletionService {
    private SafeDeletionService() {}

    public static DeletionPreflight subjectPreflight(SQLiteConnection db, int id) throws SQLException {
        return db.writeTransaction(c -> subjectPreflight(c, id));
    }

    public static DeletionPreflight teacherPreflight(SQLiteConnection db, int id) throws SQLException {
        return db.writeTransaction(c -> teacherPreflight(c, id));
    }

    public static void deleteSubject(SQLiteConnection db, int id) throws SQLException {
        db.writeTransaction(c -> {
            DeletionPreflight preflight = subjectPreflight(c, id);
            if (!preflight.exists()) throw new MissingObjectException();
            if (!preflight.deletable()) throw new ObjectInUseException(preflight);
            try (PreparedStatement statement = c.prepareStatement("DELETE FROM subjects WHERE id=?")) {
                statement.setInt(1, id);
                if (statement.executeUpdate() != 1) throw new MissingObjectException();
            }
            return null;
        });
    }

    public static String deleteTeacher(SQLiteConnection db, int id) throws SQLException {
        return db.writeTransaction(c -> {
            DeletionPreflight preflight = teacherPreflight(c, id);
            if (!preflight.exists()) throw new MissingObjectException();
            if (!preflight.deletable()) throw new ObjectInUseException(preflight);
            String email;
            try (PreparedStatement statement = c.prepareStatement("SELECT email FROM teachers WHERE id=?")) {
                statement.setInt(1, id);
                try (ResultSet result = statement.executeQuery()) {
                    if (!result.next()) throw new MissingObjectException();
                    email = result.getString(1);
                }
            }
            // These optional PM rows are strictly account-bound, not teaching/history data.
            deleteAccountRows(c, "permnodes", "username", email);
            deleteAccountRows(c, "user_roles", "username", email);
            try (PreparedStatement statement = c.prepareStatement("DELETE FROM teachers WHERE id=?")) {
                statement.setInt(1, id);
                if (statement.executeUpdate() != 1) throw new MissingObjectException();
            }
            return email;
        }, email -> {
            Teacher.evictById(id, email);
            try {
                de.igslandstuhl.database.server.Server.getInstance().getWebServer()
                    .getSessionManager().invalidateUserSessions(email);
            } catch (RuntimeException ignored) {
                // A standalone/test runtime may not have a web server; the DB delete is already committed.
            }
            try {
                de.igslandstuhl.database.events.EventListener.fireEvent(
                    new de.igslandstuhl.database.events.UserAccountDeletedEvent(email));
            } catch (RuntimeException failure) {
                de.igslandstuhl.database.Application.LOGGER_API.error("Could not invalidate account-scoped plugin caches after teacher deletion", failure);
            }
        });
    }

    private static DeletionPreflight subjectPreflight(Connection c, int id) throws SQLException {
        if (!exists(c, "subjects", id)) return new DeletionPreflight(false, false, 0, List.of(), null);
        Map<String, Long> groups = foreignKeyReferences(c, "subjects", id, false);
        addSubjectIndirectReferences(c, id, groups);
        String protection = id == 8 || id == 9
            ? "Dieses Legacy-Systemfach ist geschützt und kann nicht gelöscht werden."
            : null;
        return result(groups, protection);
    }

    private static DeletionPreflight teacherPreflight(Connection c, int id) throws SQLException {
        String email = teacherEmail(c, id);
        if (email == null) return new DeletionPreflight(false, false, 0, List.of(), null);
        Map<String, Long> groups = foreignKeyReferences(c, "teachers", id, true);
        addTeacherLogicalReferences(c, id, groups);
        String protection = accountCollision(c, email)
            ? "Dieser Login ist zusätzlich einem anderen Benutzerkonto zugeordnet und wird geschützt."
            : null;
        return result(groups, protection);
    }

    private static DeletionPreflight result(Map<String, Long> counts, String protectedReason) {
        List<DeletionPreflight.ReferenceGroup> groups = counts.entrySet().stream()
            .filter(entry -> entry.getValue() > 0)
            .map(entry -> new DeletionPreflight.ReferenceGroup(entry.getKey(), label(entry.getKey()), entry.getValue()))
            .sorted(Comparator.comparing(DeletionPreflight.ReferenceGroup::key))
            .toList();
        long total = groups.stream().mapToLong(DeletionPreflight.ReferenceGroup::count).sum();
        return new DeletionPreflight(true, total == 0 && protectedReason == null, total, groups, protectedReason);
    }

    /** Count all direct FK references from the live schema; unknown future references fail closed. */
    private static Map<String, Long> foreignKeyReferences(Connection c, String parent, int id,
                                                            boolean teacher) throws SQLException {
        Map<String, Long> counts = new LinkedHashMap<>();
        List<String> tables = tableNames(c);
        for (String table : tables) {
            String pragma = "PRAGMA foreign_key_list(" + quote(table) + ")";
            Map<Integer, List<String[]>> foreignKeys = new LinkedHashMap<>();
            try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery(pragma)) {
                while (rows.next()) {
                    if (!parent.equalsIgnoreCase(rows.getString("table"))) continue;
                    foreignKeys.computeIfAbsent(rows.getInt("id"), ignored -> new ArrayList<>())
                        .add(new String[]{rows.getString("from"), rows.getString("to")});
                }
            }
            for (List<String[]> key : foreignKeys.values()) {
                StringBuilder sql = new StringBuilder("SELECT COUNT(*) FROM ").append(quote(table)).append(" WHERE ");
                for (int i = 0; i < key.size(); i++) {
                    if (i > 0) sql.append(" AND ");
                    sql.append(quote(key.get(i)[0])).append("=?");
                }
                long count;
                try (PreparedStatement statement = c.prepareStatement(sql.toString())) {
                    for (int i = 0; i < key.size(); i++) statement.setInt(i + 1, id);
                    try (ResultSet rows = statement.executeQuery()) { count = rows.next() ? rows.getLong(1) : 0; }
                }
                if (count > 0) {
                    String group = classify(table, teacher);
                    counts.merge(group, count, Long::sum);
                }
            }
        }
        return counts;
    }

    private static void addSubjectIndirectReferences(Connection c, int id, Map<String, Long> groups) throws SQLException {
        // These are logical child rows not directly FK-linked to subjects; the direct parent row
        // already blocks deletion, but counting them gives administrators a useful impact summary.
        if (tableExists(c, "course_groups")) {
            add(groups, "course_groups", scalar(c, "SELECT COUNT(*) FROM course_groups WHERE subject=?", id));
            if (tableExists(c, "course_group_members"))
                add(groups, "course_groups", joinedCount(c, "SELECT COUNT(*) FROM course_group_members m JOIN course_groups g ON g.id=m.course_group WHERE g.subject=?", id));
            for (String table : List.of("course_group_topic_releases", "course_group_task_releases",
                    "course_group_flexible_topic_releases", "course_group_flexible_task_releases")) {
                if (tableExists(c, table)) add(groups, "course_groups", joinedCount(c,
                    "SELECT COUNT(*) FROM " + quote(table) + " r JOIN course_groups g ON g.id=r.course_group WHERE g.subject=?", id));
            }
        }
        if (tableExists(c, "taskstats") && tableExists(c, "tasks") && tableExists(c, "topics"))
            add(groups, "learning_history", joinedCount(c, "SELECT COUNT(*) FROM taskstats s JOIN tasks t ON t.id=s.task JOIN topics p ON p.id=t.topic WHERE p.subject=?", id));
        if (tableExists(c, "completed_flexible_tasks") && tableExists(c, "flexible_tasks"))
            add(groups, "learning_history", joinedCount(c, "SELECT COUNT(*) FROM completed_flexible_tasks c JOIN flexible_tasks t ON t.id=c.flexible_task WHERE t.subject=?", id));
        if (tableExists(c, "completed_individual_tasks") && tableExists(c, "individual_tasks"))
            add(groups, "learning_history", joinedCount(c, "SELECT COUNT(*) FROM completed_individual_tasks x JOIN individual_tasks t ON t.id=x.individual_task WHERE t.subject_id=?", id));
        if (tableExists(c, "completed_unscheduled_tasks") && tableExists(c, "unscheduled_tasks"))
            add(groups, "learning_history", joinedCount(c, "SELECT COUNT(*) FROM completed_unscheduled_tasks x JOIN unscheduled_tasks t ON t.id=x.unscheduled_task WHERE t.subject=?", id));
        if (tableExists(c, "curriculum_completion_transfers") && tableExists(c, "flexible_tasks"))
            add(groups, "learning_history", joinedCount(c, "SELECT COUNT(DISTINCT x.rowid) FROM curriculum_completion_transfers x JOIN flexible_tasks s ON s.id=x.source_task LEFT JOIN flexible_tasks t ON t.id=x.target_task WHERE s.subject=? OR t.subject=?", id, id));
    }

    private static void addTeacherLogicalReferences(Connection c, int id, Map<String, Long> groups) throws SQLException {
        String principal = "TEACHER:" + id;
        if (tableExists(c, "attendance_teacher_preferences") && columnExists(c, "attendance_teacher_preferences", "principal"))
            add(groups, "attendance", scalarText(c, "SELECT COUNT(*) FROM attendance_teacher_preferences WHERE principal=?", principal));
        if (tableExists(c, "attendance_events") && columnExists(c, "attendance_events", "confirmed_by"))
            add(groups, "attendance", scalarText(c, "SELECT COUNT(*) FROM attendance_events WHERE confirmed_by=?", principal));
        // PM identity nodes are exact account-bound technical rows and are removed transactionally
        // only after all pedagogical references are clear; they are not deletion blockers.
    }

    private static boolean accountCollision(Connection c, String email) throws SQLException {
        return (tableExists(c, "admins") && scalarText(c, "SELECT COUNT(*) FROM admins WHERE username=?", email) > 0)
            || (tableExists(c, "students") && scalarText(c, "SELECT COUNT(*) FROM students WHERE email=?", email) > 0);
    }

    private static String teacherEmail(Connection c, int id) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement("SELECT email FROM teachers WHERE id=?")) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getString(1) : null; }
        }
    }

    private static boolean exists(Connection c, String table, int id) throws SQLException {
        return scalar(c, "SELECT COUNT(*) FROM " + quote(table) + " WHERE id=?", id) > 0;
    }

    private static long scalar(Connection c, String sql, int id) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, id);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        }
    }

    private static long scalarText(Connection c, String sql, String value) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setString(1, value);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        }
    }

    private static long joinedCount(Connection c, String sql, int id) throws SQLException { return scalar(c, sql, id); }
    private static long joinedCount(Connection c, String sql, int first, int second) throws SQLException {
        try (PreparedStatement statement = c.prepareStatement(sql)) {
            statement.setInt(1, first);
            statement.setInt(2, second);
            try (ResultSet rows = statement.executeQuery()) { return rows.next() ? rows.getLong(1) : 0; }
        }
    }

    private static void add(Map<String, Long> counts, String key, long value) {
        if (value > 0) counts.merge(key, value, Long::sum);
    }

    private static List<String> tableNames(Connection c) throws SQLException {
        List<String> names = new ArrayList<>();
        try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'sqlite_%'")) {
            while (rows.next()) names.add(rows.getString(1));
        }
        return names;
    }

    private static boolean tableExists(Connection c, String name) throws SQLException {
        return scalarText(c, "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?", name) > 0;
    }

    private static boolean columnExists(Connection c, String table, String column) throws SQLException {
        try (Statement statement = c.createStatement(); ResultSet rows = statement.executeQuery("PRAGMA table_info(" + quote(table) + ")")) {
            while (rows.next()) if (column.equalsIgnoreCase(rows.getString("name"))) return true;
            return false;
        }
    }

    private static void deleteAccountRows(Connection c, String table, String column, String email) throws SQLException {
        if (tableExists(c, table) && columnExists(c, table, column)) {
            try (PreparedStatement statement = c.prepareStatement("DELETE FROM " + quote(table) + " WHERE " + quote(column) + "=?")) {
                statement.setString(1, email);
                statement.executeUpdate();
            }
        }
    }

    private static String quote(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }

    private static String classify(String table, boolean teacher) {
        String name = table.toLowerCase();
        if (name.startsWith("course_group")) return "course_groups";
        if (name.contains("assessment") || name.contains("completion") || name.contains("taskstats") || name.contains("active_curriculum") || name.contains("requests") || name.contains("history")) return "learning_history";
        if (name.contains("flexible")) return "flexible_content";
        if (name.contains("release")) return "releases";
        if (name.contains("context")) return "curriculum_contexts";
        if (name.contains("tutor")) return "tutor_assignments";
        if (name.contains("teacher") || name.contains("gradesubject") || name.contains("student_subject")) return "legacy_assignments";
        if (name.contains("topic") || name.contains("task")) return teacher ? "curriculum_and_content" : "curriculum_content";
        if (name.contains("assignment") || name.contains("grade_subject")) return "curriculum_assignments";
        return "other_references";
    }

    private static String label(String key) {
        return switch (key) {
            case "course_groups" -> "Lerngruppen und zugehörige Zuordnungen";
            case "learning_history" -> "Gespeicherte Lernstände und Leistungshistorie";
            case "flexible_content" -> "Flexible Inhalte und Eigentümerschaft";
            case "releases" -> "Freigaben";
            case "curriculum_contexts" -> "Curriculum-Kontexte";
            case "tutor_assignments" -> "Tutorzuordnungen";
            case "legacy_assignments" -> "Unterrichts- und Legacy-Zuordnungen";
            case "curriculum_assignments" -> "Curriculum-Zuordnungen";
            case "curriculum_content" -> "Curriculum-Inhalte";
            case "curriculum_and_content" -> "Curriculum-Inhalte und Freigaben";
            case "attendance" -> "Anwesenheitsdaten";
            default -> "Weitere fachliche Verknüpfungen";
        };
    }

    public static final class MissingObjectException extends SQLException {
        public MissingObjectException() { super("not_found"); }
    }
}
