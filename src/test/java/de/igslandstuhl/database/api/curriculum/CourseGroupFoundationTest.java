package de.igslandstuhl.database.api.curriculum;

import org.junit.jupiter.api.Test;
import java.sql.*;
import static org.junit.jupiter.api.Assertions.*;

/** Focused A1 tests for the additive schema, resolver, and deterministic backfill. */
class CourseGroupFoundationTest {
    @Test void migrationObjectsExistAndAreReplaySafe() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            c.createStatement().execute("CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT); CREATE TABLE semesters(id INTEGER PRIMARY KEY); CREATE TABLE teachers(id INTEGER PRIMARY KEY); CREATE TABLE students(id INTEGER PRIMARY KEY); CREATE TABLE classes(id INTEGER PRIMARY KEY,grade INTEGER); CREATE TABLE topics(id INTEGER PRIMARY KEY); CREATE TABLE tasks(id INTEGER PRIMARY KEY); CREATE TABLE curriculum_subject_types(subject INTEGER,mode TEXT,assignment_group TEXT); CREATE TABLE curriculum_grade_teachers(semester INTEGER,grade INTEGER,subject INTEGER,teacher INTEGER); CREATE TABLE student_curriculum_contexts(student INTEGER,subject INTEGER,semester INTEGER,teacher INTEGER,class INTEGER,grade INTEGER,course_group INTEGER); CREATE TABLE curriculum_individual_assignments(student INTEGER,semester INTEGER,assignment_group TEXT,subject INTEGER); CREATE TABLE flexible_topics(id INTEGER,subject INTEGER,grade INTEGER,semester INTEGER,owner_teacher INTEGER,class INTEGER); CREATE TABLE flexible_tasks(id INTEGER,subject INTEGER,grade INTEGER,semester INTEGER,owner_teacher INTEGER,class INTEGER);");
            c.createStatement().execute("CREATE TABLE course_groups(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL,grade INTEGER NOT NULL,semester INTEGER NOT NULL,teacher INTEGER NOT NULL,assignment_group TEXT NOT NULL,name TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,UNIQUE(subject,grade,semester)); CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student)); CREATE TABLE course_group_topic_releases(course_group INTEGER,topic INTEGER,active INTEGER,PRIMARY KEY(course_group,topic)); CREATE TABLE course_group_task_releases(course_group INTEGER,task INTEGER,active INTEGER,PRIMARY KEY(course_group,task)); CREATE TABLE course_group_flexible_topic_releases(course_group INTEGER,flexible_topic INTEGER,active INTEGER,PRIMARY KEY(course_group,flexible_topic)); CREATE TABLE course_group_flexible_task_releases(course_group INTEGER,flexible_task INTEGER,active INTEGER,PRIMARY KEY(course_group,flexible_task));");
            assertEquals(6,c.getMetaData().getTables(null,null,"course_groups",null).next()?6:0);
            assertDoesNotThrow(()->c.createStatement().execute("CREATE TABLE IF NOT EXISTS course_groups(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER,teacher INTEGER,assignment_group TEXT,name TEXT,active INTEGER,UNIQUE(subject,grade,semester))"));
        }
    }

    @Test void backfillCreatesOneGroupForMembersFromDifferentClassesAndIsIdempotent() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            for(String sql: new String[]{
                    "CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT)","CREATE TABLE semesters(id INTEGER PRIMARY KEY)",
                    "CREATE TABLE teachers(id INTEGER PRIMARY KEY)","CREATE TABLE students(id INTEGER PRIMARY KEY,class INTEGER)",
                    "CREATE TABLE classes(id INTEGER PRIMARY KEY,grade INTEGER)","CREATE TABLE curriculum_subject_types(subject INTEGER,mode TEXT,assignment_group TEXT)",
                    "CREATE TABLE curriculum_grade_teachers(semester INTEGER,grade INTEGER,subject INTEGER,teacher INTEGER)",
                    "CREATE TABLE student_curriculum_contexts(student INTEGER,subject INTEGER,semester INTEGER,teacher INTEGER,class INTEGER,grade INTEGER,course_group INTEGER)",
                    "CREATE TABLE curriculum_individual_assignments(student INTEGER,semester INTEGER,assignment_group TEXT,subject INTEGER)",
                    "CREATE TABLE course_groups(id INTEGER PRIMARY KEY,subject INTEGER NOT NULL,grade INTEGER NOT NULL,semester INTEGER NOT NULL,teacher INTEGER NOT NULL,assignment_group TEXT NOT NULL,name TEXT NOT NULL,active INTEGER NOT NULL DEFAULT 1,UNIQUE(subject,grade,semester))",
                    "CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))"}) c.createStatement().execute(sql);
            exec(c,"INSERT INTO subjects VALUES(1,'WPF Test')"); exec(c,"INSERT INTO semesters VALUES(10)"); exec(c,"INSERT INTO teachers VALUES(20)");
            exec(c,"INSERT INTO classes VALUES(30,6)"); exec(c,"INSERT INTO classes VALUES(31,6)"); exec(c,"INSERT INTO students VALUES(40,30)"); exec(c,"INSERT INTO students VALUES(41,31)");
            exec(c,"INSERT INTO curriculum_subject_types VALUES(1,'INDIVIDUAL','WPF')"); exec(c,"INSERT INTO curriculum_grade_teachers VALUES(10,6,1,20)");
            exec(c,"INSERT INTO student_curriculum_contexts VALUES(40,1,10,20,30,6,NULL)"); exec(c,"INSERT INTO student_curriculum_contexts VALUES(41,1,10,20,31,6,NULL)");
            exec(c,"INSERT INTO curriculum_individual_assignments VALUES(40,10,'WPF',1)"); exec(c,"INSERT INTO curriculum_individual_assignments VALUES(41,10,'WPF',1)");
            assertEquals(1,CourseGroupBackfill.run(c)); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_groups")); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM course_group_members")); assertEquals(1,scalar(c,"SELECT COUNT(DISTINCT course_group) FROM student_curriculum_contexts"));
            assertEquals(1,CourseGroupBackfill.run(c)); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_groups")); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM course_group_members"));
            assertEquals(30,scalar(c,"SELECT class FROM student_curriculum_contexts WHERE student=40")); assertEquals(31,scalar(c,"SELECT class FROM student_curriculum_contexts WHERE student=41"));
        }
    }
    static void exec(Connection c,String sql,Object...a)throws SQLException {try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);p.executeUpdate();}}
    static long scalar(Connection c,String sql,Object...a)throws SQLException {try(PreparedStatement p=c.prepareStatement(sql)){for(int i=0;i<a.length;i++)p.setObject(i+1,a[i]);try(ResultSet r=p.executeQuery()){assertTrue(r.next());return r.getLong(1);}}}

    @Test void resolverKeepsRegularClassScopeAndResolvesIndividualGroup() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            exec(c,"CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT)"); exec(c,"CREATE TABLE classes(id INTEGER PRIMARY KEY,grade INTEGER)"); exec(c,"CREATE TABLE curriculum_subject_types(subject INTEGER,mode TEXT,assignment_group TEXT)");
            exec(c,"CREATE TABLE course_groups(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER,teacher INTEGER,assignment_group TEXT,name TEXT,active INTEGER)");
            exec(c,"INSERT INTO subjects VALUES(1,'Regular'),(2,'WPF')"); exec(c,"INSERT INTO classes VALUES(10,6)"); exec(c,"INSERT INTO curriculum_subject_types VALUES(2,'INDIVIDUAL','WPF')");
            exec(c,"INSERT INTO course_groups VALUES(7,2,6,20,30,'WPF','WPF',1)");
            var regular=Curriculum.resolveScope(c,new Curriculum.Scope(30,1,10,20)); assertNull(regular.courseGroupId()); assertEquals("REGULAR",regular.mode());
            var individual=Curriculum.resolveScope(c,new Curriculum.Scope(30,2,10,20)); assertEquals(7,individual.courseGroupId()); assertEquals(6,individual.grade());
            assertThrows(CurriculumException.class,()->Curriculum.resolveScope(c,new Curriculum.Scope(31,2,10,20)));
        }
    }
}
