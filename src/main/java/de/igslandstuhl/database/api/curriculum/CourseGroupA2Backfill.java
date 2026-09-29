package de.igslandstuhl.database.api.curriculum;

import java.sql.*;
import java.util.*;

/** Atomic A2 data transition. Regular rows are never touched. */
public final class CourseGroupA2Backfill {
    private CourseGroupA2Backfill() {}
    public static void run(Connection c) throws SQLException {
        c.setAutoCommit(false);
        try {
            preflightFlexibleDuplicates(c);
            backfillFlexible(c);
            migrateCentralReleases(c);
            migrateFlexibleReleases(c);
            // Deliberately last: legacy data has passed the duplicate/conflict preflight.
            Curriculum.write(c,"CREATE UNIQUE INDEX IF NOT EXISTS uq_flexible_topics_course_group_name ON flexible_topics(course_group,name) WHERE course_group IS NOT NULL");
            Curriculum.write(c,"CREATE UNIQUE INDEX IF NOT EXISTS uq_flexible_tasks_course_group_name ON flexible_tasks(course_group,name) WHERE course_group IS NOT NULL");
            c.commit();
        } catch(SQLException|RuntimeException e) { try { c.rollback(); } catch(SQLException ignored) {} throw e; }
        finally { c.setAutoCommit(true); }
    }
    private static void preflightFlexibleDuplicates(Connection c) throws SQLException {
        for (String table : List.of("flexible_topics","flexible_tasks")) {
            String sql="SELECT course_group,name,COUNT(*) AS n FROM "+table+" WHERE course_group IS NOT NULL GROUP BY course_group,name HAVING COUNT(*)>1";
            for (var row:Curriculum.rows(c,sql))
                throw Curriculum.error(409,"flexible_duplicate_conflict",table+" duplicate for course_group "+Curriculum.integer(row,"course_group")+" and name '"+row.get("name")+"'.");
        }
        for (var row:Curriculum.rows(c,"SELECT t.course_group,t.name,COUNT(*) AS n FROM flexible_tasks t JOIN flexible_task_topics m ON m.flexible_task=t.id WHERE t.course_group IS NOT NULL GROUP BY t.course_group,t.name HAVING COUNT(DISTINCT m.flexible_topic)>1"))
            throw Curriculum.error(409,"flexible_topic_conflict","Flexible task duplicate topic mapping for course_group "+Curriculum.integer(row,"course_group")+" and name '"+row.get("name")+"'.");
    }
    private static CourseGroup group(Connection c,int subject,int grade,int semester,int teacher) throws SQLException {
        CourseGroup g=CourseGroup.resolve(c,subject,grade,semester);
        if(g.teacherId()!=teacher) throw Curriculum.error(409,"course_group_teacher_conflict","Individual row teacher does not match its course group.");
        return g;
    }
    private static void backfillFlexible(Connection c) throws SQLException {
        for(var r:Curriculum.rows(c,"SELECT id,owner_teacher,subject,semester,grade FROM flexible_topics WHERE course_group IS NULL")) {
            if(!Curriculum.individualSubject(c,Curriculum.integer(r,"subject"))) continue;
            CourseGroup g=group(c,Curriculum.integer(r,"subject"),Curriculum.integer(r,"grade"),Curriculum.integer(r,"semester"),Curriculum.integer(r,"owner_teacher"));
            Curriculum.write(c,"UPDATE flexible_topics SET course_group=? WHERE id=?",g.id(),Curriculum.integer(r,"id"));
        }
        for(var r:Curriculum.rows(c,"SELECT id,owner_teacher,subject,semester,grade FROM flexible_tasks WHERE course_group IS NULL")) {
            if(!Curriculum.individualSubject(c,Curriculum.integer(r,"subject"))) continue;
            CourseGroup g=group(c,Curriculum.integer(r,"subject"),Curriculum.integer(r,"grade"),Curriculum.integer(r,"semester"),Curriculum.integer(r,"owner_teacher"));
            Curriculum.write(c,"UPDATE flexible_tasks SET course_group=? WHERE id=?",g.id(),Curriculum.integer(r,"id"));
        }
        for(var r:Curriculum.rows(c,"SELECT t.id,t.course_group,p.course_group AS topic_group FROM flexible_tasks t JOIN flexible_task_topics m ON m.flexible_task=t.id JOIN flexible_topics p ON p.id=m.flexible_topic WHERE t.course_group IS NOT NULL AND p.course_group IS NOT NULL AND t.course_group<>p.course_group"))
            throw Curriculum.error(409,"course_group_conflict","Flexible task and topic belong to different course groups.");
    }
    private static void migrateCentralReleases(Connection c) throws SQLException {
        migrateReleaseTable(c,"curriculum_topic_releases","topic","topics","course_group_topic_releases");
        migrateReleaseTable(c,"curriculum_task_releases","task","tasks","course_group_task_releases");
    }
    private static void migrateFlexibleReleases(Connection c) throws SQLException {
        migrateFlexibleRelease(c,"flexible_topic_releases","flexible_topic","course_group_flexible_topic_releases","flexible_topics");
        migrateFlexibleRelease(c,"flexible_task_releases","flexible_task","course_group_flexible_task_releases","flexible_tasks");
    }
    private static void migrateReleaseTable(Connection c,String source,String stageColumn,String stageTable,String target) throws SQLException {
        Map<String,Set<Integer>> seen=new HashMap<>();
        for(var r:Curriculum.rows(c,"SELECT r."+stageColumn+" AS stage,r.active,x.subject,x.semester,cl.grade,r.teacher FROM "+source+" r JOIN "+stageTable+" x ON x.id=r."+stageColumn+" JOIN classes cl ON cl.id=r.class JOIN curriculum_subject_types st ON st.subject=x.subject AND st.mode='INDIVIDUAL'")) {
            CourseGroup g=group(c,Curriculum.integer(r,"subject"),Curriculum.integer(r,"grade"),Curriculum.integer(r,"semester"),Curriculum.integer(r,"teacher"));
            String key=g.id()+":"+Curriculum.integer(r,"stage"); Set<Integer> vals=seen.computeIfAbsent(key,k->new HashSet<>()); vals.add(Curriculum.integer(r,"active"));
            if(vals.size()>1) throw Curriculum.error(409,"release_conflict","Individual class releases disagree within one course group.");
            Curriculum.write(c,"INSERT INTO "+target+"(course_group,"+stageColumn+",active) VALUES(?,?,?) ON CONFLICT(course_group,"+stageColumn+") DO UPDATE SET active=excluded.active",g.id(),Curriculum.integer(r,"stage"),Curriculum.integer(r,"active"));
        }
        Curriculum.write(c,"DELETE FROM "+source+" WHERE subject IN (SELECT subject FROM curriculum_subject_types WHERE mode='INDIVIDUAL')");
    }
    private static void migrateFlexibleRelease(Connection c,String source,String stageColumn,String target,String stageTable) throws SQLException {
        Map<String,Set<Integer>> seen=new HashMap<>();
        for(var r:Curriculum.rows(c,"SELECT r."+stageColumn+" AS stage,r.active,x.course_group FROM "+source+" r JOIN "+stageTable+" x ON x.id=r."+stageColumn+" JOIN curriculum_subject_types st ON st.subject=x.subject AND st.mode='INDIVIDUAL'")) {
            if(r.get("course_group")==null) throw Curriculum.error(409,"course_group_missing","Individual flexible release has no course group.");
            String key=Curriculum.integer(r,"course_group")+":"+Curriculum.integer(r,"stage"); Set<Integer> vals=seen.computeIfAbsent(key,k->new HashSet<>()); vals.add(Curriculum.integer(r,"active"));
            if(vals.size()>1) throw Curriculum.error(409,"release_conflict","Individual flexible releases disagree within one course group.");
            Curriculum.write(c,"INSERT INTO "+target+"(course_group,"+stageColumn+",active) VALUES(?,?,?) ON CONFLICT(course_group,"+stageColumn+") DO UPDATE SET active=excluded.active",Curriculum.integer(r,"course_group"),Curriculum.integer(r,"stage"),Curriculum.integer(r,"active"));
        }
        Curriculum.write(c,"DELETE FROM "+source+" WHERE subject IN (SELECT subject FROM curriculum_subject_types WHERE mode='INDIVIDUAL')");
    }
}
