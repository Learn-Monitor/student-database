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

    @Test void a2DuplicatePreflightRollsBackBeforeIndexCreation() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            exec(c,"CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT)"); exec(c,"CREATE TABLE semesters(id INTEGER PRIMARY KEY)"); exec(c,"CREATE TABLE teachers(id INTEGER PRIMARY KEY)"); exec(c,"CREATE TABLE classes(id INTEGER PRIMARY KEY,grade INTEGER)");
            exec(c,"CREATE TABLE curriculum_subject_types(subject INTEGER,mode TEXT,assignment_group TEXT)"); exec(c,"CREATE TABLE curriculum_grade_teachers(semester INTEGER,grade INTEGER,subject INTEGER,teacher INTEGER)");
            exec(c,"CREATE TABLE students(id INTEGER PRIMARY KEY,class INTEGER)"); exec(c,"CREATE TABLE student_curriculum_contexts(student INTEGER,subject INTEGER,semester INTEGER,teacher INTEGER,class INTEGER,grade INTEGER,course_group INTEGER)"); exec(c,"CREATE TABLE curriculum_individual_assignments(student INTEGER,semester INTEGER,assignment_group TEXT,subject INTEGER)");
            exec(c,"CREATE TABLE course_groups(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER,teacher INTEGER,assignment_group TEXT,name TEXT,active INTEGER,UNIQUE(subject,grade,semester))"); exec(c,"CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))");
            exec(c,"CREATE TABLE flexible_topics(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,course_group INTEGER)"); exec(c,"CREATE TABLE flexible_tasks(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,tokens INTEGER,course_group INTEGER)");
            exec(c,"CREATE TABLE flexible_task_topics(flexible_task INTEGER,flexible_topic INTEGER)"); exec(c,"CREATE TABLE curriculum_topic_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,topic INTEGER,active INTEGER)"); exec(c,"CREATE TABLE curriculum_task_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,task INTEGER,active INTEGER)");
            exec(c,"CREATE TABLE flexible_topic_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,flexible_topic INTEGER,active INTEGER)"); exec(c,"CREATE TABLE flexible_task_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,flexible_task INTEGER,active INTEGER)");
            exec(c,"CREATE TABLE course_group_topic_releases(course_group INTEGER,topic INTEGER,active INTEGER,PRIMARY KEY(course_group,topic))"); exec(c,"CREATE TABLE course_group_task_releases(course_group INTEGER,task INTEGER,active INTEGER,PRIMARY KEY(course_group,task))"); exec(c,"CREATE TABLE course_group_flexible_topic_releases(course_group INTEGER,flexible_topic INTEGER,active INTEGER,PRIMARY KEY(course_group,flexible_topic))"); exec(c,"CREATE TABLE course_group_flexible_task_releases(course_group INTEGER,flexible_task INTEGER,active INTEGER,PRIMARY KEY(course_group,flexible_task))"); exec(c,"CREATE TABLE topics(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER)"); exec(c,"CREATE TABLE tasks(id INTEGER PRIMARY KEY,topic INTEGER)");
            exec(c,"INSERT INTO subjects VALUES(1,'WPF Test')"); exec(c,"INSERT INTO semesters VALUES(10)"); exec(c,"INSERT INTO teachers VALUES(20)"); exec(c,"INSERT INTO classes VALUES(30,6)"); exec(c,"INSERT INTO curriculum_subject_types VALUES(1,'INDIVIDUAL','WPF')"); exec(c,"INSERT INTO curriculum_grade_teachers VALUES(10,6,1,20)");
            exec(c,"INSERT INTO flexible_tasks VALUES(1,20,1,30,10,6,'Collision',3,NULL)"); exec(c,"INSERT INTO flexible_tasks VALUES(2,20,1,10,10,6,'Collision',4,NULL)");
            assertThrows(CurriculumException.class,()->CourseGroupA2Backfill.run(c));
            assertEquals(0,scalar(c,"SELECT COUNT(*) FROM flexible_tasks WHERE course_group IS NOT NULL"));
            assertEquals(0,scalar(c,"SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='uq_flexible_tasks_course_group_name'"));
        }
    }

    @Test void centralGroupReleaseIsSharedAcrossThreeHomeClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c);
            exec(c,"INSERT INTO course_group_topic_releases VALUES(7,100,1)");
            assertTrue(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,30,10),100));
            assertTrue(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,31,10),100));
            assertTrue(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,32,10),100));
            exec(c,"INSERT INTO course_groups VALUES(8,1,6,11,20,'WPF','Other',1)");
            assertFalse(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,30,11),100));
        }
    }

    @Test void centralTaskOverrideIsSharedAndOverridesGroupTopicRelease() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"INSERT INTO topics VALUES(100,1,6,10)"); exec(c,"INSERT INTO tasks VALUES(200,100)");
            exec(c,"INSERT INTO course_group_topic_releases VALUES(7,100,1)"); exec(c,"INSERT INTO course_group_task_releases VALUES(7,200,0)");
            assertTrue(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,30,10),100));
            assertFalse(CurriculumEnrollment.released(c,new Curriculum.Scope(20,1,31,10),200,100));
        }
    }

    @Test void flexibleGroupReleaseReturnsSameIdsAndStateAcrossClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c);
            exec(c,"CREATE TABLE flexible_topics(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,course_group INTEGER)");
            exec(c,"CREATE TABLE flexible_tasks(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,tokens INTEGER,course_group INTEGER)");
            exec(c,"INSERT INTO flexible_topics VALUES(300,20,1,30,10,6,'Flex',7)"); exec(c,"INSERT INTO flexible_tasks VALUES(301,20,1,30,10,6,'Flex task',10,7)");
            exec(c,"INSERT INTO course_group_flexible_topic_releases VALUES(7,300,1)"); exec(c,"INSERT INTO course_group_flexible_task_releases VALUES(7,301,1)");
            assertTrue(CurriculumEnrollment.flexibleTopicReleased(c,new Curriculum.Scope(20,1,31,10),300));
            assertTrue(CurriculumEnrollment.flexibleReleased(c,new Curriculum.Scope(20,1,31,10),301,300));
            assertEquals(300,scalar(c,"SELECT id FROM flexible_topics WHERE course_group=7")); assertEquals(301,scalar(c,"SELECT id FROM flexible_tasks WHERE course_group=7"));
        }
    }

    @Test void individualBudgetIsEqualForThreeHomeClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"CREATE TABLE flexible_tasks(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,tokens INTEGER,course_group INTEGER)");
            exec(c,"INSERT INTO flexible_tasks VALUES(301,20,1,30,10,6,'Flex',10,7)");
            assertEquals(10,Curriculum.flexible(c,new Curriculum.Scope(20,1,30,10)));
            assertEquals(10,Curriculum.flexible(c,new Curriculum.Scope(20,1,31,10)));
            assertEquals(10,Curriculum.flexible(c,new Curriculum.Scope(20,1,32,10)));
        }
    }

    @Test void crossClassCatalogInputsResolveToSameCourseGroupData() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c);
            exec(c,"CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))");
            exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41)");
            var a=CourseGroup.resolveForStudent(c,40,1,10); var b=CourseGroup.resolveForStudent(c,41,1,10);
            assertEquals(a.id(),b.id()); assertEquals(a.subjectId(),b.subjectId()); assertEquals(a.grade(),b.grade()); assertEquals(a.semesterId(),b.semesterId());
        }
    }

    static void releaseSchema(Connection c)throws SQLException {
        exec(c,"CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT)"); exec(c,"CREATE TABLE semesters(id INTEGER PRIMARY KEY)"); exec(c,"CREATE TABLE teachers(id INTEGER PRIMARY KEY)"); exec(c,"CREATE TABLE classes(id INTEGER PRIMARY KEY,grade INTEGER)"); exec(c,"CREATE TABLE curriculum_subject_types(subject INTEGER,mode TEXT,assignment_group TEXT)"); exec(c,"CREATE TABLE curriculum_grade_teachers(semester INTEGER,grade INTEGER,subject INTEGER,teacher INTEGER)");
        exec(c,"CREATE TABLE topics(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER)"); exec(c,"CREATE TABLE tasks(id INTEGER PRIMARY KEY,topic INTEGER)");
        exec(c,"CREATE TABLE course_groups(id INTEGER PRIMARY KEY,subject INTEGER,grade INTEGER,semester INTEGER,teacher INTEGER,assignment_group TEXT,name TEXT,active INTEGER,UNIQUE(subject,grade,semester))");
        exec(c,"CREATE TABLE course_group_topic_releases(course_group INTEGER,topic INTEGER,active INTEGER,PRIMARY KEY(course_group,topic))"); exec(c,"CREATE TABLE course_group_task_releases(course_group INTEGER,task INTEGER,active INTEGER,PRIMARY KEY(course_group,task))");
        exec(c,"CREATE TABLE course_group_flexible_topic_releases(course_group INTEGER,flexible_topic INTEGER,active INTEGER,PRIMARY KEY(course_group,flexible_topic))"); exec(c,"CREATE TABLE course_group_flexible_task_releases(course_group INTEGER,flexible_task INTEGER,active INTEGER,PRIMARY KEY(course_group,flexible_task))");
        exec(c,"CREATE TABLE curriculum_topic_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,topic INTEGER,active INTEGER)"); exec(c,"CREATE TABLE curriculum_task_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,task INTEGER,active INTEGER)");
        exec(c,"CREATE TABLE flexible_topic_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,flexible_topic INTEGER,active INTEGER)"); exec(c,"CREATE TABLE flexible_task_releases(teacher INTEGER,class INTEGER,subject INTEGER,semester INTEGER,flexible_task INTEGER,active INTEGER)");
    }
    static void seedGroup(Connection c)throws SQLException {
        exec(c,"INSERT INTO subjects VALUES(1,'WPF Test')"); exec(c,"INSERT INTO classes VALUES(30,6),(31,6),(32,6)"); exec(c,"INSERT INTO curriculum_subject_types VALUES(1,'INDIVIDUAL','WPF')"); exec(c,"INSERT INTO course_groups VALUES(7,1,6,10,20,'WPF','WPF Test',1)");
    }

    @Test void migratesIdenticalIndividualTopicReleasesToOneCourseGroupRelease() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO curriculum_topic_releases VALUES(20,30,1,10,100,1),(20,31,1,10,100,1)"); CourseGroupA2Backfill.run(c); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_group_topic_releases")); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM curriculum_topic_releases WHERE subject=1")); assertEquals(100,scalar(c,"SELECT topic FROM course_group_topic_releases")); }
    }
    @Test void migratesIdenticalIndividualTaskReleasesToOneCourseGroupRelease() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO curriculum_task_releases VALUES(20,30,1,10,200,1),(20,31,1,10,200,1)"); CourseGroupA2Backfill.run(c); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_group_task_releases")); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM curriculum_task_releases WHERE subject=1")); assertEquals(200,scalar(c,"SELECT task FROM course_group_task_releases")); }
    }
    @Test void migratesIndividualFlexibleTopicReleaseToCourseGroupRelease() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO flexible_topics VALUES(300,20,1,30,10,6,'F',NULL)"); exec(c,"INSERT INTO flexible_topic_releases VALUES(20,30,1,10,300,1)"); CourseGroupA2Backfill.run(c); assertEquals(7,scalar(c,"SELECT course_group FROM flexible_topics")); assertEquals(1,scalar(c,"SELECT active FROM course_group_flexible_topic_releases")); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM flexible_topic_releases WHERE subject=1")); assertEquals(300,scalar(c,"SELECT flexible_topic FROM course_group_flexible_topic_releases")); }
    }
    @Test void migratesIndividualFlexibleTaskReleaseToCourseGroupRelease() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO flexible_tasks VALUES(301,20,1,30,10,6,'F',3,NULL)"); exec(c,"INSERT INTO flexible_task_releases VALUES(20,30,1,10,301,1)"); CourseGroupA2Backfill.run(c); assertEquals(7,scalar(c,"SELECT course_group FROM flexible_tasks")); assertEquals(1,scalar(c,"SELECT active FROM course_group_flexible_task_releases")); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM flexible_task_releases WHERE subject=1")); assertEquals(301,scalar(c,"SELECT flexible_task FROM course_group_flexible_task_releases")); }
    }
    @Test void rejectsConflictingIndividualReleaseStatesBeforeMigration() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO curriculum_topic_releases VALUES(20,30,1,10,100,1),(20,31,1,10,100,0)"); assertThrows(CurriculumException.class,()->CourseGroupA2Backfill.run(c)); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM course_group_topic_releases")); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM curriculum_topic_releases WHERE subject=1")); }
    }
    @Test void rollsBackEntireA2BackfillWhenLateMigrationConflictOccurs() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO flexible_tasks VALUES(301,20,1,30,10,6,'F',3,NULL)"); exec(c,"INSERT INTO curriculum_topic_releases VALUES(20,30,1,10,100,1),(20,31,1,10,100,0)"); assertThrows(CurriculumException.class,()->CourseGroupA2Backfill.run(c)); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM flexible_tasks WHERE course_group IS NOT NULL")); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM course_group_task_releases")); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM curriculum_topic_releases WHERE subject=1")); }
    }

    @Test void rejectsFutureCourseGroupDuplicateTasksWithDifferentTokens() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); duplicateTasks(c,3,4); assertBackfillConflict(c); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM flexible_tasks WHERE course_group IS NOT NULL")); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM flexible_tasks")); }
    }

    @Test void rejectsFutureCourseGroupDuplicateTasksWithDifferentTopics() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO flexible_topics VALUES(300,20,1,30,10,6,'Topic A',NULL),(301,20,1,31,10,6,'Topic B',NULL)"); exec(c,"INSERT INTO flexible_tasks VALUES(302,20,1,30,10,6,'Same',3,NULL),(303,20,1,31,10,6,'Same',3,NULL)"); exec(c,"INSERT INTO flexible_task_topics VALUES(302,300),(303,301)"); assertBackfillConflict(c); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM flexible_task_topics")); }
    }

    @Test void rejectsDuplicateFlexibleTaskWhenCompletionWouldBeAtRisk() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"CREATE TABLE completed_flexible_tasks(student INTEGER,flexible_task INTEGER)"); duplicateTasks(c,3,3); exec(c,"INSERT INTO completed_flexible_tasks VALUES(40,302)"); assertBackfillConflict(c); assertEquals(302,scalar(c,"SELECT flexible_task FROM completed_flexible_tasks")); }
    }

    @Test void rejectsDuplicateFlexibleTaskWhenActiveStageWouldBeAtRisk() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"CREATE TABLE student_active_curriculum_stages(student INTEGER,subject INTEGER,semester INTEGER,task INTEGER)"); duplicateTasks(c,3,3); exec(c,"INSERT INTO student_active_curriculum_stages VALUES(40,1,10,302)"); assertBackfillConflict(c); assertEquals(302,scalar(c,"SELECT task FROM student_active_curriculum_stages")); }
    }

    @Test void rejectsDuplicateFlexibleDataWithDifferentReleaseSemantics() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); duplicateTasks(c,3,3); exec(c,"INSERT INTO flexible_task_releases VALUES(20,30,1,10,302,1),(20,31,1,10,303,0)"); assertBackfillConflict(c); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM flexible_task_releases")); }
    }

    @Test void regularFlexibleTasksMayShareNamesAcrossClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO flexible_tasks VALUES(302,20,2,30,10,6,'Experiment',3,NULL),(303,20,2,31,10,6,'Experiment',4,NULL)"); CourseGroupA2Backfill.run(c); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM flexible_tasks WHERE course_group IS NULL AND name='Experiment'")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='uq_flexible_tasks_course_group_name'")); }
    }

    @Test void freshDatabaseCreatesCourseGroupUniqueIndexesAfterBackfill() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); CourseGroupA2Backfill.run(c); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='uq_flexible_topics_course_group_name' AND sql LIKE '%UNIQUE INDEX%course_group,name%WHERE course_group IS NOT NULL%'")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='uq_flexible_tasks_course_group_name' AND sql LIKE '%UNIQUE INDEX%course_group,name%WHERE course_group IS NOT NULL%'")); }
    }

    @Test void conflictingLegacyDatabaseFailsInPreflightBeforeUniqueIndexCreation() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); duplicateTasks(c,3,4); assertBackfillConflict(c); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name IN ('uq_flexible_topics_course_group_name','uq_flexible_tasks_course_group_name')")); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM flexible_tasks WHERE course_group IS NULL")); }
    }

    @Test void successfulA2BackfillIsFullyIdempotentOnReplay() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) { migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO flexible_topics VALUES(300,20,1,30,10,6,'FTopic',NULL)"); exec(c,"INSERT INTO flexible_tasks VALUES(302,20,1,30,10,6,'FTask',3,NULL)"); exec(c,"INSERT INTO flexible_task_topics VALUES(302,300)"); exec(c,"INSERT INTO curriculum_topic_releases VALUES(20,30,1,10,100,1)"); exec(c,"INSERT INTO flexible_topic_releases VALUES(20,30,1,10,300,1)"); exec(c,"INSERT INTO flexible_task_releases VALUES(20,30,1,10,302,1)"); CourseGroupA2Backfill.run(c); long groups=scalar(c,"SELECT COUNT(*) FROM course_groups"),members=scalar(c,"SELECT COUNT(*) FROM course_group_members"),topics=scalar(c,"SELECT COUNT(*) FROM course_group_topic_releases"),flex=scalar(c,"SELECT COUNT(*) FROM course_group_flexible_task_releases"); CourseGroupA2Backfill.run(c); assertEquals(groups,scalar(c,"SELECT COUNT(*) FROM course_groups")); assertEquals(members,scalar(c,"SELECT COUNT(*) FROM course_group_members")); assertEquals(topics,scalar(c,"SELECT COUNT(*) FROM course_group_topic_releases")); assertEquals(flex,scalar(c,"SELECT COUNT(*) FROM course_group_flexible_task_releases")); assertEquals(302,scalar(c,"SELECT id FROM flexible_tasks WHERE name='FTask'")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='uq_flexible_tasks_course_group_name'")); }
    }

    static void duplicateTasks(Connection c,int tokensA,int tokensB)throws SQLException { exec(c,"INSERT INTO flexible_tasks VALUES(302,20,1,30,10,6,'Collision',?,NULL),(303,20,1,31,10,6,'Collision',?,NULL)",tokensA,tokensB); }
    static void assertBackfillConflict(Connection c) { assertThrows(CurriculumException.class,()->CourseGroupA2Backfill.run(c)); }

    @Test void assignIndividualAddsCrossClassMembersToSameGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO students VALUES(40),(41)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41)"); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7")); assertEquals(7,CourseGroup.resolve(c,1,6,10).id()); } }
    @Test void assignmentMembershipAndContextShareOneCourseGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"CREATE TABLE student_curriculum_contexts(student INTEGER,subject INTEGER,semester INTEGER,teacher INTEGER,class INTEGER,grade INTEGER,course_group INTEGER)"); exec(c,"CREATE TABLE curriculum_individual_assignments(student INTEGER,semester INTEGER,assignment_group TEXT,subject INTEGER)"); exec(c,"INSERT INTO student_curriculum_contexts VALUES(40,1,10,20,30,6,7)"); exec(c,"INSERT INTO curriculum_individual_assignments VALUES(40,10,'WPF',1)"); exec(c,"INSERT INTO course_group_members VALUES(7,40)"); assertEquals(7,scalar(c,"SELECT course_group FROM student_curriculum_contexts")); assertEquals(1,scalar(c,"SELECT subject FROM curriculum_individual_assignments")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_group_members")); } }
    @Test void repeatedMembershipInsertIsIdempotent() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO course_group_members VALUES(7,40) ON CONFLICT DO NOTHING"); exec(c,"INSERT INTO course_group_members VALUES(7,40) ON CONFLICT DO NOTHING"); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_group_members")); } }
    @Test void subjectSwitchRemovesOldMembershipBeforeNewMembership() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO subjects VALUES(3,'WPF Other')"); exec(c,"INSERT INTO course_groups VALUES(8,3,6,10,20,'WPF','WPF Other',1)"); exec(c,"INSERT INTO course_group_members VALUES(7,40)"); exec(c,"DELETE FROM course_group_members WHERE course_group=7 AND student=40"); exec(c,"INSERT INTO course_group_members VALUES(8,40)"); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7 AND student=40")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=8 AND student=40")); } }
    @Test void historyProtectedAssignmentKeepsExistingGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"CREATE TABLE completed_flexible_tasks(student INTEGER,flexible_task INTEGER)"); exec(c,"INSERT INTO course_group_members VALUES(7,40)"); exec(c,"INSERT INTO completed_flexible_tasks VALUES(40,900)"); assertEquals(7,scalar(c,"SELECT id FROM course_groups WHERE subject=1")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM completed_flexible_tasks")); } }
    @Test void courseGroupTeacherIsCanonicalSource() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); assertEquals(20,scalar(c,"SELECT teacher FROM course_groups WHERE id=7")); assertEquals(20,CourseGroup.resolve(c,1,6,10).teacherId()); } }
    @Test void semesterCopyCreatesDistinctCourseGroupId() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO semesters VALUES(11)"); var copy=CourseGroup.copyForSemester(c,CourseGroup.resolve(c,1,6,10),11); assertNotEquals(7,copy.id()); assertEquals(11,copy.semesterId()); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_groups WHERE subject=1 AND grade=6 AND semester=11")); } }
    @Test void semesterCopyPreservesGroupIdentityFields() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO semesters VALUES(11)"); var copy=CourseGroup.copyForSemester(c,CourseGroup.resolve(c,1,6,10),11); assertEquals(1,copy.subjectId()); assertEquals(6,copy.grade()); assertEquals(20,copy.teacherId()); assertEquals("WPF",copy.assignmentGroup()); assertEquals("WPF Test",copy.name()); } }
    @Test void semesterCopyDoesNotReuseExistingSemesterGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO semesters VALUES(11)"); var first=CourseGroup.copyForSemester(c,CourseGroup.resolve(c,1,6,10),11); var second=CourseGroup.copyForSemester(c,CourseGroup.resolve(c,1,6,10),11); assertEquals(first.id(),second.id()); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_groups WHERE semester=11")); } }
    @Test void courseGroupMemberCountUsesMembershipRows() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO students VALUES(40),(41),(42)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41),(7,42)"); assertEquals(3,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7")); } }
    @Test void regularSubjectsHaveNoCourseGroupIdentity() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM course_groups WHERE subject=2")); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM curriculum_subject_types WHERE subject=2 AND mode='REGULAR'")); } }
    @Test void individualRosterUsesCourseGroupMembersAcrossHomeClasses() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO students VALUES(40),(41),(42)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41),(7,42)"); assertEquals(3,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7")); } }
    @Test void individualRosterEntryClassDoesNotChangeMembershipSet() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO students VALUES(40),(41)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41)"); assertEquals(scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7"),scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7")); } }
    @Test void individualRosterExcludesOtherCourseGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO subjects VALUES(3,'Other')"); exec(c,"INSERT INTO course_groups VALUES(8,3,6,10,20,'WPF','Other',1)"); exec(c,"INSERT INTO students VALUES(40),(41)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(8,41)"); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7")); } }
    @Test void regularRosterRemainsClassScoped() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO students VALUES(40),(41)"); exec(c,"CREATE TABLE student_curriculum_contexts(student INTEGER,subject INTEGER,semester INTEGER,teacher INTEGER,class INTEGER,grade INTEGER,course_group INTEGER)"); exec(c,"INSERT INTO student_curriculum_contexts VALUES(40,2,10,20,30,6,NULL),(41,2,10,20,31,6,NULL)"); assertEquals(1,scalar(c,"SELECT COUNT(*) FROM student_curriculum_contexts WHERE subject=2 AND class=30")); } }
    @Test void partnerCandidatesRequireSameCourseGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO students VALUES(40),(41)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41)"); assertEquals(2,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7")); } }
    @Test void partnerCandidatesExcludeOtherCourseGroup() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"INSERT INTO subjects VALUES(3,'Other')"); exec(c,"INSERT INTO course_groups VALUES(8,3,6,10,20,'WPF','Other',1)"); exec(c,"INSERT INTO students VALUES(40),(41)"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(8,41)"); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7 AND student=41")); } }
    @Test void partnerCandidateRequiresPartnerRequestRow() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"CREATE TABLE student_subject_requests(student INTEGER,subject INTEGER,semester INTEGER,request_type TEXT)"); exec(c,"INSERT INTO student_subject_requests VALUES(40,1,10,'PARTNER')"); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM student_subject_requests WHERE student=41 AND request_type='PARTNER'")); } }
    @Test void partnerCandidateRequiresMatchingActiveStage() throws Exception { try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")){ migrationSchema(c); migrationSeed(c); exec(c,"CREATE TABLE student_active_curriculum_stages(student INTEGER,subject INTEGER,semester INTEGER,central_task INTEGER,flexible_task INTEGER)"); exec(c,"INSERT INTO student_active_curriculum_stages VALUES(40,1,10,200,NULL),(41,1,10,201,NULL)"); assertEquals(0,scalar(c,"SELECT COUNT(*) FROM student_active_curriculum_stages a JOIN student_active_curriculum_stages b ON a.subject=b.subject AND a.semester=b.semester AND a.central_task=b.central_task WHERE a.student=40 AND b.student=41")); } }

    static void migrationSchema(Connection c)throws SQLException {
        releaseSchema(c); exec(c,"CREATE TABLE students(id INTEGER PRIMARY KEY)"); exec(c,"CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))"); exec(c,"CREATE TABLE flexible_topics(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,course_group INTEGER)"); exec(c,"CREATE TABLE flexible_tasks(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,tokens INTEGER,course_group INTEGER)"); exec(c,"CREATE TABLE flexible_task_topics(flexible_task INTEGER,flexible_topic INTEGER)");
    }
    static void migrationSeed(Connection c)throws SQLException {
        exec(c,"INSERT INTO subjects VALUES(1,'WPF Test'),(2,'Mathe')"); exec(c,"INSERT INTO semesters VALUES(10)"); exec(c,"INSERT INTO classes VALUES(30,6),(31,6)"); exec(c,"INSERT INTO curriculum_subject_types VALUES(1,'INDIVIDUAL','WPF'),(2,'REGULAR',NULL)"); exec(c,"INSERT INTO course_groups VALUES(7,1,6,10,20,'WPF','WPF Test',1)"); exec(c,"INSERT INTO topics VALUES(100,1,6,10),(101,2,6,10)"); exec(c,"INSERT INTO tasks VALUES(200,100)"); exec(c,"INSERT INTO curriculum_topic_releases VALUES(20,30,2,10,101,1)"); exec(c,"INSERT INTO curriculum_grade_teachers VALUES(10,6,1,20)");
    }

    @Test void centralGroupReleaseDisableClearsActiveStagesAcrossHomeClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))"); exec(c,"INSERT INTO course_group_members VALUES(7,40),(7,41)"); exec(c,"INSERT INTO course_group_topic_releases VALUES(7,100,1)");
            assertTrue(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,30,10),100));
            exec(c,"UPDATE course_group_topic_releases SET active=0 WHERE course_group=7 AND topic=100");
            assertFalse(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,1,31,10),100));
            assertEquals(2,scalar(c,"SELECT COUNT(*) FROM course_group_members WHERE course_group=7"));
        }
    }

    @Test void flexibleGroupReleaseDisableClearsActiveStagesAcrossHomeClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"CREATE TABLE flexible_topics(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,course_group INTEGER)"); exec(c,"CREATE TABLE flexible_tasks(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,tokens INTEGER,course_group INTEGER)");
            exec(c,"INSERT INTO flexible_topics VALUES(300,20,1,30,10,6,'F',7)"); exec(c,"INSERT INTO flexible_tasks VALUES(301,20,1,30,10,6,'F',10,7)"); exec(c,"INSERT INTO course_group_flexible_topic_releases VALUES(7,300,1)"); exec(c,"INSERT INTO course_group_flexible_task_releases VALUES(7,301,1)");
            assertTrue(CurriculumEnrollment.flexibleReleased(c,new Curriculum.Scope(20,1,31,10),301,300));
            exec(c,"UPDATE course_group_flexible_task_releases SET active=0 WHERE course_group=7 AND flexible_task=301");
            assertFalse(CurriculumEnrollment.flexibleReleased(c,new Curriculum.Scope(20,1,31,10),301,300));
        }
    }

    @Test void centralTaskAccessIsSharedAcrossCourseGroupHomeClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"INSERT INTO topics VALUES(100,1,6,10)"); exec(c,"INSERT INTO tasks VALUES(200,100)"); exec(c,"INSERT INTO course_group_topic_releases VALUES(7,100,1)");
            assertTrue(CurriculumEnrollment.released(c,new Curriculum.Scope(20,1,30,10),200,100)); assertTrue(CurriculumEnrollment.released(c,new Curriculum.Scope(20,1,32,10),200,100));
        }
    }

    @Test void centralTaskAccessRejectsStudentOutsideCourseGroup() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))"); exec(c,"INSERT INTO course_group_members VALUES(7,40)");
            assertThrows(CurriculumException.class,()->CourseGroup.resolveForStudent(c,41,1,10));
        }
    }

    @Test void flexibleTaskAccessIsSharedAcrossCourseGroupHomeClasses() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"CREATE TABLE flexible_topics(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,course_group INTEGER)"); exec(c,"CREATE TABLE flexible_tasks(id INTEGER PRIMARY KEY,owner_teacher INTEGER,subject INTEGER,class INTEGER,semester INTEGER,grade INTEGER,name TEXT,tokens INTEGER,course_group INTEGER)");
            exec(c,"INSERT INTO flexible_topics VALUES(300,20,1,30,10,6,'F',7)"); exec(c,"INSERT INTO flexible_tasks VALUES(301,20,1,30,10,6,'F',10,7)"); exec(c,"INSERT INTO course_group_flexible_task_releases VALUES(7,301,1)");
            assertTrue(CurriculumEnrollment.flexibleReleased(c,new Curriculum.Scope(20,1,30,10),301,null)); assertTrue(CurriculumEnrollment.flexibleReleased(c,new Curriculum.Scope(20,1,32,10),301,null));
        }
    }

    @Test void flexibleTaskAccessRejectsStudentOutsideCourseGroup() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); seedGroup(c); exec(c,"CREATE TABLE course_group_members(course_group INTEGER,student INTEGER,PRIMARY KEY(course_group,student))"); exec(c,"INSERT INTO course_group_members VALUES(7,40)");
            assertThrows(CurriculumException.class,()->CourseGroup.resolveForStudent(c,41,1,10));
        }
    }

    @Test void regularTaskAccessRemainsClassScoped() throws Exception {
        try(Connection c=DriverManager.getConnection("jdbc:sqlite::memory:")) {
            releaseSchema(c); exec(c,"INSERT INTO subjects VALUES(2,'Mathe')"); exec(c,"INSERT INTO curriculum_subject_types VALUES(2,'REGULAR',NULL)"); exec(c,"INSERT INTO course_groups VALUES(8,2,6,10,20,'REGULAR','Mathe',1)");
            exec(c,"INSERT INTO curriculum_topic_releases VALUES(20,30,2,10,100,1)");
            assertTrue(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,2,30,10),100));
            assertFalse(CurriculumEnrollment.topicReleased(c,new Curriculum.Scope(20,2,31,10),100));
        }
    }
}
