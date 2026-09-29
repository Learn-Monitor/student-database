package de.igslandstuhl.database.api.curriculum;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** The semester- and grade-scoped identity of an INDIVIDUAL subject. */
public record CourseGroup(int id, int subjectId, int grade, int semesterId,
                          int teacherId, String assignmentGroup, String name, boolean active) {
    static CourseGroup from(Map<String,Object> row) {
        return new CourseGroup(((Number) row.get("id")).intValue(), ((Number) row.get("subject")).intValue(),
                ((Number) row.get("grade")).intValue(), ((Number) row.get("semester")).intValue(),
                ((Number) row.get("teacher")).intValue(), String.valueOf(row.get("assignment_group")),
                String.valueOf(row.get("name")), ((Number) row.get("active")).intValue()!=0);
    }
    public static CourseGroup resolve(Connection c, int subject, int grade, int semester) throws SQLException {
        var rows = Curriculum.rows(c, "SELECT id,subject,grade,semester,teacher,assignment_group,name,active FROM course_groups WHERE subject=? AND grade=? AND semester=?", subject, grade, semester);
        if (rows.isEmpty()) throw Curriculum.error(409, "course_group_missing", "No course group exists for this individual subject context.");
        if (rows.size()!=1) throw Curriculum.error(409, "course_group_conflict", "More than one course group exists for this subject context.");
        return from(rows.get(0));
    }
    public static CourseGroup resolveForStudent(Connection c, int student, int subject, int semester) throws SQLException {
        var rows = Curriculum.rows(c, "SELECT g.id,g.subject,g.grade,g.semester,g.teacher,g.assignment_group,g.name,g.active FROM course_groups g JOIN course_group_members m ON m.course_group=g.id WHERE m.student=? AND g.subject=? AND g.semester=?", student, subject, semester);
        if (rows.isEmpty()) throw Curriculum.error(403, "course_group_membership_required", "Student is not a member of this course group.");
        if (rows.size()!=1) throw Curriculum.error(409, "course_group_conflict", "Student has more than one course group for this subject context.");
        return from(rows.get(0));
    }
    /** Creates the next-semester group without reusing the previous group's identity. */
    public static CourseGroup copyForSemester(Connection c, CourseGroup source, int semesterId) throws SQLException {
        Curriculum.write(c,"INSERT INTO course_groups(subject,grade,semester,teacher,assignment_group,name,active) VALUES(?,?,?,?,?,?,?) ON CONFLICT(subject,grade,semester) DO NOTHING",
                source.subjectId(),source.grade(),semesterId,source.teacherId(),source.assignmentGroup(),source.name(),source.active()?1:0);
        return resolve(c,source.subjectId(),source.grade(),semesterId);
    }
}
