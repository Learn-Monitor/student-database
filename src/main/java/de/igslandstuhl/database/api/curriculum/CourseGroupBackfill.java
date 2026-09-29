package de.igslandstuhl.database.api.curriculum;

import java.sql.*;
import java.util.*;

/** Deterministic, idempotent backfill for the additive CourseGroup schema. */
public final class CourseGroupBackfill {
    private CourseGroupBackfill() {}
    public static int run(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try {
            var contexts = Curriculum.rows(c, "SELECT DISTINCT x.subject,x.grade,x.semester FROM student_curriculum_contexts x JOIN curriculum_subject_types st ON st.subject=x.subject AND st.mode='INDIVIDUAL'");
            var assignments = Curriculum.rows(c, "SELECT DISTINCT a.subject,cl.grade,a.semester FROM curriculum_individual_assignments a JOIN students s ON s.id=a.student JOIN classes cl ON cl.id=s.class JOIN curriculum_subject_types st ON st.subject=a.subject AND st.mode='INDIVIDUAL'");
            Set<String> keys=new LinkedHashSet<>();
            for (var r:contexts) keys.add(key(r));
            for (var r:assignments) keys.add(key(r));
            int groups=0;
            for (String k:keys) {
                String[] p=k.split(":"); int subject=Integer.parseInt(p[0]), grade=Integer.parseInt(p[1]), semester=Integer.parseInt(p[2]);
                var st=Curriculum.rows(c,"SELECT assignment_group FROM curriculum_subject_types WHERE subject=? AND mode='INDIVIDUAL'",subject);
                if(st.size()!=1 || st.get(0).get("assignment_group")==null) throw Curriculum.error(409,"course_group_subject_type_conflict","Individual subject has no unique assignment group.");
                var teachers=Curriculum.rows(c,"SELECT DISTINCT teacher FROM curriculum_grade_teachers WHERE subject=? AND grade=? AND semester=?",subject,grade,semester);
                if(teachers.size()!=1) throw Curriculum.error(409,"course_group_teacher_conflict","Individual subject context requires exactly one grade teacher.");
                var subjectRow=Curriculum.require(c,"SELECT name FROM subjects WHERE id=?",subject);
                Curriculum.write(c,"INSERT INTO course_groups(subject,grade,semester,teacher,assignment_group,name) VALUES(?,?,?,?,?,?) ON CONFLICT(subject,grade,semester) DO UPDATE SET teacher=excluded.teacher,assignment_group=excluded.assignment_group,name=excluded.name",subject,grade,semester,Curriculum.integer(teachers.get(0),"teacher"),String.valueOf(st.get(0).get("assignment_group")),String.valueOf(subjectRow.get("name")));
                groups++;
            }
            for(var r:Curriculum.rows(c,"SELECT x.student,x.subject,x.semester,x.teacher,x.grade FROM student_curriculum_contexts x JOIN curriculum_subject_types st ON st.subject=x.subject AND st.mode='INDIVIDUAL'")) {
                CourseGroup g=CourseGroup.resolve(c,Curriculum.integer(r,"subject"),Curriculum.integer(r,"grade"),Curriculum.integer(r,"semester"));
                if(Curriculum.integer(r,"teacher")!=g.teacherId()) throw Curriculum.error(409,"course_group_context_conflict","Context teacher does not match course group teacher.");
                Curriculum.write(c,"INSERT INTO course_group_members(course_group,student) VALUES(?,?) ON CONFLICT DO NOTHING",g.id(),Curriculum.integer(r,"student"));
                Curriculum.write(c,"UPDATE student_curriculum_contexts SET course_group=? WHERE student=? AND subject=? AND semester=?",g.id(),Curriculum.integer(r,"student"),Curriculum.integer(r,"subject"),Curriculum.integer(r,"semester"));
            }
            for(var r:Curriculum.rows(c,"SELECT a.student,a.subject,a.semester,cl.grade FROM curriculum_individual_assignments a JOIN students s ON s.id=a.student JOIN classes cl ON cl.id=s.class JOIN curriculum_subject_types st ON st.subject=a.subject AND st.mode='INDIVIDUAL'")) {
                CourseGroup g=CourseGroup.resolve(c,Curriculum.integer(r,"subject"),Curriculum.integer(r,"grade"),Curriculum.integer(r,"semester"));
                Curriculum.write(c,"INSERT INTO course_group_members(course_group,student) VALUES(?,?) ON CONFLICT DO NOTHING",g.id(),Curriculum.integer(r,"student"));
            }
            c.commit(); return groups;
        } catch(SQLException|RuntimeException e) { try { c.rollback(); } catch(SQLException ignored) {} throw e; }
        finally { c.setAutoCommit(true); }
    }
    private static String key(Map<String,Object> r){return Curriculum.integer(r,"subject")+":"+Curriculum.integer(r,"grade")+":"+Curriculum.integer(r,"semester");}
}
