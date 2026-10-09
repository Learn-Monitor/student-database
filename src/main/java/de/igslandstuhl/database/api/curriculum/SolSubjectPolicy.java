package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.Subject;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The single server-side definition of the currently active SOL subject set
 * for grades 5 and 6.  Historical rows remain untouched; this policy is used
 * for new operational assignments and current-subject read models.
 */
final class SolSubjectPolicy {
    static final String WPF = "WPF";
    private static final Set<String> CORE = Set.of(
            "Deutsch", "Englisch", "Mathematik", "Naturwissenschaften", "Gesellschaftslehre");

    private SolSubjectPolicy() {}

    static boolean isManagedGrade(int grade) {
        return grade == 5 || grade == 6;
    }

    static boolean isAllowed(Connection c, int grade, int subject) throws SQLException {
        if (!isManagedGrade(grade)) return true;
        var row = Curriculum.require(c,
                "SELECT s.name,COALESCE(t.mode,'REGULAR') AS mode,t.assignment_group "
                        + "FROM subjects s LEFT JOIN curriculum_subject_types t ON t.subject=s.id WHERE s.id=?", subject);
        String mode = String.valueOf(row.get("mode"));
        String group = row.get("assignment_group") == null ? null
                : String.valueOf(row.get("assignment_group")).strip().toUpperCase(Locale.ROOT);
        if ("INDIVIDUAL".equals(mode)) return grade == 6 && WPF.equals(group);
        return !isManagedGrade(grade) || CORE.contains(Subject.displayName(String.valueOf(row.get("name"))));
    }

    static void requireAllowed(Connection c, int grade, int subject) throws SQLException {
        if (!isAllowed(c, grade, subject))
            throw Curriculum.error(400, "subject_not_allowed",
                    "Für Jahrgang 5/6 ist dieses Fach im aktuellen SOL-Fachumfang nicht zulässig.");
    }

    static void requireCurrentAllowed(Connection c, int grade, int subject, int semester) throws SQLException {
        if (isManagedGrade(grade)
                && Curriculum.number(c,"SELECT COUNT(*) FROM school_years WHERE current_semester=?",semester)>0)
            requireAllowed(c,grade,subject);
    }

    static List<Integer> activeSubjectIds(Connection c, int grade) throws SQLException {
        if (!isManagedGrade(grade)) return List.of();
        Set<Integer> ids = new LinkedHashSet<>();
        for (var row : Curriculum.rows(c, "SELECT id FROM subjects ORDER BY id")) {
            int subject = Curriculum.integer(row, "id");
            if (isAllowed(c, grade, subject)) ids.add(subject);
        }
        return List.copyOf(ids);
    }
}
