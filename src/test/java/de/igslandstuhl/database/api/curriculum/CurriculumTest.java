package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.*;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
import de.igslandstuhl.database.server.webserver.Status;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class CurriculumTest {
    static final AtomicInteger sequence=new AtomicInteger(20000);
    SQLiteConnection db; Curriculum service; int id,topic;
    Curriculum.Actor admin=new Curriculum.Actor(true,0),teacher,other;
    Curriculum.Scope scope;
    @BeforeEach void setup() throws Exception {
        db=Server.getInstance().getConnection();db.createTables();
        service=new Curriculum(db);id=sequence.getAndAdd(10);topic=id;
        // Synthetic records only, in the test server's database; unique IDs avoid shared-cache collisions.
        db.writeTransaction(c->{
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id,"Subject-"+id);
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id+1,"Other-"+id);
            exec(c,"INSERT INTO school_years(id,label,week_count,current_week) VALUES(?,?,39,1)",id,"Year-"+id);
            for(int n=0;n<2;n++) {
                exec(c,"INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,?,?)",id+n,"Semester-"+(id+n),n+1,id);
                exec(c,"INSERT INTO classes(id,label,grade) VALUES(?,?,5)",id+n,"Class-"+(id+n));
                exec(c,"INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,'Synthetic','Teacher',?,'unused')",id+n,"teacher"+(id+n)+"@example.invalid");
                exec(c,"INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)",id+n,id);
                for(int k=0;k<2;k++)exec(c,"INSERT INTO teacher_classes(teacher_id,class_id) VALUES(?,?)",id+n,id+k);
            }
            exec(c,"INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,5,1,?)",topic,"Topic-"+id,id,id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Synthetic','Student',?,'unused',?,1)",id,"student"+id+"@example.invalid",id);
            return null;
        });
        teacher=new Curriculum.Actor(false,id);other=new Curriculum.Actor(false,id+1);scope=new Curriculum.Scope(id,id,id,id);
        service.assign(admin,id,scope);
    }
    static void exec(Connection c,String sql,Object...args)throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}
    }
    long scalar(String sql,Object...args)throws SQLException {
        try(PreparedStatement s=db.getSQLConnection().prepareStatement(sql)) {for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(ResultSet r=s.executeQuery()){assertTrue(r.next());return r.getLong(1);}}
    }
    int central(int tokens)throws SQLException{return service.createCentralTask(topic,"Central",TaskLevel.LEVEL1,tokens);}
    int centralNamed(String name,int tokens)throws SQLException{return service.createCentralTask(topic,name,TaskLevel.LEVEL1,tokens);}
    int otherSubjectCentral(String name,int tokens)throws Exception {
        int otherTopic=id+2;
        db.writeTransaction(c->{
            exec(c,"INSERT OR IGNORE INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)",id,id+1);
            exec(c,"INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,5,1,?)",otherTopic,"Other topic-"+id,id+1,id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",id,id+1,id,id,id);
            return null;
        });
        new CurriculumEnrollment(service).release(teacher,new Curriculum.Scope(id,id+1,id,id),otherTopic,null,true);
        return service.createCentralTask(otherTopic,name,TaskLevel.LEVEL1,tokens);
    }
    void releaseAllCentral() throws Exception {
        new CurriculumEnrollment(service).release(teacher,scope,topic,null,true);
    }
    Map<String,Object> activeRow() throws Exception {
        try(PreparedStatement s=db.getSQLConnection().prepareStatement("SELECT * FROM student_active_curriculum_stages WHERE student=? AND subject=?")) {
            s.setInt(1,id);s.setInt(2,id);
            try(ResultSet r=s.executeQuery()) {
                assertTrue(r.next());
                Map<String,Object> row=new java.util.HashMap<>();
                row.put("central_task",r.getObject("central_task"));
                row.put("flexible_task",r.getObject("flexible_task"));
                row.put("semester",r.getObject("semester"));
                return row;
            }
        }
    }
    @Test void topicRenameKeepsIdAndAllCachedReferences()throws Exception {
        Topic before=Topic.get(topic);Subject subject=before.getSubject();subject.getTopics(5);
        service.renameTopic(admin,topic,"Renamed");
        assertSame(before,Topic.get(topic));assertEquals(topic,before.getId());assertEquals("Renamed",before.getName());
        assertEquals("Renamed",subject.getTopics(5).stream().filter(t->t.getId()==topic).findFirst().orElseThrow().getName());
    }
    @Test void centralEditProtectsCompletedTokenValue()throws Exception {
        int taskId=central(6);Task task=Task.get(taskId);Topic.get(topic).getTasks();
        Student student=Student.get(id);student.changeTaskStatus(task,Task.STATUS_COMPLETED);
        long rowid=scalar("SELECT rowid FROM taskstats WHERE student=? AND task=?",id,taskId);
        assertEquals(6,student.getCurrentProgress(Subject.get(id)));
        var error=assertThrows(CurriculumException.class,()->service.editTask(admin,taskId,"Changed",4));
        assertEquals(409,error.status);assertEquals("completion_history_conflict",error.code);
        service.editTask(admin,taskId,"Changed",6);
        assertSame(task,Task.get(taskId));assertEquals("Changed",task.getName());assertEquals(6,task.getTokens());
        assertEquals(6,student.getCurrentProgress(Subject.get(id)));
        assertEquals(rowid,scalar("SELECT rowid FROM taskstats WHERE student=? AND task=?",id,taskId));
        assertEquals(Task.STATUS_COMPLETED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,taskId));
        assertTrue(student.getCompletedTasks().contains(task)); // HashSet membership must survive rename.
        student.changeTaskStatus(task,Task.STATUS_IN_PROGRESS);assertFalse(student.getCompletedTasks().contains(task));
    }
    @Test void flexibleCompletionProtectsCompletedTokenValue()throws Exception {
        var task=service.create(teacher,scope,"Flexible",6);service.complete(teacher,task.id(),id);
        long rowid=scalar("SELECT rowid FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,task.id());
        assertEquals(6,service.completedTokens(id,scope));
        var error=assertThrows(CurriculumException.class,()->service.edit(teacher,task.id(),"Renamed",4));
        assertEquals(409,error.status);assertEquals("completion_history_conflict",error.code);
        var updated=service.edit(teacher,task.id(),"Renamed",6);
        assertEquals(task.id(),updated.id());assertEquals(6,service.completedTokens(id,scope));
        assertEquals(rowid,scalar("SELECT rowid FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,task.id()));
        assertEquals("Renamed",service.list(teacher,scope).get(0).name());
    }
    @Test void teacherCannotEditSomeoneElsesTask()throws Exception {
        var task=service.create(teacher,scope,"Own",5);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.edit(other,task.id(),"Stolen",1)).status);
        assertEquals(5,service.list(teacher,scope).get(0).tokens());
    }
    @Test void assignmentsAreReadFreshAndBothAreRequired()throws Exception {
        assertEquals(403,assertThrows(CurriculumException.class,()->service.create(teacher,new Curriculum.Scope(id,id+1,id,id),"Wrong subject",1)).status);
        db.writeTransaction(c->{exec(c,"DELETE FROM teacher_classes WHERE teacher_id=? AND class_id=?",id,id);return null;});
        assertEquals(403,assertThrows(CurriculumException.class,()->service.create(teacher,scope,"Wrong class",1)).status);
    }
    @Test void teacherCannotMutateCentralStructure()throws Exception {
        int task=central(6);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.renameTopic(teacher,topic,"No")).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.editTask(teacher,task,"No",4)).status);
    }
    @Test void independentTeachersCanUseIdenticalNamesInSameClass()throws Exception {
        central(70);service.create(teacher,scope,"Flexible",35);
        var otherScope=new Curriculum.Scope(id+1,id,id,id);service.create(other,otherScope,"Flexible",35);
        assertEquals(105,service.budget(teacher,scope).totalTokens());assertEquals(105,service.budget(other,otherScope).totalTokens());
    }
    @Test void independentClassesForSameTeacher()throws Exception {
        central(70);service.create(teacher,scope,"Flexible",35);
        var second=new Curriculum.Scope(id,id,id+1,id);service.create(teacher,second,"Flexible",35);
        assertEquals(105,service.budget(teacher,second).totalTokens());
    }
    @Test void independentSemesters()throws Exception {
        central(70);service.create(teacher,scope,"Flexible",35);
        var second=new Curriculum.Scope(id,id,id,id+1);service.create(teacher,second,"Flexible",105);
        assertEquals(105,service.budget(teacher,second).totalTokens());assertEquals(0,service.budget(teacher,second).centralTokens());
    }
    @ParameterizedTest @ValueSource(ints={100,101,102,103,104,105})
    void regularAndToleratedBudgetsAllowed(int total)throws Exception {
        central(70);var task=service.create(teacher,scope,"Flexible",total-70);
        assertEquals(total,service.budget(teacher,scope).totalTokens());
        service.edit(teacher,task.id(),"Flexible edited",total-70);
        assertEquals(105-total,service.budget(teacher,scope).remainingHard());
    }
    @Test void creationAndEditAt106RollBack()throws Exception {
        central(70);
        var error=assertThrows(CurriculumException.class,()->service.create(teacher,scope,"No",36));assertEquals("budget_exceeded",error.code);
        assertEquals(106,error.affectedContexts.get(0).totalTokens());assertTrue(service.list(teacher,scope).isEmpty());
        var task=service.create(teacher,scope,"Valid",30);
        assertThrows(CurriculumException.class,()->service.edit(teacher,task.id(),"No",36));
        assertEquals(30,service.list(teacher,scope).get(0).tokens());
    }
    @Test void adminIncreaseReportsAffectedContextsAndPreservesCache()throws Exception {
        int task=central(70);Task cached=Task.get(task);service.create(teacher,scope,"Flexible",35);
        var error=assertThrows(CurriculumException.class,()->service.editTask(admin,task,"Raised",71));
        assertEquals(scope.teacherId(),error.affectedContexts.get(0).teacherId());assertEquals(106,error.affectedContexts.get(0).totalTokens());
        assertEquals(70,cached.getTokens());assertEquals(70,scalar("SELECT tokens FROM tasks WHERE id=?",task));
        assertThrows(CurriculumException.class,()->service.createCentralTask(topic,"Another",TaskLevel.LEVEL2,1));
    }
    @Test void centralOnlyHardLimit()throws Exception {
        int task=central(105);assertThrows(CurriculumException.class,()->service.editTask(admin,task,"No",106));
        assertThrows(CurriculumException.class,()->service.createCentralTask(topic,"No",TaskLevel.LEVEL2,1));
    }
    @Test void activeCentralStageIsUniquePerStudentAndSubject() throws Exception {
        releaseAllCentral();
        int first=centralNamed("First active",5),second=centralNamed("Second active",6);
        service.activateCentralStage(id,first);
        assertEquals(Task.STATUS_IN_PROGRESS,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,first));
        assertEquals(Curriculum.ActiveStageType.CENTRAL,service.activeStage(id,id).type());
        assertEquals(first,service.activeStage(id,id).taskId());

        service.activateCentralStage(id,second);
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,first));
        assertEquals(Task.STATUS_IN_PROGRESS,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,second));
        assertEquals(second,service.activeStage(id,id).taskId());
        assertEquals(1,scalar("SELECT COUNT(*) FROM student_active_curriculum_stages WHERE student=? AND subject=?",id,id));
        assertEquals(Set.of(Task.get(second)),Student.get(id).getSelectedTasks());
    }
    @Test void activeStagesForDifferentSubjectsAreIndependent() throws Exception {
        releaseAllCentral();
        int math=centralNamed("Math active",5),german=otherSubjectCentral("German active",7);
        service.activateCentralStage(id,math);
        service.activateCentralStage(id,german);
        assertEquals(2,scalar("SELECT COUNT(*) FROM student_active_curriculum_stages WHERE student=?",id));
        assertEquals(math,service.activeStage(id,id).taskId());
        assertEquals(german,service.activeStage(id,id+1).taskId());
    }
    @Test void centralActivationPreservesCompletedAndLockedStages() throws Exception {
        releaseAllCentral();
        int completed=centralNamed("Already done",5),locked=centralNamed("Locked stage",6),next=centralNamed("Next active",7);
        Student.get(id).changeTaskStatus(Task.get(completed),Task.STATUS_COMPLETED);
        Student.get(id).changeTaskStatus(Task.get(locked),Task.STATUS_LOCKED);
        service.activateCentralStage(id,next);
        assertEquals(Task.STATUS_COMPLETED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,completed));
        assertEquals(Task.STATUS_LOCKED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,locked));
        assertEquals(Task.STATUS_IN_PROGRESS,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,next));
    }
    @Test void centralTerminalChangesClearOnlyMatchingActiveStage() throws Exception {
        releaseAllCentral();
        int active=centralNamed("Active",5),otherActive=otherSubjectCentral("Other active",6);
        service.activateCentralStage(id,active);
        service.activateCentralStage(id,otherActive);
        service.changeCentralStageStatus(id,active,Task.STATUS_NOT_STARTED);
        assertNull(service.activeStage(id,id));
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
        assertEquals(otherActive,service.activeStage(id,id+1).taskId());

        service.activateCentralStage(id,active);
        service.changeCentralStageStatus(id,active,Task.STATUS_COMPLETED);
        assertNull(service.activeStage(id,id));
        assertEquals(Task.STATUS_COMPLETED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));

        service.activateCentralStage(id,active);
        service.changeCentralStageStatus(id,active,Task.STATUS_LOCKED);
        assertNull(service.activeStage(id,id));
        assertEquals(Task.STATUS_LOCKED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
        assertEquals(otherActive,service.activeStage(id,id+1).taskId());
    }
    @Test void notStartedAndLockedUpdateStudentTaskCachesDifferently() throws Exception {
        int task=centralNamed("Cache task",5);Task cached=Task.get(task);Student student=Student.get(id);
        student.changeTaskStatus(cached,Task.STATUS_LOCKED);
        assertTrue(student.getLockedTasks().contains(cached));
        student.changeTaskStatus(cached,Task.STATUS_NOT_STARTED);
        assertFalse(student.getLockedTasks().contains(cached));
        assertFalse(student.getSelectedTasks().contains(cached));
        assertFalse(student.getCompletedTasks().contains(cached));
        student.changeTaskStatus(cached,Task.STATUS_LOCKED);
        assertTrue(student.getLockedTasks().contains(cached));
    }
    @Test void flexibleActivationSharesTheSameActiveStageSlot() throws Exception {
        releaseAllCentral();
        var enrollment=new CurriculumEnrollment(service);
        int central=centralNamed("Central active",5);
        int topicId=service.createFlexibleTopic(teacher,scope,"Practice");
        var flexible=service.create(teacher,scope,"Flexible active",6,topicId);
        enrollment.release(teacher,scope,null,null,null,flexible.id(),true);

        service.activateFlexibleStage(id,flexible.id());
        assertEquals(Curriculum.ActiveStageType.FLEXIBLE,service.activeStage(id,id).type());
        assertEquals(flexible.id(),service.activeStage(id,id).taskId());
        service.activateCentralStage(id,central);
        assertEquals(Curriculum.ActiveStageType.CENTRAL,service.activeStage(id,id).type());
        assertEquals(central,service.activeStage(id,id).taskId());

        service.activateFlexibleStage(id,flexible.id());
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,central));
        assertEquals(Curriculum.ActiveStageType.FLEXIBLE,service.activeStage(id,id).type());
        assertEquals(flexible.id(),service.activeStage(id,id).taskId());
    }
    @Test void flexibleActivationDoesNotReplaceOtherSubjects() throws Exception {
        releaseAllCentral();
        int math=centralNamed("Math active",5),german=otherSubjectCentral("German active",7);
        service.activateCentralStage(id,math);
        service.activateCentralStage(id,german);
        int topicId=service.createFlexibleTopic(teacher,scope,"Practice");
        var flexible=service.create(teacher,scope,"Flexible active",6,topicId);
        new CurriculumEnrollment(service).release(teacher,scope,null,null,null,flexible.id(),true);
        service.activateFlexibleStage(id,flexible.id());
        assertEquals(Curriculum.ActiveStageType.FLEXIBLE,service.activeStage(id,id).type());
        assertEquals(german,service.activeStage(id,id+1).taskId());
    }
    @Test void flexibleActivationRequiresReleaseAndMatchingStudentContext() throws Exception {
        int topicId=service.createFlexibleTopic(teacher,scope,"Practice");
        var hidden=service.create(teacher,scope,"Hidden",6,topicId);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.activateFlexibleStage(id,hidden.id())).status);
        new CurriculumEnrollment(service).release(teacher,scope,null,null,null,hidden.id(),true);

        var foreignScope=new Curriculum.Scope(id+1,id,id+1,id);
        var foreign=service.create(other,foreignScope,"Foreign",6);
        new CurriculumEnrollment(service).release(other,foreignScope,null,null,null,foreign.id(),true);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.activateFlexibleStage(id,foreign.id())).status);
        service.activateFlexibleStage(id,hidden.id());
        assertEquals(hidden.id(),service.activeStage(id,id).taskId());
    }
    @Test void teacherRosterUsesCanonicalRegularContextAndStudentContextsOnly() throws Exception {
        int first=id+2,second=id+3,unassigned=id+4,inactive=id+5;
        db.writeTransaction(c->{
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Bob','Zeile',?,'unused',?,1)",first,"roster"+first+"@example.invalid",id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Ada','Alpha',?,'unused',?,1)",second,"roster"+second+"@example.invalid",id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'No','Context',?,'unused',?,1)",unassigned,"roster"+unassigned+"@example.invalid",id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level,active) VALUES(?,'Inactive','Hidden',?,'unused',?,1,0)",inactive,"roster"+inactive+"@example.invalid",id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",first,id,id,id,id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",second,id,id,id,id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",inactive,id,id,id,id);
            return null;
        });
        var roster=service.teacherRoster(teacher,scope);
        assertEquals(List.of(second,id,first),roster.stream().map(r->((Number)r.get("id")).intValue()).toList());
        assertTrue(roster.stream().noneMatch(r->((Number)r.get("id")).intValue()==unassigned));
        assertTrue(roster.stream().noneMatch(r->((Number)r.get("id")).intValue()==inactive));
        assertEquals("Synthetic Student",byId(roster,id).get("name"));
        assertNull(byId(roster,id).get("activeStage"));
        assertFalse(byId(roster,id).containsKey("email"));
        assertFalse(byId(roster,id).containsKey("password"));
        assertFalse(byId(roster,id).containsKey("passwordHash"));
        assertEquals(List.of(id),roster.stream().filter(r->((Number)r.get("id")).intValue()==id).map(r->((Number)r.get("id")).intValue()).toList());
        assertEquals(403,assertThrows(CurriculumException.class,()->service.teacherRoster(other,scope)).status);

        db.writeTransaction(c->{exec(c,"DELETE FROM curriculum_class_teachers WHERE semester=? AND class=? AND subject=? AND teacher=?",id,id,id,id);return null;});
        assertEquals(403,assertThrows(CurriculumException.class,()->service.teacherRoster(teacher,scope)).status);
        db.writeTransaction(c->{exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id);exec(c,"DELETE FROM student_curriculum_contexts WHERE teacher=? AND class=? AND subject=? AND semester=?",id,id,id,id);return null;});
        assertTrue(service.teacherRoster(teacher,scope).isEmpty());
    }
    @Test void teacherRosterSupportsIndividualContexts() throws Exception {
        int visible=id+2,alsoVisible=id+3,hidden=id+4;
        db.writeTransaction(c->{
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id+2,"Individual-"+id);
            exec(c,"INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,1,'INDIVIDUAL','WPF')",id+2);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Visible','One',?,'unused',?,1)",visible,"visible"+id+"@example.invalid",id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Visible','Two',?,'unused',?,1)",alsoVisible,"also"+id+"@example.invalid",id);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Hidden','NoContext',?,'unused',?,1)",hidden,"hidden"+id+"@example.invalid",id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",visible,id+2,id,id,id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",alsoVisible,id+2,id,id,id);
            return null;
        });
        var individualScope=new Curriculum.Scope(id,id+2,id,id);
        var roster=service.teacherRoster(teacher,individualScope);
        assertEquals(Set.of(visible,alsoVisible),Set.copyOf(roster.stream().map(r->((Number)r.get("id")).intValue()).toList()));
        assertTrue(roster.stream().noneMatch(r->((Number)r.get("id")).intValue()==hidden));
    }
    @SuppressWarnings("unchecked")
    @Test void teacherRosterReadsAuthoritativeActiveStagesAndSignals() throws Exception {
        db.writeTransaction(c->{exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id);return null;});
        int central=centralNamed("Central roster",5);
        releaseAllCentral();
        service.activateCentralStage(id,central);
        db.writeTransaction(c->{
            exec(c,"INSERT INTO student_subject_requests(student,subject,semester,request_type) VALUES(?,?,?,'HELP')",id,id,id);
            exec(c,"INSERT INTO student_subject_requests(student,subject,semester,request_type) VALUES(?,?,?,'PARTNER')",id,id,id);
            exec(c,"INSERT INTO student_subject_requests(student,subject,semester,request_type) VALUES(?,?,?,'EXPERIMENT')",id,id+1,id);
            exec(c,"INSERT INTO student_subject_requests(student,subject,semester,request_type) VALUES(?,?,?,'EXAM')",id,id,id+1);
            return null;
        });
        var row=byId(service.teacherRoster(teacher,scope),id);
        var active=(Map<String,Object>)row.get("activeStage");
        assertEquals("CENTRAL",active.get("type"));
        assertEquals(central,((Number)active.get("taskId")).intValue());
        assertEquals("Central roster",active.get("name"));
        var signals=(Map<String,Object>)row.get("signals");
        assertEquals(true,signals.get("help"));
        assertEquals(true,signals.get("partner"));
        assertEquals(false,signals.get("experiment"));
        assertEquals(false,signals.get("exam"));

        int otherSubjectTask=otherSubjectCentral("Other subject active",6);
        service.activateCentralStage(id,otherSubjectTask);
        db.writeTransaction(c->{exec(c,"UPDATE student_active_curriculum_stages SET semester=? WHERE student=? AND subject=?",id+1,id,id);return null;});
        assertNull(byId(service.teacherRoster(teacher,scope),id).get("activeStage"));

        var flexible=service.create(teacher,scope,"Flexible roster",5);
        new CurriculumEnrollment(service).release(teacher,scope,null,null,null,flexible.id(),true);
        service.activateFlexibleStage(id,flexible.id());
        row=byId(service.teacherRoster(teacher,scope),id);
        active=(Map<String,Object>)row.get("activeStage");
        assertEquals("FLEXIBLE",active.get("type"));
        assertEquals(flexible.id(),((Number)active.get("taskId")).intValue());
        assertEquals("Flexible roster",active.get("name"));
        service.deactivateFlexibleStage(id,flexible.id());
        row=byId(service.teacherRoster(teacher,scope),id);
        assertNull(row.get("activeStage"));
        signals=(Map<String,Object>)row.get("signals");
        assertEquals(true,signals.get("help"));
        assertEquals(true,signals.get("partner"));
    }
    @SuppressWarnings("unchecked")
    @Test void teacherRosterMapsExperimentAndExamTogether() throws Exception {
        db.writeTransaction(c->{
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id);
            exec(c,"INSERT INTO student_subject_requests(student,subject,semester,request_type) VALUES(?,?,?,'EXPERIMENT')",id,id,id);
            exec(c,"INSERT INTO student_subject_requests(student,subject,semester,request_type) VALUES(?,?,?,'EXAM')",id,id,id);
            return null;
        });
        var signals=(Map<String,Object>)byId(service.teacherRoster(teacher,scope),id).get("signals");
        assertEquals(false,signals.get("help"));
        assertEquals(false,signals.get("partner"));
        assertEquals(true,signals.get("experiment"));
        assertEquals(true,signals.get("exam"));
    }
    @Test void legacyUnscheduledAndIndividualRemainUntouched()throws Exception {
        var legacy=UnscheduledTask.addUnscheduledTask("Legacy-"+id,SchoolClass.get(id),Subject.get(id),6);
        db.writeTransaction(c->{exec(c,"INSERT INTO completed_unscheduled_tasks(student,unscheduled_task) VALUES(?,?)",id,legacy.getId());return null;});
        db.createTables();db.migrateTables();db.createTables();
        assertEquals(6,UnscheduledTask.get(legacy.getId()).getMaxTokens());
        assertEquals(1,scalar("SELECT COUNT(*) FROM completed_unscheduled_tasks WHERE student=? AND unscheduled_task=?",id,legacy.getId()));
        service.create(teacher,scope,"Legacy-"+id,5);assertEquals(0,service.completedTokens(id,scope));
    }
    @Test void administrativeCorrectionAndInvalidInput()throws Exception {
        var task=service.create(teacher,scope,"Own",5);service.edit(admin,task.id(),"Admin correction",4);
        assertEquals(4,service.list(admin,scope).get(0).tokens());
        assertEquals(400,assertThrows(CurriculumException.class,()->service.create(teacher,scope," ",1)).status);
        assertEquals(400,assertThrows(CurriculumException.class,()->service.create(teacher,scope,"Negative",-1)).status);
        assertEquals(404,assertThrows(CurriculumException.class,()->service.edit(teacher,Integer.MAX_VALUE,"Missing",1)).status);
        assertEquals(409,assertThrows(CurriculumException.class,()->service.create(teacher,scope,"Admin correction",1)).status);
    }
    @Test void concurrentCreatesCannotSpendSameRemainderTwice()throws Exception {
        central(100);ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            Callable<Boolean> first=()->{gate.await();try{service.create(teacher,scope,"Concurrent-"+Thread.currentThread().getId(),5);return true;}catch(CurriculumException e){assertEquals("budget_exceeded",e.code);return false;}};
            Future<Boolean>a=pool.submit(first),b=pool.submit(first);gate.countDown();assertNotEquals(a.get(10,TimeUnit.SECONDS),b.get(10,TimeUnit.SECONDS));
            assertEquals(105,service.budget(teacher,scope).totalTokens());
        }finally{pool.shutdownNow();}
    }
    @Test void scopedProgressIsRetroactiveAndExcludesOtherTeachers() throws Exception {
        int central = central(6);
        Student.get(id).changeTaskStatus(Task.get(central), Task.STATUS_COMPLETED);
        var own = service.create(teacher, scope, "Own", 6);
        service.complete(teacher, own.id(), id);
        var anotherScope = new Curriculum.Scope(id+1,id,id,id);
        var another = service.create(other, anotherScope, "Other", 20);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.complete(other,another.id(),id)).status);
        assertEquals(12L, service.progress(teacher,id,scope).get("totalTokens"));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.editTask(admin,central,"Central revised",4)).status);
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(teacher,own.id(),"Own revised",4)).status);
        service.editTask(admin,central,"Central revised",6);
        service.edit(teacher,own.id(),"Own revised",6);
        assertEquals(12L, service.progress(teacher,id,scope).get("totalTokens"));
        assertEquals(403,assertThrows(CurriculumException.class,()->service.progress(other,id,anotherScope)).status);
    }
    @Test void quotedNamesAreValidJsonAndDuplicateRenameIsAtomic() throws Exception {
        int task=central(6);
        String name="Quotes \" and backslash \\";
        service.renameTopic(admin,topic,name);
        service.editTask(admin,task,name,4);
        assertEquals(name,com.google.gson.JsonParser.parseString(Topic.get(topic).toJSON()).getAsJsonObject().get("name").getAsString());
        assertEquals(name,com.google.gson.JsonParser.parseString(Task.get(task).toJSON()).getAsJsonObject().get("name").getAsString());
        int second=service.createCentralTask(topic,"Second",TaskLevel.LEVEL1,1);
        assertEquals(409,assertThrows(CurriculumException.class,()->service.editTask(admin,second,name,5)).status);
        assertEquals(1,Task.get(second).getTokens());assertEquals("Second",Task.get(second).getName());
    }
    @Test void apiUsesSessionIdentityAndReturnsUsefulStatuses() throws Exception {
        User user=Teacher.get(id);
        assertEquals(de.igslandstuhl.database.server.webserver.Status.UNAUTHORIZED,request(User.ANONYMOUS,"/curriculum-catalog","{}").getStatus());
        assertEquals(de.igslandstuhl.database.server.webserver.Status.FORBIDDEN,request(Student.get(id),"/curriculum-catalog","{}").getStatus());
        assertEquals(de.igslandstuhl.database.server.webserver.Status.FORBIDDEN,request(user,"/rename-topic","{\"topicId\":"+topic+",\"name\":\"No\"}").getStatus());
        assertEquals(de.igslandstuhl.database.server.webserver.Status.BAD_REQUEST,request(user,"/edit-flexible-task","{\"taskId\":1.5,\"name\":\"No\",\"tokens\":4}").getStatus());
        assertEquals(de.igslandstuhl.database.server.webserver.Status.NOT_FOUND,request(user,"/edit-flexible-task","{\"taskId\":2147483647,\"name\":\"No\",\"tokens\":4}").getStatus());
        String payload="{\"teacherId\":"+(id+1)+",\"subjectId\":"+id+",\"classId\":"+id+",\"semesterId\":"+id+",\"name\":\"No\",\"tokens\":4}";
        assertEquals(de.igslandstuhl.database.server.webserver.Status.FORBIDDEN,request(user,"/add-flexible-task",payload).getStatus());
        central(105);
        payload="{\"subjectId\":"+id+",\"classId\":"+id+",\"semesterId\":"+id+",\"name\":\"No\",\"tokens\":1}";
        var response=request(user,"/add-flexible-task",payload);
        assertEquals(de.igslandstuhl.database.server.webserver.Status.CONFLICT,response.getStatus());
        var output=new java.io.ByteArrayOutputStream();response.respond(new java.io.PrintStream(output));
        assertTrue(output.toString().contains("budget_exceeded"));assertTrue(output.toString().contains("remainingHard"));
        assertEquals(de.igslandstuhl.database.server.webserver.Status.OK,request(user,"/curriculum-catalog","{}").getStatus());
    }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> teacherContexts(int teacherId) throws Exception {
        return (List<Map<String,Object>>)service.catalog(new Curriculum.Actor(false,teacherId)).get("contexts");
    }
    Map<String,Object> context(List<Map<String,Object>> contexts,int classId,int subjectId,int semesterId) {
        return contexts.stream().filter(row -> ((Number)row.get("classId")).intValue()==classId
                && ((Number)row.get("subjectId")).intValue()==subjectId
                && ((Number)row.get("semesterId")).intValue()==semesterId).findFirst().orElse(null);
    }
    @Test void teacherCatalogContextsUseCanonicalRegularAssignments() throws Exception {
        db.writeTransaction(c->{
            exec(c,"DELETE FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",id,id,id);
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id);
            exec(c,"UPDATE school_years SET current_semester=? WHERE id=?",id,id);
            return null;
        });
        var contexts=teacherContexts(id);
        var ctx=context(contexts,id,id,id);
        assertNotNull(ctx);
        assertEquals(id,((Number)ctx.get("semesterId")).intValue());
        assertEquals("Semester-"+id,ctx.get("semesterLabel"));
        assertEquals(true,ctx.get("activeSemester"));
        assertEquals(id,((Number)ctx.get("classId")).intValue());
        assertEquals("Class-"+id,ctx.get("classLabel"));
        assertEquals(5,((Number)ctx.get("grade")).intValue());
        assertEquals(id,((Number)ctx.get("subjectId")).intValue());
        assertEquals("Subject-"+id,ctx.get("subjectName"));
        assertEquals("REGULAR",ctx.get("subjectMode"));
    }
    @Test void teacherCatalogContextsIgnoreLegacyOnlyAndOtherTeachers() throws Exception {
        db.writeTransaction(c->{
            exec(c,"DELETE FROM student_curriculum_contexts WHERE subject=? AND semester=?",id,id);
            exec(c,"DELETE FROM curriculum_class_teachers WHERE subject=? AND semester=?",id,id);
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id+1);
            return null;
        });
        var contexts=teacherContexts(id);
        assertNull(context(contexts,id,id,id));
        assertTrue(contexts.stream().noneMatch(row -> ((Number)row.get("subjectId")).intValue()==id && ((Number)row.get("classId")).intValue()==id));
    }
    @Test void teacherCatalogContextsUseDistinctIndividualStudentContexts() throws Exception {
        db.writeTransaction(c->{
            exec(c,"INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,1,'INDIVIDUAL','WPF')",id+1);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Second','Student',?,'unused',?,1)",id+2,"student"+(id+2)+"@example.invalid",id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",id,id+1,id,id,id);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",id+2,id+1,id,id,id);
            return null;
        });
        var contexts=teacherContexts(id);
        var matching=contexts.stream().filter(row -> ((Number)row.get("classId")).intValue()==id
                && ((Number)row.get("subjectId")).intValue()==id+1
                && ((Number)row.get("semesterId")).intValue()==id).toList();
        assertEquals(1,matching.size());
        assertEquals("Other-"+id,matching.get(0).get("subjectName"));
        assertEquals("INDIVIDUAL",matching.get(0).get("subjectMode"));
        assertEquals("WPF",matching.get(0).get("assignmentGroup"));
    }
    @Test void teacherCatalogContextsExcludeUnassignedAndInactiveClasses() throws Exception {
        db.writeTransaction(c->{
            exec(c,"INSERT OR IGNORE INTO classes(id,label,grade,active) VALUES(0,'Nicht zugeordnet',0,1)");
            exec(c,"INSERT INTO classes(id,label,grade,active) VALUES(?,?,5,0)",id+3,"Inactive-"+id);
            exec(c,"INSERT OR REPLACE INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,0,id,id);
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id+3,id,id);
            return null;
        });
        var contexts=teacherContexts(id);
        assertTrue(contexts.stream().noneMatch(row -> ((Number)row.get("classId")).intValue()==0));
        assertTrue(contexts.stream().noneMatch(row -> ((Number)row.get("classId")).intValue()==id+3));
    }
    @Test void teacherCatalogContextsMarkInactiveSemesters() throws Exception {
        db.writeTransaction(c->{
            exec(c,"DELETE FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",id,id,id);
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",id,id,id,id);
            exec(c,"UPDATE school_years SET current_semester=? WHERE id=?",id+1,id);
            return null;
        });
        var ctx=context(teacherContexts(id),id,id,id);
        assertNotNull(ctx);
        assertEquals(false,ctx.get("activeSemester"));
    }
    Curriculum.Scope secondScope() {return new Curriculum.Scope(id+1,id,id,id);}
    void unassign() throws Exception {
        db.writeTransaction(c->{exec(c,"DELETE FROM student_curriculum_contexts WHERE student=?",id);return null;});
    }
    String body() {return "{\"subjectId\":"+id+",\"semesterId\":"+id+"}";}
    String assignmentBody() {return "{\"studentId\":"+id+",\"teacherId\":"+id+",\"subjectId\":"+id+",\"classId\":"+id+",\"semesterId\":"+id+"}";}
    String responseBody(de.igslandstuhl.database.server.webserver.responses.PostResponse response) {
        var output=new java.io.ByteArrayOutputStream();response.respond(new java.io.PrintStream(output));return output.toString();
    }
    @Test void twoTeachersSameClassSubjectCannotBothAwardToOneStudent() throws Exception {
        int central=central(70);Student.get(id).changeTaskStatus(Task.get(central),Task.STATUS_COMPLETED);
        var first=service.create(teacher,scope,"First",35);
        var second=service.create(other,secondScope(),"Second",35);
        service.complete(teacher,first.id(),id);service.complete(teacher,first.id(),id);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.complete(other,second.id(),id)).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.complete(admin,second.id(),id)).status);
        assertEquals(105L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(1,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.assign(admin,id,secondScope())).status);
        assertEquals(id,scalar("SELECT teacher FROM student_curriculum_contexts WHERE student=?",id));
    }
    @Test void distinctStudentsCanUseDifferentTeachersInSameClass() throws Exception {
        db.writeTransaction(c->{exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Other','Student',?,'unused',?,1)",id+1,"student"+(id+1)+"@example.invalid",id);return null;});
        service.assign(admin,id+1,secondScope());
        central(70);var a=service.create(teacher,scope,"A",35);var b=service.create(other,secondScope(),"B",35);
        service.complete(teacher,a.id(),id);service.complete(other,b.id(),id+1);
        assertEquals(35L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(35L,service.studentProgress(Student.get(id+1),id,id).get("totalTokens"));
        assertEquals(403,assertThrows(CurriculumException.class,()->service.progress(teacher,id+1,scope)).status);
    }
    @Test void missingAssignmentIsNotInferredFromClassOrTeacher() throws Exception {
        unassign();var task=service.create(teacher,scope,"Task",5);
        assertEquals("context_unassigned",assertThrows(CurriculumException.class,()->service.complete(teacher,task.id(),id)).code);
        assertEquals("context_unassigned",assertThrows(CurriculumException.class,()->service.studentProgress(Student.get(id),id,id)).code);
        assertEquals(0,scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE student=?",id));
    }
    @Test void adminCanReassignBeforeFlexibleCompletionAndRepeatAssignment() throws Exception {
        int task=central(70);Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        service.assign(admin,id,secondScope());service.assign(admin,id,secondScope());
        assertEquals(1,scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE student=?",id));
        assertEquals(id+1,scalar("SELECT teacher FROM student_curriculum_contexts WHERE student=?",id));
        assertEquals(70L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
    }
    @Test void teacherCannotAssignAndAdminCannotAssignInvalidMemberships() throws Exception {
        assertEquals(403,assertThrows(CurriculumException.class,()->service.assign(teacher,id,scope)).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.assign(admin,id,new Curriculum.Scope(id,id,id+1,id))).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.assign(admin,id,new Curriculum.Scope(id,id+1,id,id))).status);
        db.writeTransaction(c->{exec(c,"DELETE FROM teacher_classes WHERE teacher_id=? AND class_id=?",id,id);return null;});
        assertEquals(403,assertThrows(CurriculumException.class,()->service.assign(admin,id,scope)).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.progress(teacher,id,scope)).status);
    }
    @Test void studentApiUsesOnlySessionAndDoesNotAcceptScopeOverrides() throws Exception {
        var task=service.create(teacher,scope,"Task",5);service.complete(teacher,task.id(),id);
        assertEquals(Status.OK,request(Student.get(id),"/my-curriculum-progress",body()).getStatus());
        assertTrue(responseBody(request(Student.get(id),"/my-curriculum-progress",body())).contains("\"totalTokens\":5"));
        for(String field:new String[]{"studentId","teacherId","classId","grade"})
            assertEquals(Status.BAD_REQUEST,request(Student.get(id),"/my-curriculum-progress",body().replace("}",",\""+field+"\":1}")).getStatus());
        assertEquals(Status.UNAUTHORIZED,request(User.ANONYMOUS,"/my-curriculum-progress",body()).getStatus());
        assertEquals(Status.FORBIDDEN,request(Teacher.get(id),"/my-curriculum-progress",body()).getStatus());
        assertEquals(Status.CONFLICT,request(Student.get(id),"/my-curriculum-progress",body().replace("\"semesterId\":"+id,"\"semesterId\":"+(id+1))).getStatus());
        assertEquals(Status.BAD_REQUEST,request(Student.get(id),"/my-curriculum-progress","{\"subjectId\":1.5,\"semesterId\":1}").getStatus());
    }
    @Test void assignmentApiAndRosterEnforceAdminInsideHandler() throws Exception {
        for(User user:new User[]{Student.get(id),Teacher.get(id)}) {
            assertEquals(Status.FORBIDDEN,request(user,"/assign-curriculum-context",assignmentBody()).getStatus());
            assertEquals(Status.FORBIDDEN,request(user,"/curriculum-students",assignmentBody()).getStatus());
        }
        var roster=service.students(admin,scope);assertEquals(1,roster.size());
        assertEquals(java.util.Set.of("id","first_name","last_name","teacherId","classId"),roster.get(0).keySet());
    }
    @Test void semesterAssignmentsAreIndependentAndHistoricalGradeIsStable() throws Exception {
        var second=new Curriculum.Scope(id+1,id,id,id+1);service.assign(admin,id,second);
        var a=service.create(teacher,scope,"A",105);var b=service.create(other,second,"B",105);
        service.complete(teacher,a.id(),id);service.complete(other,b.id(),id);
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=6 WHERE id=?",id);return null;});
        assertEquals(105L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(105L,service.studentProgress(Student.get(id),id,id+1).get("totalTokens"));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.create(teacher,scope,"Wrong grade",0)).status);
    }
    @Test void assignmentPinsGradeEvenBeforeFirstFlexibleTask() throws Exception {
        central(70);
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=6 WHERE id=?",id);return null;});
        assertEquals(5,service.budget(teacher,scope).grade());
        assertEquals(409,assertThrows(CurriculumException.class,()->service.create(teacher,scope,"Wrong grade",1)).status);
    }
    @Test void legacyMixedCompletionsArePreservedAndRequireExplicitCorrection() throws Exception {
        var a=service.create(teacher,scope,"A",35);var b=service.create(other,secondScope(),"B",35);
        db.writeTransaction(c->{exec(c,"INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?),(?,?)",id,a.id(),id,b.id());return null;});
        db.createTables();db.createTables();
        assertEquals("context_conflict",assertThrows(CurriculumException.class,()->service.studentProgress(Student.get(id),id,id)).code);
        assertThrows(CurriculumException.class,()->service.assign(admin,id,scope));
        assertThrows(CurriculumException.class,()->service.assign(admin,id,secondScope()));
        assertEquals(2,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
    }
    @Test void overBudgetLegacyContextCannotBeAssignedCompletedOrReportedAsValid() throws Exception {
        central(70);var a=service.create(teacher,scope,"A",35);
        db.writeTransaction(c->{exec(c,"UPDATE flexible_tasks SET tokens=36 WHERE id=?",a.id());return null;});
        assertEquals("budget_exceeded",assertThrows(CurriculumException.class,()->service.assign(admin,id,scope)).code);
        assertEquals("budget_exceeded",assertThrows(CurriculumException.class,()->service.complete(teacher,a.id(),id)).code);
        assertEquals("budget_exceeded",assertThrows(CurriculumException.class,()->service.studentProgress(Student.get(id),id,id)).code);
        assertEquals(0,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
    }
    @Test void zeroTokenCompletionAlsoPreventsContextSwitch() throws Exception {
        var a=service.create(teacher,scope,"Zero",0);service.complete(teacher,a.id(),id);
        assertThrows(CurriculumException.class,()->service.assign(admin,id,secondScope()));
    }
    @Test void assignmentAndCompletionRaceCannotMixContexts() throws Exception {
        central(70);var a=service.create(teacher,scope,"A",35);var b=service.create(other,secondScope(),"B",35);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            Future<Boolean> complete=pool.submit(()->{gate.await();try{service.complete(teacher,a.id(),id);return true;}catch(CurriculumException e){assertEquals(403,e.status);return false;}});
            Future<Boolean> assign=pool.submit(()->{gate.await();try{service.assign(admin,id,secondScope());return true;}catch(CurriculumException e){assertEquals(409,e.status);return false;}});
            gate.countDown();assertNotEquals(complete.get(10,TimeUnit.SECONDS),assign.get(10,TimeUnit.SECONDS));
            if(assign.get())service.complete(other,b.id(),id);
            assertEquals(1,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
            assertEquals(35L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        }finally{pool.shutdownNow();}
    }
    @Test void explicitTransferPreservesHistoryAndCountsOnlyCurrentTargetDefinition() throws Exception {
        int central=central(70);Student.get(id).changeTaskStatus(Task.get(central),Task.STATUS_COMPLETED);
        var a=service.create(teacher,scope,"A",35);var b=service.create(other,secondScope(),"B",35);
        service.complete(teacher,a.id(),id);
        assertEquals(1,((List<?>)service.transferPreview(admin,id,secondScope()).get("completions")).size());
        service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),35)));
        assertEquals(2,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
        assertEquals(1,scalar("SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=?",id));
        assertEquals(105L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(403,assertThrows(CurriculumException.class,()->service.complete(teacher,a.id(),id)).status);
        service.complete(other,b.id(),id);assertEquals(105L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(teacher,a.id(),"Historical edit",30)).status);
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(other,b.id(),"Current edit",30)).status);
        service.edit(other,b.id(),"Current edit",35);assertEquals(105L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertThrows(CurriculumException.class,()->service.edit(other,b.id(),"Over budget",36));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),35)))).status);
    }
    @Test void failedTransferRollsBackEarlierMappingsAndAssignment() throws Exception {
        var a=service.create(teacher,scope,"A",5);var a2=service.create(teacher,scope,"A2",6);
        var b=service.create(other,secondScope(),"B",5);var b2=service.create(other,secondScope(),"B2",7);
        service.complete(teacher,a.id(),id);service.complete(teacher,a2.id(),id);
        assertThrows(CurriculumException.class,()->service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),5),new Curriculum.Transfer(a2.id(),b2.id(),6))));
        assertEquals(id,scalar("SELECT teacher FROM student_curriculum_contexts WHERE student=?",id));
        assertEquals(2,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=?",id));
        assertEquals(11L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
    }
    @Test void transferRejectsMissingDuplicatedForeignAndStaleMappings() throws Exception {
        var a=service.create(teacher,scope,"A",5);var a2=service.create(teacher,scope,"A2",5);
        var b=service.create(other,secondScope(),"B",5);var b2=service.create(other,secondScope(),"B2",5);
        service.complete(teacher,a.id(),id);service.complete(teacher,a2.id(),id);
        for(var mappings:List.of(
                List.<Curriculum.Transfer>of(),
                List.of(new Curriculum.Transfer(a.id(),b.id(),5)),
                List.of(new Curriculum.Transfer(a.id(),b.id(),5),new Curriculum.Transfer(a.id(),b2.id(),5)),
                List.of(new Curriculum.Transfer(a.id(),b.id(),5),new Curriculum.Transfer(a2.id(),b.id(),5)),
                List.of(new Curriculum.Transfer(b.id(),a.id(),5),new Curriculum.Transfer(a2.id(),b2.id(),5)),
                List.of(new Curriculum.Transfer(a.id(),a2.id(),5),new Curriculum.Transfer(a2.id(),b2.id(),5)),
                List.of(new Curriculum.Transfer(a.id(),b.id(),4),new Curriculum.Transfer(a2.id(),b2.id(),5)))) {
            assertThrows(CurriculumException.class,()->service.transfer(admin,id,scope,secondScope(),mappings));
            assertEquals(0,scalar("SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=?",id));
        }
        assertEquals(403,assertThrows(CurriculumException.class,()->service.transfer(teacher,id,scope,secondScope(),List.of())).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.transferPreview(teacher,id,secondScope())).status);
    }
    @Test void transferChainCannotReactivatePreviouslyTransferredCompletions() throws Exception {
        var a=service.create(teacher,scope,"A",5);var b=service.create(other,secondScope(),"B",5);
        service.complete(teacher,a.id(),id);
        service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),5)));
        assertThrows(CurriculumException.class,()->service.transfer(admin,id,secondScope(),scope,List.of(new Curriculum.Transfer(b.id(),a.id(),5))));
        var a2=service.create(teacher,scope,"New target",5);
        service.transfer(admin,id,secondScope(),scope,List.of(new Curriculum.Transfer(b.id(),a2.id(),5)));
        assertEquals(5L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertThrows(CurriculumException.class,()->service.complete(teacher,a.id(),id));
        assertEquals(3,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
    }
    @Test void targetBudgetAndGradeMustRemainValidDuringTransfer() throws Exception {
        central(70);var a=service.create(teacher,scope,"A",35);var b=service.create(other,secondScope(),"B",35);
        service.complete(teacher,a.id(),id);
        db.writeTransaction(c->{exec(c,"INSERT INTO flexible_tasks(owner_teacher,subject,class,semester,grade,name,tokens) VALUES(?,?,?,?,5,'Legacy extra',1)",id+1,id,id,id);return null;});
        assertEquals("budget_exceeded",assertThrows(CurriculumException.class,()->service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),35)))).code);
        assertEquals(id,scalar("SELECT teacher FROM student_curriculum_contexts WHERE student=?",id));
    }
    @Test void concurrentTransfersCannotDuplicateAwards() throws Exception {
        var a=service.create(teacher,scope,"A",5);var b=service.create(other,secondScope(),"B",5);
        service.complete(teacher,a.id(),id);
        ExecutorService pool=Executors.newFixedThreadPool(2);CountDownLatch gate=new CountDownLatch(1);
        try {
            Callable<Boolean> work=()->{gate.await();try{service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),5)));return true;}catch(CurriculumException e){assertEquals(409,e.status);return false;}};
            var first=pool.submit(work);var second=pool.submit(work);gate.countDown();
            assertNotEquals(first.get(10,TimeUnit.SECONDS),second.get(10,TimeUnit.SECONDS));
            assertEquals(1,scalar("SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=?",id));
            assertEquals(5L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        }finally{pool.shutdownNow();}
    }
    @Test void nonAdminCannotTransferViaHandlerEvenWithExplicitMapping() throws Exception {
        String payload=assignmentBody().replace("}",",\"sourceTeacherId\":"+(id+1)+",\"sourceClassId\":"+id+",\"transfers\":[]}");
        assertEquals(Status.FORBIDDEN,request(Teacher.get(id),"/transfer-curriculum-context",payload).getStatus());
        assertEquals(Status.FORBIDDEN,request(Student.get(id),"/transfer-curriculum-context",payload).getStatus());
    }
    @Test void adminHttpTransferValidatesMappingAndReturnsSafeProgress() throws Exception {
        User adminUser=Admin.create("curriculum-admin-"+id,"synthetic-test-only");
        assertEquals(Status.OK,request(adminUser,"/assign-curriculum-context",assignmentBody()).getStatus());
        assertEquals(Status.OK,request(adminUser,"/curriculum-students",assignmentBody()).getStatus());
        var a=service.create(teacher,scope,"A",5);var b=service.create(other,secondScope(),"B",5);service.complete(teacher,a.id(),id);
        String target=assignmentBody().replace("\"teacherId\":"+id,"\"teacherId\":"+(id+1));
        assertEquals(Status.OK,request(adminUser,"/curriculum-transfer-preview",target).getStatus());
        String prefix=target.substring(0,target.length()-1)+",\"sourceTeacherId\":"+id+",\"sourceClassId\":"+id+",\"transfers\":";
        assertEquals(Status.BAD_REQUEST,request(adminUser,"/transfer-curriculum-context",prefix+"[null]}").getStatus());
        assertEquals(Status.BAD_REQUEST,request(adminUser,"/transfer-curriculum-context",prefix+"[{\"sourceTaskId\":1.5,\"targetTaskId\":1,\"tokens\":5}]}").getStatus());
        String mappings="[{\"sourceTaskId\":"+a.id()+",\"targetTaskId\":"+b.id()+",\"tokens\":5}]";
        assertEquals(Status.OK,request(adminUser,"/transfer-curriculum-context",prefix+mappings+"}").getStatus());
        assertEquals(5L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
    }
    @Test void explicitTransferCanResolveLegacyMixedCompletionsWithoutDeletingHistory() throws Exception {
        var a=service.create(teacher,scope,"A",5);var legacy=service.create(other,secondScope(),"Legacy",5);
        var b=service.create(other,secondScope(),"Replacement A",5);var b2=service.create(other,secondScope(),"Replacement legacy",5);
        service.complete(teacher,a.id(),id);
        db.writeTransaction(c->{exec(c,"INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?)",id,legacy.id());return null;});
        unassign();
        assertEquals(new Curriculum.Scope(0,id,0,id),service.transferPreview(admin,id,secondScope()).get("source"));
        service.transfer(admin,id,new Curriculum.Scope(0,id,0,id),secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),5),new Curriculum.Transfer(legacy.id(),b2.id(),5)));
        assertEquals(10L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(4,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
        db.createTables();assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=?",id));
    }
    @Test void unassignedLegacyCompletionCanBeAssignedOnlyToItsActualContext() throws Exception {
        var a=service.create(teacher,scope,"A",5);service.complete(teacher,a.id(),id);unassign();
        assertThrows(CurriculumException.class,()->service.assign(admin,id,secondScope()));
        service.assign(admin,id,scope);assertEquals(5L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
    }
    @Test void transferDoesNotSilentlyChangeCentralGradeAfterPromotion() throws Exception {
        db.writeTransaction(c->{exec(c,"UPDATE students SET class=? WHERE id=?",id+1,id);exec(c,"UPDATE classes SET grade=6 WHERE id=?",id+1);return null;});
        var target=new Curriculum.Scope(id+1,id,id+1,id);
        assertThrows(CurriculumException.class,()->service.assign(admin,id,target));
        assertThrows(CurriculumException.class,()->service.transfer(admin,id,scope,target,List.of()));
        assertEquals(id,scalar("SELECT class FROM student_curriculum_contexts WHERE student=?",id));
    }
    @SuppressWarnings("unchecked")
    List<Curriculum.CompletedCentralTask> centralDetails(Map<String,Object> result) {
        return (List<Curriculum.CompletedCentralTask>) result.get("completedCentralTasks");
    }
    @SuppressWarnings("unchecked")
    List<Curriculum.CompletedFlexibleTask> flexibleDetails(Map<String,Object> result) {
        return (List<Curriculum.CompletedFlexibleTask>) result.get("completedFlexibleTasks");
    }
    void assertDetailSums(Map<String,Object> result) {
        long central=centralDetails(result).stream().mapToLong(Curriculum.CompletedCentralTask::tokens).sum();
        long flexible=flexibleDetails(result).stream().mapToLong(Curriculum.CompletedFlexibleTask::tokens).sum();
        assertEquals(central,result.get("centralTokens"));assertEquals(flexible,result.get("flexibleTokens"));
        assertEquals(central+flexible,result.get("totalTokens"));
    }
    Map<String,Object> ownDetails() throws Exception {
        var result=service.studentProgress(Student.get(id),id,id);assertDetailSums(result);return result;
    }
    @Test void detailsIncludeOnlyCompletedCentralAndFlexibleTasks() throws Exception {
        int done=central(6);Student.get(id).changeTaskStatus(Task.get(done),Task.STATUS_COMPLETED);
        int pending=service.createCentralTask(topic,"Pending",TaskLevel.LEVEL2,3);
        Student.get(id).changeTaskStatus(Task.get(pending),Task.STATUS_IN_PROGRESS);
        service.createCentralTask(topic,"Not started",TaskLevel.LEVEL3,2);
        var flexible=service.create(teacher,scope,"Completed flexible",6);service.complete(teacher,flexible.id(),id);
        service.create(teacher,scope,"Not completed",5);
        var result=ownDetails();
        assertEquals(List.of(new Curriculum.CompletedCentralTask(done,"Central",6,1,topic,"Topic-"+id)),centralDetails(result));
        assertEquals(List.of(new Curriculum.CompletedFlexibleTask(flexible.id(),"Completed flexible",6)),flexibleDetails(result));
        assertEquals(12L,result.get("totalTokens"));
    }
    @Test void centralDetailsReflectCurrentTaskAndTopicDefinitions() throws Exception {
        int task=central(6);Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        assertEquals(6L,ownDetails().get("totalTokens"));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.editTask(admin,task,"Renamed task",4)).status);
        service.editTask(admin,task,"Renamed task",6);service.renameTopic(admin,topic,"Renamed topic");
        var result=ownDetails();assertEquals(6L,result.get("totalTokens"));
        assertEquals(new Curriculum.CompletedCentralTask(task,"Renamed task",6,1,topic,"Renamed topic"),centralDetails(result).get(0));
    }
    @Test void flexibleDetailsReflectCurrentDefinitionAfterCompletion() throws Exception {
        var task=service.create(teacher,scope,"Flexible",6);service.complete(teacher,task.id(),id);
        assertEquals(6L,ownDetails().get("totalTokens"));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(teacher,task.id(),"Renamed flexible",4)).status);
        service.edit(teacher,task.id(),"Renamed flexible",6);
        var result=ownDetails();assertEquals(6L,result.get("totalTokens"));
        assertEquals(List.of(new Curriculum.CompletedFlexibleTask(task.id(),"Renamed flexible",6)),flexibleDetails(result));
    }
    @Test void detailArraysKeepZeroTokensAndDistinctIdsWithIdenticalNames() throws Exception {
        int first=central(0);Student.get(id).changeTaskStatus(Task.get(first),Task.STATUS_COMPLETED);
        int secondTopic=service.createTopic(admin,id,5,id,2,"Another topic");
        int second=service.createCentralTask(secondTopic,"Central",TaskLevel.LEVEL2,0);
        Student.get(id).changeTaskStatus(Task.get(second),Task.STATUS_COMPLETED);
        var flexible=service.create(teacher,scope,"Central",0);service.complete(teacher,flexible.id(),id);
        var result=ownDetails();assertEquals(0L,result.get("totalTokens"));
        assertEquals(List.of(first,second),centralDetails(result).stream().map(Curriculum.CompletedCentralTask::id).toList());
        assertEquals(List.of(new Curriculum.CompletedFlexibleTask(flexible.id(),"Central",0)),flexibleDetails(result));
    }
    @Test void assignedContextWithoutCompletionsReturnsEmptyArrays() throws Exception {
        central(6);service.create(teacher,scope,"Not completed",6);
        var result=ownDetails();assertTrue(centralDetails(result).isEmpty());assertTrue(flexibleDetails(result).isEmpty());
        assertEquals(0L,result.get("totalTokens"));
    }
    @Test void detailsExcludeOtherStudentsTeachersSubjectsSemestersAndGrades() throws Exception {
        int own=central(6);Student.get(id).changeTaskStatus(Task.get(own),Task.STATUS_COMPLETED);
        for(int n=0;n<3;n++) {
            int otherTopic=service.createTopic(admin,n==2?id+1:id,n==1?6:5,n==0?id+1:id,n==0?2:1,"Excluded topic "+n);
            int task=service.createCentralTask(otherTopic,"Excluded task",TaskLevel.LEVEL1,10);
            db.writeTransaction(c->{exec(c,"INSERT INTO taskstats(student,task,status) VALUES(?,?,2)",id,task);return null;});
        }
        db.writeTransaction(c->{exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Other','Student',?,'unused',?,1)",id+1,"details"+id+"@example.invalid",id);return null;});
        service.assign(admin,id+1,secondScope());
        var first=service.create(teacher,scope,"Own flexible",5);service.complete(teacher,first.id(),id);
        var second=service.create(other,secondScope(),"Other teacher flexible",8);service.complete(other,second.id(),id+1);
        int otherStudentOnly=service.createCentralTask(topic,"Other student central",TaskLevel.LEVEL1,7);
        Student.get(id+1).changeTaskStatus(Task.get(otherStudentOnly),Task.STATUS_COMPLETED);
        var nextSemester=new Curriculum.Scope(id,id,id,id+1);service.assign(admin,id,nextSemester);
        var later=service.create(teacher,nextSemester,"Later flexible",9);service.complete(teacher,later.id(),id);
        var result=ownDetails();assertEquals(List.of(own),centralDetails(result).stream().map(Curriculum.CompletedCentralTask::id).toList());
        assertEquals(List.of(first.id()),flexibleDetails(result).stream().map(Curriculum.CompletedFlexibleTask::id).toList());
        var another=service.studentProgress(Student.get(id+1),id,id);assertDetailSums(another);
        assertEquals(List.of(second.id()),flexibleDetails(another).stream().map(Curriculum.CompletedFlexibleTask::id).toList());
        assertEquals(List.of(otherStudentOnly),centralDetails(another).stream().map(Curriculum.CompletedCentralTask::id).toList());
        var laterResult=service.studentProgress(Student.get(id),id,id+1);assertDetailSums(laterResult);
        assertEquals(List.of(later.id()),flexibleDetails(laterResult).stream().map(Curriculum.CompletedFlexibleTask::id).toList());
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=6 WHERE id=?",id);return null;});
        assertEquals(result,ownDetails()); // Pinned grade 5 remains the authoritative central selection.
    }
    @Test void transferDetailsContainOnlyTerminalCompletionAndPreserveHistory() throws Exception {
        var a=service.create(teacher,scope,"A",6);var b=service.create(other,secondScope(),"B",6);
        service.complete(teacher,a.id(),id);
        service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),6)));
        assertEquals(List.of(new Curriculum.CompletedFlexibleTask(b.id(),"B",6)),flexibleDetails(ownDetails()));
        assertEquals(1,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,a.id()));
        var terminal=service.create(teacher,scope,"Terminal",6);
        service.transfer(admin,id,secondScope(),scope,List.of(new Curriculum.Transfer(b.id(),terminal.id(),6)));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(teacher,a.id(),"Historical A",4)).status);
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(other,b.id(),"Historical B",4)).status);
        service.edit(teacher,terminal.id(),"Current terminal",6);
        var result=ownDetails();assertEquals(6L,result.get("totalTokens"));
        assertEquals(List.of(new Curriculum.CompletedFlexibleTask(terminal.id(),"Current terminal",6)),flexibleDetails(result));
        assertEquals(3,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
        assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=?",id));
    }
    com.google.gson.JsonObject jsonResponse(User user,String path,String payload) {
        var response=request(user,path,payload);assertEquals(Status.OK,response.getStatus());
        return com.google.gson.JsonParser.parseString(responseBody(response).split("\r\n\r\n",2)[1]).getAsJsonObject();
    }
    @Test void bothHttpResponsesExposeSameAdditiveTypedContractWithoutPersonalData() throws Exception {
        int task=central(6);Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        var flexible=service.create(teacher,scope,"Quoted \"name\"",4);service.complete(teacher,flexible.id(),id);
        var own=jsonResponse(Student.get(id),"/my-curriculum-progress",body());
        var staff=jsonResponse(Teacher.get(id),"/curriculum-progress",assignmentBody());assertEquals(own,staff);
        var adminUser=Admin.create("details-admin-"+id,"synthetic-test-only");
        assertEquals(own,jsonResponse(adminUser,"/curriculum-progress",assignmentBody()));
        assertEquals(Set.of("semesterId","centralTokens","flexibleTokens","totalTokens","completedCentralTasks","completedFlexibleTasks"),own.keySet());
        var central=own.getAsJsonArray("completedCentralTasks").get(0).getAsJsonObject();
        assertEquals(Set.of("id","name","tokens","niveau","topicId","topicName"),central.keySet());
        var detail=own.getAsJsonArray("completedFlexibleTasks").get(0).getAsJsonObject();
        assertEquals(Set.of("id","name","tokens"),detail.keySet());assertEquals("Quoted \"name\"",detail.get("name").getAsString());
        long centralSum=0,flexibleSum=0;
        for(var entry:own.getAsJsonArray("completedCentralTasks"))centralSum+=entry.getAsJsonObject().get("tokens").getAsLong();
        for(var entry:own.getAsJsonArray("completedFlexibleTasks"))flexibleSum+=entry.getAsJsonObject().get("tokens").getAsLong();
        assertEquals(centralSum,own.get("centralTokens").getAsLong());assertEquals(flexibleSum,own.get("flexibleTokens").getAsLong());
        assertEquals(centralSum+flexibleSum,own.get("totalTokens").getAsLong());
    }
    @Test void detailsDoNotWeakenAssignmentScopeOrBudgetChecks() throws Exception {
        central(70);var task=service.create(teacher,scope,"Flexible",35);service.complete(teacher,task.id(),id);
        assertEquals(35L,ownDetails().get("totalTokens"));
        assertThrows(CurriculumException.class,()->service.edit(teacher,task.id(),"Too much",36));
        assertEquals(403,assertThrows(CurriculumException.class,()->service.progress(admin,id,secondScope())).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.progress(other,id,scope)).status);
        unassign();
        for(User user:List.of(Student.get(id),Teacher.get(id))) {
            var response=request(user,user.isStudent()?"/my-curriculum-progress":"/curriculum-progress",user.isStudent()?body():assignmentBody());
            assertEquals(Status.CONFLICT,response.getStatus());
            assertTrue(responseBody(response).contains("context_unassigned"));assertFalse(responseBody(response).contains("completedCentralTasks"));
        }
    }
    @Test void detailsAndSumsStayConsistentDuringConcurrentDefinitionEdits() throws Exception {
        int task=central(6);Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        var flexible=service.create(teacher,scope,"Flexible",6);service.complete(teacher,flexible.id(),id);
        ExecutorService pool=Executors.newSingleThreadExecutor();
        try {
            var edits=pool.submit(()->{for(int n=0;n<20;n++){service.editTask(admin,task,"Central "+n,6);service.edit(teacher,flexible.id(),"Flexible "+n,6);}return null;});
            for(int n=0;n<20;n++)assertDetailSums(ownDetails());
            edits.get(10,TimeUnit.SECONDS);
        }finally{pool.shutdownNow();}
    }
    @AfterEach void clearCurrentYearDates() throws Exception {
        // Only this case's synthetic school year; other tests share the isolated JVM.
        db.writeTransaction(c->{exec(c,"UPDATE school_years SET start_date=NULL,end_date=NULL WHERE id=?",id);return null;});
    }
    SchoolYear currentYear() throws Exception {
        var today=java.time.LocalDate.now();
        db.writeTransaction(c->{exec(c,"UPDATE school_years SET start_date=?,end_date=? WHERE id=?",
                today.minusDays(1).toString(),today.plusDays(1).toString(),id);return null;});
        return SchoolYear.getCurrentYear(false);
    }
    String progressPath(boolean student) {return student?"/my-curriculum-progress":"/curriculum-progress";}
    User progressUser(boolean student) {return student?Student.get(id):Teacher.get(id);}
    String progressBody(boolean student,boolean explicit) {
        String payload=student?body():assignmentBody();
        return explicit?payload:payload.replace(",\"semesterId\":"+id,"");
    }
    void assertProgressError(boolean student,String payload,Status status,String code) {
        var response=request(progressUser(student),progressPath(student),payload);
        assertEquals(status,response.getStatus());assertTrue(responseBody(response).contains("\"error\":\""+code+"\""));
        assertFalse(com.google.gson.JsonParser.parseString(responseBody(response).split("\r\n\r\n",2)[1]).getAsJsonObject().has("totalTokens"));
    }
    void assertJsonSums(com.google.gson.JsonObject result) {
        long central=0,flexible=0;
        for(var task:result.getAsJsonArray("completedCentralTasks"))central+=task.getAsJsonObject().get("tokens").getAsLong();
        for(var task:result.getAsJsonArray("completedFlexibleTasks"))flexible+=task.getAsJsonObject().get("tokens").getAsLong();
        assertEquals(central,result.get("centralTokens").getAsLong());
        assertEquals(flexible,result.get("flexibleTokens").getAsLong());
        assertEquals(central+flexible,result.get("totalTokens").getAsLong());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void currentSemesterSwitchKeepsExplicitHistoryAndIsolatesDetails(boolean student) throws Exception {
        var year=currentYear();assertNotNull(year);year=year.setCurrentSemester(Semester.get(id));
        int first=central(6);Student.get(id).changeTaskStatus(Task.get(first),Task.STATUS_COMPLETED);
        var a=service.create(teacher,scope,"A",4);service.complete(teacher,a.id(),id);
        var second=new Curriculum.Scope(id,id,id,id+1);service.assign(admin,id,second);
        int secondTopic=service.createTopic(admin,id,5,id+1,2,"Second semester");
        int secondTask=service.createCentralTask(secondTopic,"Second central",TaskLevel.LEVEL1,7);
        Student.get(id).changeTaskStatus(Task.get(secondTask),Task.STATUS_COMPLETED);
        var b=service.create(teacher,second,"B",8);service.complete(teacher,b.id(),id);
        var explicit=jsonResponse(progressUser(student),progressPath(student),progressBody(student,true));
        assertEquals(id,explicit.get("semesterId").getAsInt());assertEquals(10,explicit.get("totalTokens").getAsInt());
        assertEquals(explicit,jsonResponse(progressUser(student),progressPath(student),progressBody(student,false)));
        // Use the real API, including its replacement-object cache behavior.
        year.setCurrentSemester(Semester.get(id+1));
        assertEquals(id+1,SchoolYear.get(id).getCurrentSemester().getId());
        var next=jsonResponse(progressUser(student),progressPath(student),progressBody(student,false));
        assertEquals(id+1,next.get("semesterId").getAsInt());assertEquals(15,next.get("totalTokens").getAsInt());
        assertEquals(secondTask,next.getAsJsonArray("completedCentralTasks").get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals(b.id(),next.getAsJsonArray("completedFlexibleTasks").get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals(1,next.getAsJsonArray("completedCentralTasks").size());assertEquals(1,next.getAsJsonArray("completedFlexibleTasks").size());
        assertJsonSums(explicit);assertJsonSums(next);
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=6 WHERE id=?",id);return null;});
        assertEquals(explicit,jsonResponse(progressUser(student),progressPath(student),progressBody(student,true)));
        assertEquals(next,jsonResponse(progressUser(student),progressPath(student),progressBody(student,false)));
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void missingConfiguredSemesterFailsButExplicitHistoryStillWorks(boolean student) throws Exception {
        assertNull(currentYear().getCurrentSemester());
        assertProgressError(student,progressBody(student,false),Status.CONFLICT,"current_semester_unavailable");
        assertEquals(id,jsonResponse(progressUser(student),progressPath(student),progressBody(student,true)).get("semesterId").getAsInt());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void noDeterminedYearDoesNotUseLegacyLatestLabel(boolean student) throws Exception {
        SchoolYear.get(id).setCurrentSemester(Semester.get(id));
        assertNull(SchoolYear.getCurrentYear(false));
        assertNotNull(SchoolYear.getCurrentYear()); // Legacy selection still exists for old callers only.
        assertProgressError(student,progressBody(student,false),Status.CONFLICT,"current_semester_unavailable");
        assertEquals(id,jsonResponse(progressUser(student),progressPath(student),progressBody(student,true)).get("semesterId").getAsInt());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void resolvedSemesterStillRequiresAssignment(boolean student) throws Exception {
        currentYear().setCurrentSemester(Semester.get(id+1));
        assertProgressError(student,progressBody(student,false),Status.CONFLICT,"context_unassigned");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void explicitInvalidSemesterIsNotTreatedAsMissing(boolean student) throws Exception {
        currentYear().setCurrentSemester(Semester.get(id));
        for(String value:List.of("null","-1","1.5","\"bad\""))
            assertProgressError(student,progressBody(student,true).replace("\"semesterId\":"+id,"\"semesterId\":"+value),Status.BAD_REQUEST,"invalid_input");
    }
    @Test void fallbackKeepsStudentScopeAndSessionRestrictions() throws Exception {
        for(String field:List.of("studentId","teacherId","classId","grade"))
            assertProgressError(true,progressBody(true,false).replace("}",",\""+field+"\":1}"),Status.BAD_REQUEST,"invalid_input");
        for(boolean student:List.of(true,false))
            assertEquals(Status.UNAUTHORIZED,request(User.ANONYMOUS,progressPath(student),progressBody(student,false)).getStatus());
        assertEquals(Status.FORBIDDEN,request(Teacher.get(id),progressPath(true),progressBody(true,false)).getStatus());
        assertEquals(Status.FORBIDDEN,request(Student.get(id),progressPath(false),progressBody(false,false)).getStatus());
    }
    @Test void fallbackKeepsTeacherAndAdminScopeOwnership() throws Exception {
        currentYear().setCurrentSemester(Semester.get(id));
        var adminUser=Admin.create("semester-admin-"+id,"synthetic-test-only");
        var expected=jsonResponse(Student.get(id),progressPath(true),progressBody(true,false));
        assertEquals(expected,jsonResponse(adminUser,progressPath(false),progressBody(false,false)));
        assertEquals(Status.FORBIDDEN,request(Teacher.get(id+1),progressPath(false),progressBody(false,false)).getStatus());
        String otherTeacher=progressBody(false,false).replace("\"teacherId\":"+id,"\"teacherId\":"+(id+1));
        assertEquals(Status.FORBIDDEN,request(adminUser,progressPath(false),otherTeacher).getStatus());
        db.writeTransaction(c->{exec(c,"DELETE FROM teacher_subjects WHERE teacher_id=? AND subject_id=?",id,id);return null;});
        assertEquals(expected,jsonResponse(adminUser,progressPath(false),progressBody(false,false))); // Existing admin read authorization.
        assertProgressError(false,progressBody(false,false),Status.FORBIDDEN,"forbidden");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void fallbackStillRejectsOversubscribedContext(boolean student) throws Exception {
        currentYear().setCurrentSemester(Semester.get(id));central(105);
        db.writeTransaction(c->{exec(c,"INSERT INTO flexible_tasks(owner_teacher,subject,class,semester,grade,name,tokens) VALUES(?,?,?,?,5,'Legacy excess',1)",id,id,id,id);return null;});
        assertProgressError(student,progressBody(student,false),Status.CONFLICT,"budget_exceeded");
    }
    @Test void coldSemesterResolutionDoesNotRecurseThroughSchoolYear() throws Exception {
        currentYear();
        // Neither this semester nor a year with this current-semester pointer was cached.
        db.writeTransaction(c->{exec(c,"UPDATE school_years SET current_semester=? WHERE id=?",id+1,id);return null;});
        service.assign(admin,id,new Curriculum.Scope(id,id,id,id+1));
        var yearCache=SchoolYear.class.getDeclaredField("years");yearCache.setAccessible(true);
        ((Map<?,?>)yearCache.get(null)).clear();
        var semesterCache=Semester.class.getDeclaredField("CACHE");semesterCache.setAccessible(true);
        ((Map<?,?>)semesterCache.get(null)).clear();
        assertEquals(id+1,jsonResponse(Student.get(id),progressPath(true),progressBody(true,false)).get("semesterId").getAsInt());
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void unresolvedConfiguredSemesterDoesNotSelectAnother(boolean student) throws Exception {
        currentYear();
        db.writeTransaction(c->{exec(c,"UPDATE school_years SET current_semester=999999 WHERE id=?",id);return null;});
        assertProgressError(student,progressBody(student,false),Status.CONFLICT,"current_semester_unavailable");
    }
    @ParameterizedTest @ValueSource(booleans={true,false})
    void fallbackPreservesTransferChainAndCurrentTerminalValues(boolean student) throws Exception {
        currentYear().setCurrentSemester(Semester.get(id));
        var a=service.create(teacher,scope,"A",6);var b=service.create(other,secondScope(),"B",6);
        service.complete(teacher,a.id(),id);
        service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),6)));
        String target=progressBody(student,false);
        if(!student)target=target.replace("\"teacherId\":"+id,"\"teacherId\":"+(id+1));
        var afterB=jsonResponse(student?Student.get(id):Teacher.get(id+1),progressPath(student),target);
        assertEquals(b.id(),afterB.getAsJsonArray("completedFlexibleTasks").get(0).getAsJsonObject().get("id").getAsInt());
        var terminal=service.create(teacher,scope,"Terminal",6);
        service.transfer(admin,id,secondScope(),scope,List.of(new Curriculum.Transfer(b.id(),terminal.id(),6)));
        service.edit(teacher,terminal.id(),"Current terminal",6);
        var result=jsonResponse(progressUser(student),progressPath(student),progressBody(student,false));
        assertEquals(id,result.get("semesterId").getAsInt());assertEquals(6,result.get("totalTokens").getAsInt());
        assertEquals(1,result.getAsJsonArray("completedFlexibleTasks").size());
        assertEquals(terminal.id(),result.getAsJsonArray("completedFlexibleTasks").get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals(3,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
        assertJsonSums(afterB);assertJsonSums(result);
    }
    @Test void onlyProgressAllowsOmittedSemesterAndOtherFieldsStayRequired() throws Exception {
        currentYear().setCurrentSemester(Semester.get(id));
        for(String path:List.of("/curriculum-budget","/flexible-tasks"))
            assertEquals(Status.BAD_REQUEST,request(Teacher.get(id),path,progressBody(false,false)).getStatus());
        assertProgressError(true,"{}",Status.BAD_REQUEST,"invalid_input");
        for(String field:List.of("studentId","subjectId","classId")) {
            var payload=com.google.gson.JsonParser.parseString(progressBody(false,false)).getAsJsonObject();payload.remove(field);
            assertProgressError(false,payload.toString(),Status.BAD_REQUEST,"invalid_input");
        }
    }
    @Test void flexibleTopicsAreOwnedAndDoNotChangeCentralTopics() throws Exception {
        int own=service.createFlexibleTopic(teacher,scope,"Shared name");
        int theirs=service.createFlexibleTopic(other,secondScope(),"Shared name");
        assertNotEquals(own,theirs);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.renameFlexibleTopic(other,own,"No")).status);
        service.renameFlexibleTopic(teacher,own,"Own revised");
        assertEquals("Topic-"+id,Topic.get(topic).getName());
        assertEquals("Own revised",((List<Map<String,Object>>)service.flexibleStructure(teacher,scope).get("topics")).get(0).get("name"));
        assertEquals(409,assertThrows(CurriculumException.class,()->service.createFlexibleTopic(teacher,scope,"Own revised")).status);
        assertEquals(400,assertThrows(CurriculumException.class,()->service.createFlexibleTopic(teacher,scope," ")).status);
        db.writeTransaction(c->{exec(c,"DELETE FROM teacher_subjects WHERE teacher_id=? AND subject_id=?",id,id);return null;});
        assertEquals(403,assertThrows(CurriculumException.class,()->service.renameFlexibleTopic(teacher,own,"Revoked")).status);
    }
    @Test void topicAssociationIsAtomicAndOldEditsPreserveIt() throws Exception {
        int own=service.createFlexibleTopic(teacher,scope,"Own");
        int foreign=service.createFlexibleTopic(other,secondScope(),"Other");
        var task=service.create(teacher,scope,"Linked",5,own);
        service.edit(teacher,task.id(),"Renamed",4);
        assertEquals(own,scalar("SELECT flexible_topic FROM flexible_task_topics WHERE flexible_task=?",task.id()));
        assertEquals(403,assertThrows(CurriculumException.class,()->service.edit(teacher,task.id(),"Bad edit",8,true,foreign)).status);
        assertEquals("Renamed",service.list(teacher,scope).get(0).name());
        assertEquals(4,service.list(teacher,scope).get(0).tokens());
        assertEquals(403,assertThrows(CurriculumException.class,()->service.create(teacher,scope,"Bad create",6,foreign)).status);
        assertEquals(1,service.list(teacher,scope).size());
        service.edit(teacher,task.id(),"Detached",4,true,null);
        assertEquals(0,scalar("SELECT COUNT(*) FROM flexible_task_topics WHERE flexible_task=?",task.id()));
    }
    @Test void topicAssociationRejectsOtherClassSubjectAndSemester() throws Exception {
        var task=service.create(teacher,scope,"Own",5);
        for(var foreignScope:List.of(new Curriculum.Scope(id,id,id+1,id),new Curriculum.Scope(id,id,id,id+1),new Curriculum.Scope(id,id+1,id,id))) {
            int foreign=service.createFlexibleTopic(admin,foreignScope,"Foreign");
            assertEquals(403,assertThrows(CurriculumException.class,()->service.edit(admin,task.id(),"No",5,true,foreign)).status);
        }
        assertEquals(0,scalar("SELECT COUNT(*) FROM flexible_task_topics WHERE flexible_task=?",task.id()));
    }
    @Test void legacyTasksAndCompletionsSurviveRepeatedSchemaCreation() throws Exception {
        var task=service.create(teacher,scope,"Existing",5);service.complete(teacher,task.id(),id);
        db.createTables();db.createTables();
        var result=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        var entry=result.getAsJsonArray("flexibleTasks").get(0).getAsJsonObject();
        assertEquals(task.id(),entry.get("id").getAsInt());assertTrue(entry.get("completed").getAsBoolean());
        assertTrue(!entry.has("topicId") || entry.get("topicId").isJsonNull());
        assertEquals(5,result.getAsJsonObject("progress").get("totalTokens").getAsInt());
        int newTopic=service.createFlexibleTopic(teacher,scope,"New");
        service.edit(teacher,task.id(),"Existing",5,true,newTopic);
        db.createTables();assertEquals(newTopic,scalar("SELECT flexible_topic FROM flexible_task_topics WHERE flexible_task=?",task.id()));
    }
    @Test void catalogSeparatesPlansFromAwardsAndReadsRenamesFresh() throws Exception {
        int centralId=central(70);new CurriculumEnrollment(service).release(teacher,scope,topic,null,true);int own=service.createFlexibleTopic(teacher,scope,"Practice");
        var flexible=service.create(teacher,scope,"Exercise",35,own);
        var zero=service.create(teacher,scope,"Zero",0,own);
        var before=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertEquals(105,before.getAsJsonObject("planned").get("totalTokens").getAsInt());
        assertEquals(100,before.getAsJsonObject("planned").get("regularLimit").getAsInt());
        assertEquals(0,before.getAsJsonObject("progress").get("totalTokens").getAsInt());
        assertFalse(before.getAsJsonArray("centralTasks").get(0).getAsJsonObject().get("completed").getAsBoolean());
        service.complete(teacher,flexible.id(),id);service.complete(teacher,flexible.id(),id);service.complete(teacher,zero.id(),id);
        Student.get(id).changeTaskStatus(Task.get(centralId),Task.STATUS_COMPLETED);
        assertEquals(409,assertThrows(CurriculumException.class,()->service.edit(teacher,flexible.id(),"Updated exercise",30)).status);
        service.edit(teacher,flexible.id(),"Updated exercise",35);service.renameFlexibleTopic(teacher,own,"Updated topic");
        var after=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertEquals(105,after.getAsJsonObject("progress").get("totalTokens").getAsInt());
        var entry=after.getAsJsonArray("flexibleTasks").get(0).getAsJsonObject();
        assertEquals("Updated topic",entry.get("topicName").getAsString());assertEquals("Updated exercise",entry.get("name").getAsString());
        assertEquals(35,entry.get("tokens").getAsInt());assertTrue(entry.get("completed").getAsBoolean());
        assertEquals(2,after.getAsJsonArray("flexibleTasks").size());
        assertEquals(jsonResponse(Student.get(id),"/my-curriculum-progress",body()),after.getAsJsonObject("progress"));
    }
    @Test void catalogOnlyShowsAssignedContextAndActiveTransferredCompletion() throws Exception {
        int firstTopic=service.createFlexibleTopic(teacher,scope,"Source topic");
        int targetTopic=service.createFlexibleTopic(other,secondScope(),"Target topic");
        var a=service.create(teacher,scope,"Same name",5,firstTopic);
        var b=service.create(other,secondScope(),"Same name",5,targetTopic);
        service.complete(teacher,a.id(),id);
        service.transfer(admin,id,scope,secondScope(),List.of(new Curriculum.Transfer(a.id(),b.id(),5)));
        var result=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertEquals(1,result.getAsJsonArray("flexibleTopics").size());
        assertEquals(targetTopic,result.getAsJsonArray("flexibleTopics").get(0).getAsJsonObject().get("id").getAsInt());
        assertEquals(1,result.getAsJsonArray("flexibleTasks").size());
        assertEquals(b.id(),result.getAsJsonArray("flexibleTasks").get(0).getAsJsonObject().get("id").getAsInt());
        assertTrue(result.getAsJsonArray("flexibleTasks").get(0).getAsJsonObject().get("completed").getAsBoolean());
        assertEquals(2,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=?",id));
    }
    @Test void catalogEnforcesStudentSessionAndNeverReturnsMissingContextsAsZero() throws Exception {
        for(String field:List.of("studentId","teacherId","classId","grade"))
            assertEquals(Status.BAD_REQUEST,request(Student.get(id),"/my-curriculum-catalog",body().replace("}",",\""+field+"\":1}")).getStatus());
        assertEquals(Status.UNAUTHORIZED,request(User.ANONYMOUS,"/my-curriculum-catalog",body()).getStatus());
        assertEquals(Status.FORBIDDEN,request(Teacher.get(id),"/my-curriculum-catalog",body()).getStatus());
        assertEquals(401,assertThrows(CurriculumException.class,()->service.studentCatalog(User.ANONYMOUS,id,id)).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->service.studentCatalog(Teacher.get(id),id,id)).status);
        var response=request(Student.get(id),"/my-curriculum-catalog",body().replace("\"subjectId\":"+id,"\"subjectId\":"+(id+1)));
        assertEquals(Status.CONFLICT,response.getStatus());assertTrue(responseBody(response).contains("context_unassigned"));
        assertFalse(responseBody(response).contains("totalTokens"));
    }
    @Test void catalogUsesConfiguredSemesterAndPreservesHistoricalGrade() throws Exception {
        var year=currentYear().setCurrentSemester(Semester.get(id));central(5);
        int own=service.createFlexibleTopic(teacher,scope,"Historical");service.create(teacher,scope,"Historical task",4,own);
        assertEquals(id,jsonResponse(Student.get(id),"/my-curriculum-catalog",progressBody(true,false)).get("semesterId").getAsInt());
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=6 WHERE id=?",id);return null;});
        assertEquals(9,jsonResponse(Student.get(id),"/my-curriculum-catalog",body()).getAsJsonObject("planned").get("totalTokens").getAsInt());
        service.assign(admin,id,new Curriculum.Scope(id,id,id,id+1));year.setCurrentSemester(Semester.get(id+1));
        assertEquals(id+1,jsonResponse(Student.get(id),"/my-curriculum-catalog",progressBody(true,false)).get("semesterId").getAsInt());
        assertEquals(9,jsonResponse(Student.get(id),"/my-curriculum-catalog",body()).getAsJsonObject("planned").get("totalTokens").getAsInt());
        assertEquals(Status.BAD_REQUEST,request(Student.get(id),"/my-curriculum-catalog",body().replace("\"semesterId\":"+id,"\"semesterId\":null")).getStatus());
    }
    @Test void topicOnlyContextPinsGradeAndCatalogRejectsOversubscribedLegacyPlan() throws Exception {
        var unassignedScope=new Curriculum.Scope(id,id,id+1,id);
        service.createFlexibleTopic(teacher,unassignedScope,"Before promotion");
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=6 WHERE id=?",id+1);return null;});
        assertEquals(409,assertThrows(CurriculumException.class,()->service.create(teacher,unassignedScope,"After promotion",1)).status);
        central(100);var task=service.create(teacher,scope,"Five",5);
        db.writeTransaction(c->{exec(c,"UPDATE flexible_tasks SET tokens=6 WHERE id=?",task.id());return null;});
        assertEquals("budget_exceeded",assertThrows(CurriculumException.class,()->service.studentCatalog(Student.get(id),id,id)).code);
    }
    @Test void topicHttpContractAndExplicitDetach() throws Exception {
        String topicBody=assignmentBody().replace("}",",\"name\":\"Practice\"}");
        int own=jsonResponse(Teacher.get(id),"/add-flexible-topic",topicBody).get("id").getAsInt();
        String taskBody=assignmentBody().replace("}",",\"name\":\"Exercise\",\"tokens\":5,\"topicId\":"+own+"}");
        int task=jsonResponse(Teacher.get(id),"/add-flexible-task",taskBody).get("id").getAsInt();
        assertEquals(own,scalar("SELECT flexible_topic FROM flexible_task_topics WHERE flexible_task=?",task));
        jsonResponse(Teacher.get(id),"/rename-flexible-topic","{\"topicId\":"+own+",\"name\":\"New name\"}");
        assertEquals("New name",jsonResponse(Teacher.get(id),"/flexible-curriculum-structure",assignmentBody()).getAsJsonArray("topics").get(0).getAsJsonObject().get("name").getAsString());
        assertEquals(Status.BAD_REQUEST,request(Teacher.get(id),"/add-flexible-task",taskBody.replace("\"topicId\":"+own,"\"topicId\":1.5")).getStatus());
        assertEquals(Status.FORBIDDEN,request(Student.get(id),"/add-flexible-topic",topicBody).getStatus());
        jsonResponse(Teacher.get(id),"/edit-flexible-task","{\"taskId\":"+task+",\"name\":\"Exercise\",\"tokens\":5,\"topicId\":null}");
        assertEquals(0,scalar("SELECT COUNT(*) FROM flexible_task_topics WHERE flexible_task=?",task));
    }
    de.igslandstuhl.database.server.webserver.responses.PostResponse request(User user,String path,String body) {
        var rq=new de.igslandstuhl.database.server.webserver.requests.APIPostRequest(
                new de.igslandstuhl.database.server.webserver.requests.HttpHeader("POST "+path+" HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: "+body.length()+"\r\n"),body,"127.0.0.1",true) {
            @Override public User getUser(){return user;}
        };
        return de.igslandstuhl.database.server.webserver.handlers.CurriculumRequestHandler.handle(rq);
    }

    @AfterEach void restoreEnrollmentFixtureGrade() throws Exception {
        db.writeTransaction(c->{exec(c,"UPDATE classes SET grade=5 WHERE id IN (?,?)",id,id+1);return null;});
    }
    CurriculumEnrollment enrollmentFixture() throws Exception {
        db.writeTransaction(c->{
            exec(c,"DELETE FROM student_curriculum_contexts WHERE student=?",id);
            exec(c,"UPDATE classes SET grade=13 WHERE id IN (?,?)",id,id+1);
            exec(c,"UPDATE topics SET grade=13 WHERE id=?",topic);
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Synthetic','Second',?,'unused',?,1)",id+1,"second"+id+"@example.invalid",id+1);
            for(int teacherId:new int[]{id,id+1})exec(c,"INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)",teacherId,id+1);
            return null;
        });
        return new CurriculumEnrollment(service);
    }
    List<CurriculumEnrollment.Teaching> teachingMappings() {
        return List.of(new CurriculumEnrollment.Teaching(id,id,id),new CurriculumEnrollment.Teaching(id+1,id,id+1));
    }
    @Test void gradeEnrollmentAssignsEveryClassAndPupilIdempotently() throws Exception {
        var enrollment=enrollmentFixture();var result=enrollment.assignGrade(admin,13,id,List.of(id),teachingMappings());
        assertEquals(2,result.get("students"));assertEquals(2,result.get("assignments"));
        assertEquals(List.of(id),enrollment.studentSubjects(id,id));assertEquals(List.of(id),enrollment.studentSubjects(id+1,id));
        enrollment.assignGrade(admin,13,id,List.of(id),teachingMappings());
        assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_enrolled_students WHERE semester=?",id));
        assertEquals(id+1,scalar("SELECT teacher FROM student_curriculum_contexts WHERE student=? AND semester=?",id+1,id));
    }
    @Test void gradeEnrollmentRejectsMissingMappingAndWorksWithoutLegacyTeacherLinks() throws Exception {
        var enrollment=enrollmentFixture();
        assertThrows(CurriculumException.class,()->enrollment.assignGrade(admin,13,id,List.of(id),List.of(teachingMappings().get(0))));
        db.writeTransaction(c->{exec(c,"DELETE FROM teacher_classes WHERE teacher_id=? AND class_id=?",id+1,id+1);return null;});
        enrollment.assignGrade(admin,13,id,List.of(id),teachingMappings());
        assertEquals(2,scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE semester=?",id));
        assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_class_teachers WHERE semester=?",id));
        assertEquals(1,scalar("SELECT COUNT(*) FROM curriculum_grade_subjects WHERE semester=?",id));
    }
    @Test void archivedClassMovesStudentsToSystemClassAndStopsCurrentContext() throws Exception {
        SchoolClass.get(id).delete();
        assertEquals(1,scalar("SELECT COUNT(*) FROM students WHERE id=?",id));
        assertEquals(SchoolClass.UNASSIGNED_CLASS_ID,scalar("SELECT class FROM students WHERE id=?",id));
        assertNotNull(SchoolClass.get(id));
        assertEquals(0,scalar("SELECT active FROM classes WHERE id=?",id));
        assertFalse(SchoolClass.getAll().stream().anyMatch(c -> c.getId() == id));
        var error=assertThrows(CurriculumException.class,()->service.studentProgress(Student.get(id),id,id));
        assertEquals("context_unassigned",error.code);
        assertThrows(IllegalStateException.class,()->SchoolClass.get(SchoolClass.UNASSIGNED_CLASS_ID).delete());
    }
    @Test void gradeEnrollmentExcludesWpfAndTeacherCannotAssign() throws Exception {
        var enrollment=enrollmentFixture();enrollment.subjectType(admin,id,true);
        assertThrows(CurriculumException.class,()->enrollment.assignGrade(admin,13,id,List.of(id),teachingMappings()));
        assertThrows(CurriculumException.class,()->enrollment.subjectType(teacher,id,false));
        assertThrows(CurriculumException.class,()->enrollment.assignGrade(teacher,13,id,List.of(id),teachingMappings()));
        assertEquals(0,scalar("SELECT COUNT(*) FROM curriculum_enrolled_students WHERE semester=?",id));
    }
    @Test void wpfAssignmentIsIndividualAndRejectsStaleDragAndForeignClass() throws Exception {
        var enrollment=enrollmentFixture();enrollment.subjectType(admin,id+1,true);
        var wpf=new Curriculum.Scope(id,id+1,id,id);
        enrollment.assignWpf(admin,id,wpf,null);
        assertEquals(id+1,scalar("SELECT subject FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group='WPF'",id,id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM curriculum_individual_assignments WHERE student=?",id+1));
        assertThrows(CurriculumException.class,()->enrollment.assignWpf(admin,id,wpf,null));
        assertThrows(CurriculumException.class,()->enrollment.assignWpf(admin,id+1,wpf,null));
        assertThrows(CurriculumException.class,()->enrollment.assignWpf(teacher,id,wpf,id+1));
        enrollment.assignWpf(admin,id,wpf,id+1);
        assertEquals(1,scalar("SELECT COUNT(*) FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group='WPF'",id,id));
        assertEquals(1,enrollment.wpfRoster(admin,id,id).size());
    }
    @Test void wpfSwapKeepsOneChoiceAndPreservesOldContextWithoutAwardLoss() throws Exception {
        var enrollment=enrollmentFixture();enrollment.subjectType(admin,id,true);enrollment.subjectType(admin,id+1,true);
        enrollment.assignWpf(admin,id,scope,null);
        enrollment.assignWpf(admin,id,new Curriculum.Scope(id,id+1,id,id),id);
        assertEquals(List.of(id+1),enrollment.selectedWpf(id,id));
        assertThrows(CurriculumException.class,()->service.studentCatalog(Student.get(id),id,id));
        enrollment.assignWpf(admin,id,scope,id+1);
        int task=central(5);Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        assertThrows(CurriculumException.class,()->enrollment.assignWpf(admin,id,new Curriculum.Scope(id,id+1,id,id),id));
        assertEquals(List.of(id),enrollment.selectedWpf(id,id));
        assertEquals(5L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
    }
    @Test void individualGroupsAllowWpfAndReligionEthikInParallel() throws Exception {
        var enrollment=enrollmentFixture();
        db.writeTransaction(c->{
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id+2,"Evangelische Religion "+id);
            return null;
        });
        enrollment.subjectType(admin,id+1,"INDIVIDUAL","WPF");
        enrollment.subjectType(admin,id+2,"INDIVIDUAL","RELIGION_ETHIK");
        db.writeTransaction(c->{
            exec(c,"DELETE FROM teacher_classes WHERE teacher_id=?",id);
            exec(c,"DELETE FROM teacher_subjects WHERE teacher_id=?",id);
            return null;
        });
        enrollment.assignIndividual(admin,id,new Curriculum.Scope(id,id+1,id,id),"WPF",null);
        enrollment.assignIndividual(admin,id,new Curriculum.Scope(id,id+2,id,id),"RELIGION_ETHIK",null);
        assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_individual_assignments WHERE student=? AND semester=?",id,id));
        assertEquals(Set.of(id+1,id+2),Set.copyOf(enrollment.studentSubjects(id,id)));
        assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_grade_teachers WHERE semester=? AND grade=13",id));
    }
    @Test void individualAssignmentAllowsOnlyOneSubjectPerGroup() throws Exception {
        var enrollment=enrollmentFixture();
        db.writeTransaction(c->{
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id+2,"WPF Kunst "+id);
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id+3,"Ethik "+id);
            return null;
        });
        enrollment.subjectType(admin,id+1,"INDIVIDUAL","WPF");
        enrollment.subjectType(admin,id+2,"INDIVIDUAL","WPF");
        enrollment.subjectType(admin,id+3,"INDIVIDUAL","RELIGION_ETHIK");
        enrollment.assignIndividual(admin,id,new Curriculum.Scope(id,id+1,id,id),"WPF",null);
        assertEquals(409,assertThrows(CurriculumException.class,()->enrollment.assignIndividual(admin,id,new Curriculum.Scope(id,id+2,id,id),"WPF",null)).status);
        enrollment.assignIndividual(admin,id,new Curriculum.Scope(id,id+2,id,id),"WPF",id+1);
        assertEquals(id+2,scalar("SELECT subject FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group='WPF'",id,id));
        enrollment.assignIndividual(admin,id,new Curriculum.Scope(id,id+3,id,id),"RELIGION_ETHIK",null);
        assertEquals(2,scalar("SELECT COUNT(*) FROM curriculum_individual_assignments WHERE student=? AND semester=?",id,id));
    }
    @Test void removingManagedGradeSubjectWithoutWorkRemovesCurrentContexts() throws Exception {
        var enrollment=enrollmentFixture();
        enrollment.assignGrade(admin,13,id,List.of(id),teachingMappings());
        assertEquals(2,scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE semester=? AND subject=?",id,id));
        enrollment.removeGradeSubject(admin,13,id,id);
        assertEquals(0,scalar("SELECT COUNT(*) FROM curriculum_grade_subjects WHERE semester=? AND subject=?",id,id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM curriculum_class_teachers WHERE semester=? AND subject=?",id,id));
        assertEquals(0,scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE semester=? AND subject=?",id,id));
        assertEquals(List.of(),enrollment.studentSubjects(id,id));
    }
    @Test void removingManagedGradeSubjectWithWorkIsBlocked() throws Exception {
        var enrollment=enrollmentFixture();
        enrollment.assignGrade(admin,13,id,List.of(id),teachingMappings());
        int task=central(5);
        Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        var error=assertThrows(CurriculumException.class,()->enrollment.removeGradeSubject(admin,13,id,id));
        assertEquals(409,error.status);
        assertEquals(1,scalar("SELECT COUNT(*) FROM curriculum_grade_subjects WHERE semester=? AND subject=?",id,id));
        assertEquals(2,scalar("SELECT COUNT(*) FROM student_curriculum_contexts WHERE semester=? AND subject=?",id,id));
    }
    @Test void globalSubjectDeletionBlocksHistoricalReferences() throws Exception {
        assertThrows(SQLException.class,()->Subject.get(id).delete());
        db.writeTransaction(c->{exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",id+4,"Disposable "+id);return null;});
        assertDoesNotThrow(()->Subject.get(id+4).delete());
        assertNull(Subject.get(id+4));
    }
    @Test void publicationDefaultsClosedAndSupportsWholeTopicAndTaskOverrides() throws Exception {
        var enrollment=new CurriculumEnrollment(service);int a=central(5),b=service.createCentralTask(topic,"Second",TaskLevel.LEVEL1,6);
        assertEquals(0,((List<?>)service.studentCatalog(Student.get(id),id,id).get("centralTasks")).size());
        assertFalse(enrollment.canAccessTask(id,a,false));
        enrollment.release(teacher,scope,topic,null,true);
        assertTrue(enrollment.canAccessTask(id,a,false));assertTrue(enrollment.canAccessTask(id,b,false));
        enrollment.release(teacher,scope,null,a,false);
        assertFalse(enrollment.canAccessTask(id,a,false));assertTrue(enrollment.canAccessTask(id,b,false));
        assertEquals(1,((List<?>)service.studentCatalog(Student.get(id),id,id).get("centralTasks")).size());
        int later=service.createCentralTask(topic,"Later",TaskLevel.LEVEL1,3);assertTrue(enrollment.canAccessTask(id,later,false));
        enrollment.release(teacher,scope,topic,null,false);
        assertFalse(enrollment.canAccessTask(id,b,false));
        enrollment.release(teacher,scope,null,a,true);
        assertEquals(1,((List<?>)service.studentCatalog(Student.get(id),id,id).get("centralTopics")).size());
    }
    @SuppressWarnings("unchecked")
    List<Map<String,Object>> rows(Map<String,Object> result,String key) { return (List<Map<String,Object>>)result.get(key); }
    Map<String,Object> byId(List<Map<String,Object>> rows,int rowId) {
        return rows.stream().filter(r->((Number)r.get("id")).intValue()==rowId).findFirst().orElseThrow();
    }
    com.google.gson.JsonObject byId(com.google.gson.JsonArray rows,int rowId) {
        for(var entry:rows) {
            var row=entry.getAsJsonObject();
            if(row.get("id").getAsInt()==rowId)return row;
        }
        throw new AssertionError("Missing row "+rowId);
    }
    long countInProgress(com.google.gson.JsonArray rows) {
        long count=0;
        for(var entry:rows) if(entry.getAsJsonObject().get("inProgress").getAsBoolean()) count++;
        return count;
    }
    @Test void flexiblePublicationDefaultsClosedInheritsTopicsAndClearsOverrides() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int topicId=service.createFlexibleTopic(teacher,scope,"Practice");
        var first=service.create(teacher,scope,"First",5,topicId);
        var second=service.create(teacher,scope,"Second",6,topicId);
        var loose=service.create(teacher,scope,"Loose",7);
        var releases=enrollment.releases(teacher,scope);
        assertFalse((Boolean)byId(rows(releases,"flexibleTopics"),topicId).get("active"));
        assertFalse((Boolean)byId(rows(releases,"flexibleTasks"),first.id()).get("active"));
        assertFalse((Boolean)byId(rows(releases,"flexibleTasks"),loose.id()).get("active"));
        enrollment.release(teacher,scope,null,null,topicId,null,true);
        releases=enrollment.releases(teacher,scope);
        assertTrue((Boolean)byId(rows(releases,"flexibleTasks"),first.id()).get("active"));
        assertTrue((Boolean)byId(rows(releases,"flexibleTasks"),second.id()).get("active"));
        enrollment.release(teacher,scope,null,null,null,first.id(),false);
        releases=enrollment.releases(teacher,scope);
        assertFalse((Boolean)byId(rows(releases,"flexibleTasks"),first.id()).get("active"));
        assertTrue((Boolean)byId(rows(releases,"flexibleTasks"),second.id()).get("active"));
        enrollment.release(teacher,scope,null,null,topicId,null,true);
        releases=enrollment.releases(teacher,scope);
        assertTrue((Boolean)byId(rows(releases,"flexibleTasks"),first.id()).get("active"));
        assertEquals(0,scalar("SELECT COUNT(*) FROM flexible_task_releases WHERE flexible_task=?",first.id()));
        enrollment.release(teacher,scope,null,null,topicId,null,false);
        releases=enrollment.releases(teacher,scope);
        assertFalse((Boolean)byId(rows(releases,"flexibleTasks"),first.id()).get("active"));
        assertFalse((Boolean)byId(rows(releases,"flexibleTasks"),second.id()).get("active"));
        enrollment.release(teacher,scope,null,null,null,loose.id(),true);
        assertTrue((Boolean)byId(rows(enrollment.releases(teacher,scope),"flexibleTasks"),loose.id()).get("active"));
    }
    @Test void centralTaskLockStopsOnlyMatchingActiveStageAndResetsInProgress() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int active=centralNamed("Active",5),otherTask=centralNamed("Other",6);
        enrollment.release(teacher,scope,topic,null,true);
        service.activateCentralStage(id,active);

        enrollment.release(teacher,scope,null,active,false);
        assertNull(service.activeStage(id,id));
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
        assertNotEquals(Task.STATUS_LOCKED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));

        enrollment.release(teacher,scope,null,active,true);
        service.activateCentralStage(id,active);
        enrollment.release(teacher,scope,null,otherTask,false);
        assertEquals(active,service.activeStage(id,id).taskId());
        assertEquals(Task.STATUS_IN_PROGRESS,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
    }
    @Test void centralTopicLockStopsOnlyActiveStagesInThatTopic() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int topicA=topic, activeA=centralNamed("Topic A active",5);
        int topicB=id+2;
        db.writeTransaction(c->{exec(c,"INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,5,2,?)",topicB,"Topic B-"+id,id,id);return null;});
        int activeB=service.createCentralTask(topicB,"Topic B active",TaskLevel.LEVEL1,6);
        enrollment.release(teacher,scope,topicA,null,true);
        enrollment.release(teacher,scope,topicB,null,true);

        service.activateCentralStage(id,activeA);
        enrollment.release(teacher,scope,topicA,null,false);
        assertNull(service.activeStage(id,id));
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,activeA));

        service.activateCentralStage(id,activeB);
        enrollment.release(teacher,scope,topicA,null,false);
        assertEquals(activeB,service.activeStage(id,id).taskId());
        assertEquals(Task.STATUS_IN_PROGRESS,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,activeB));
    }
    @Test void centralReleaseLocksUseExactCurriculumContextScope() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int active=centralNamed("Shared central",5);
        enrollment.release(teacher,scope,topic,null,true);
        service.activateCentralStage(id,active);

        enrollment.release(teacher,new Curriculum.Scope(id,id,id+1,id),null,active,false);
        assertEquals(active,service.activeStage(id,id).taskId());
        enrollment.release(other,new Curriculum.Scope(id+1,id,id,id),null,active,false);
        assertEquals(active,service.activeStage(id,id).taskId());

        int futureTopic=id+2;
        db.writeTransaction(c->{exec(c,"INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,5,1,?)",futureTopic,"Future-"+id,id,id+1);return null;});
        int futureTask=service.createCentralTask(futureTopic,"Future central",TaskLevel.LEVEL1,7);
        enrollment.release(teacher,new Curriculum.Scope(id,id,id,id+1),null,futureTask,false);
        assertEquals(active,service.activeStage(id,id).taskId());
        assertEquals(Task.STATUS_IN_PROGRESS,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
    }
    @Test void centralGroupLockPreservesCompletionsAndCacheSets() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int active=centralNamed("Active cache",5),done=centralNamed("Done cache",6),locked=centralNamed("Locked cache",7);
        enrollment.release(teacher,scope,topic,null,true);
        Student student=Student.get(id);
        student.changeTaskStatus(Task.get(done),Task.STATUS_COMPLETED);
        student.changeTaskStatus(Task.get(locked),Task.STATUS_LOCKED);
        service.activateCentralStage(id,active);

        enrollment.release(teacher,scope,null,active,false);
        assertNull(service.activeStage(id,id));
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
        assertEquals(Task.STATUS_COMPLETED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,done));
        assertEquals(Task.STATUS_LOCKED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,locked));
        assertFalse(student.getSelectedTasks().contains(Task.get(active)));
        assertTrue(student.getCompletedTasks().contains(Task.get(done)));
        assertTrue(student.getLockedTasks().contains(Task.get(locked)));
    }
    @Test void centralLockAndReReleaseDoesNotRestartStudentActivity() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int active=centralNamed("Restart central",5);
        enrollment.release(teacher,scope,topic,null,true);
        service.activateCentralStage(id,active);
        enrollment.release(teacher,scope,null,active,false);
        enrollment.release(teacher,scope,null,active,true);
        assertNull(service.activeStage(id,id));

        service.activateCentralStage(id,active);
        enrollment.release(teacher,scope,topic,null,false);
        enrollment.release(teacher,scope,topic,null,true);
        assertNull(service.activeStage(id,id));
    }
    @Test void centralGroupLockLeavesOtherSubjectsActiveAndStopsAllMatchingStudents() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int active=centralNamed("Multi student",5),otherSubject=otherSubjectCentral("Other subject active",6),secondStudent=id+3;
        db.writeTransaction(c->{exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Second','Student',?,'unused',?,1)",secondStudent,"second"+id+"@example.invalid",id);return null;});
        service.assign(admin,secondStudent,scope);
        enrollment.release(teacher,scope,topic,null,true);
        service.activateCentralStage(id,active);
        service.activateCentralStage(id,otherSubject);
        service.activateCentralStage(secondStudent,active);

        enrollment.release(teacher,scope,null,active,false);
        assertNull(service.activeStage(id,id));
        assertNull(service.activeStage(secondStudent,id));
        assertEquals(otherSubject,service.activeStage(id,id+1).taskId());
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,active));
        assertEquals(Task.STATUS_NOT_STARTED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",secondStudent,active));
    }
    @Test void flexibleTaskAndTopicLocksStopOnlyMatchingActiveFlexibleStages() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int topicA=service.createFlexibleTopic(teacher,scope,"Flexible A"),topicB=service.createFlexibleTopic(teacher,scope,"Flexible B");
        var taskA=service.create(teacher,scope,"Task A",5,topicA);
        var taskB=service.create(teacher,scope,"Task B",6,topicB);
        var loose=service.create(teacher,scope,"Loose",7);
        enrollment.release(teacher,scope,null,null,topicA,null,true);
        enrollment.release(teacher,scope,null,null,topicB,null,true);
        enrollment.release(teacher,scope,null,null,null,loose.id(),true);

        service.activateFlexibleStage(id,taskA.id());
        enrollment.release(teacher,scope,null,null,null,taskA.id(),false);
        assertNull(service.activeStage(id,id));

        service.activateFlexibleStage(id,taskB.id());
        enrollment.release(teacher,scope,null,null,topicA,null,false);
        assertEquals(taskB.id(),service.activeStage(id,id).taskId());

        enrollment.release(teacher,scope,null,null,topicA,null,true);
        service.activateFlexibleStage(id,taskA.id());
        enrollment.release(teacher,scope,null,null,topicA,null,false);
        assertNull(service.activeStage(id,id));

        service.activateFlexibleStage(id,loose.id());
        enrollment.release(teacher,scope,null,null,topicA,null,false);
        assertEquals(loose.id(),service.activeStage(id,id).taskId());
    }
    @Test void flexibleLocksPreserveCompletionsAndReReleaseDoesNotRestart() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int topicId=service.createFlexibleTopic(teacher,scope,"Flexible completion");
        var active=service.create(teacher,scope,"Flexible active",5,topicId);
        var completed=service.create(teacher,scope,"Flexible done",6,topicId);
        enrollment.release(teacher,scope,null,null,topicId,null,true);
        service.complete(teacher,completed.id(),id);
        service.activateFlexibleStage(id,active.id());

        enrollment.release(teacher,scope,null,null,null,active.id(),false);
        assertNull(service.activeStage(id,id));
        assertEquals(1,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,completed.id()));
        enrollment.release(teacher,scope,null,null,null,active.id(),true);
        assertNull(service.activeStage(id,id));

        service.activateFlexibleStage(id,active.id());
        enrollment.release(teacher,scope,null,null,topicId,null,false);
        enrollment.release(teacher,scope,null,null,topicId,null,true);
        assertNull(service.activeStage(id,id));
        assertEquals(1,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,completed.id()));
    }
    @Test void flexiblePublicationRejectsForeignActorScopeAndInvalidIdentityMix() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int topicId=service.createFlexibleTopic(teacher,scope,"Practice");
        var task=service.create(teacher,scope,"First",5,topicId);
        assertEquals(403,assertThrows(CurriculumException.class,()->enrollment.release(other,scope,null,null,topicId,null,true)).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->enrollment.release(teacher,new Curriculum.Scope(id,id,id+1,id),null,null,topicId,null,true)).status);
        assertEquals(403,assertThrows(CurriculumException.class,()->enrollment.release(teacher,new Curriculum.Scope(id,id,id+1,id),null,null,null,task.id(),true)).status);
        assertEquals(400,assertThrows(CurriculumException.class,()->enrollment.release(teacher,scope,topic,null,topicId,null,true)).status);
        assertEquals(400,assertThrows(CurriculumException.class,()->enrollment.release(teacher,scope,null,null,null,null,true)).status);
    }
    @Test void releasesResponseIncludesCentralAndFlexibleEntries() throws Exception {
        var enrollment=new CurriculumEnrollment(service);central(5);
        int flexibleTopic=service.createFlexibleTopic(teacher,scope,"Practice");
        var flexibleTask=service.create(teacher,scope,"Exercise",6,flexibleTopic);
        var releases=enrollment.releases(teacher,scope);
        assertFalse(rows(releases,"topics").isEmpty());assertFalse(rows(releases,"tasks").isEmpty());
        assertEquals(flexibleTopic,((Number)rows(releases,"flexibleTopics").get(0).get("id")).intValue());
        assertEquals(flexibleTask.id(),((Number)rows(releases,"flexibleTasks").get(0).get("id")).intValue());
        assertEquals(flexibleTopic,((Number)rows(releases,"flexibleTasks").get(0).get("topicId")).intValue());
    }
    @Test void flexiblePublicationHttpExtendsExistingReleaseRoute() throws Exception {
        int flexibleTopic=service.createFlexibleTopic(teacher,scope,"Practice");
        String payload=assignmentBody().replace("}",",\"flexibleTopicId\":"+flexibleTopic+",\"active\":true}");
        assertEquals(Status.OK,request(Teacher.get(id),"/set-curriculum-release",payload).getStatus());
        assertEquals(1,scalar("SELECT active FROM flexible_topic_releases WHERE flexible_topic=?",flexibleTopic));
        assertEquals(Status.BAD_REQUEST,request(Teacher.get(id),"/set-curriculum-release",payload.replace("}",",\"topicId\":"+topic+"}")).getStatus());
    }
    @Test void studentCatalogFiltersFlexibleReleasesButKeepsCompletedAndPlanning() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int hiddenTopic=service.createFlexibleTopic(teacher,scope,"Hidden topic");
        service.create(teacher,scope,"Hidden task",4,hiddenTopic);
        int visibleTopic=service.createFlexibleTopic(teacher,scope,"Visible topic");
        var visible=service.create(teacher,scope,"Visible task",5,visibleTopic);
        var completed=service.create(teacher,scope,"Completed task",6);
        service.complete(teacher,completed.id(),id);
        long planned=(Long)((Map<?,?>)service.studentCatalog(Student.get(id),id,id).get("planned")).get("flexibleTokens");
        assertEquals(15L,planned);
        assertTrue(rows(service.studentCatalog(Student.get(id),id,id),"flexibleTasks").stream().noneMatch(r->((Number)r.get("id")).intValue()==visible.id()));
        enrollment.release(teacher,scope,null,null,null,visible.id(),true);
        var catalog=service.studentCatalog(Student.get(id),id,id);
        assertEquals(List.of(visible.id(),completed.id()),rows(catalog,"flexibleTasks").stream().map(r->((Number)r.get("id")).intValue()).sorted().toList());
        assertEquals(List.of(visibleTopic),rows(catalog,"flexibleTopics").stream().map(r->((Number)r.get("id")).intValue()).toList());
        enrollment.release(teacher,scope,null,null,null,completed.id(),false);
        assertTrue(rows(service.studentCatalog(Student.get(id),id,id),"flexibleTasks").stream().anyMatch(r->((Number)r.get("id")).intValue()==completed.id()));
        assertEquals(15L,((Map<?,?>)service.studentCatalog(Student.get(id),id,id).get("planned")).get("flexibleTokens"));
    }
    @Test void studentFlexibleActiveStageHttpUsesOnlySessionStudentAndRelease() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        var hidden=service.create(teacher,scope,"Hidden",5);
        assertEquals(Status.FORBIDDEN,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+hidden.id()+"}").getStatus());
        enrollment.release(teacher,scope,null,null,null,hidden.id(),true);
        assertEquals(Status.BAD_REQUEST,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+hidden.id()+",\"studentId\":"+(id+1)+"}").getStatus());
        for(String field:List.of("teacherId","classId","grade","subjectId","semesterId"))
            assertEquals(Status.BAD_REQUEST,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+hidden.id()+",\""+field+"\":1}").getStatus());
        assertEquals(Status.FORBIDDEN,request(Teacher.get(id),"/begin-flexible-task","{\"taskId\":"+hidden.id()+"}").getStatus());
        assertEquals(Status.FORBIDDEN,request(Admin.create("student-flex-admin-"+id,"synthetic-test-only"),"/begin-flexible-task","{\"taskId\":"+hidden.id()+"}").getStatus());

        assertEquals(Status.OK,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+hidden.id()+"}").getStatus());
        var catalog=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertEquals("FLEXIBLE",catalog.getAsJsonObject("activeStage").get("type").getAsString());
        assertEquals(hidden.id(),catalog.getAsJsonObject("activeStage").get("taskId").getAsInt());
        assertEquals(1,countInProgress(catalog.getAsJsonArray("flexibleTasks")));
        assertEquals(0,countInProgress(catalog.getAsJsonArray("centralTasks")));
        var entry=byId(catalog.getAsJsonArray("flexibleTasks"),hidden.id());
        assertTrue(entry.get("active").getAsBoolean());assertFalse(entry.get("completed").getAsBoolean());assertTrue(entry.get("inProgress").getAsBoolean());
    }
    @Test void studentFlexibleCancelIsIdempotentAndPreservesCompletionsAndOtherActiveStages() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        var first=service.create(teacher,scope,"First active",5);
        var second=service.create(teacher,scope,"Second inactive",6);
        enrollment.release(teacher,scope,null,null,null,first.id(),true);
        enrollment.release(teacher,scope,null,null,null,second.id(),true);
        service.complete(teacher,first.id(),id);
        assertEquals(Status.OK,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+second.id()+"}").getStatus());
        assertEquals(Status.OK,request(Student.get(id),"/cancel-flexible-task","{\"taskId\":"+first.id()+"}").getStatus());
        assertEquals(second.id(),service.activeStage(id,id).taskId());
        assertEquals(1,scalar("SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,first.id()));
        assertEquals(Status.OK,request(Student.get(id),"/cancel-flexible-task","{\"taskId\":"+second.id()+"}").getStatus());
        assertNull(service.activeStage(id,id));
        var catalog=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertTrue(catalog.get("activeStage").isJsonNull());
        assertEquals(0,countInProgress(catalog.getAsJsonArray("flexibleTasks")));
        assertEquals(Status.OK,request(Student.get(id),"/cancel-flexible-task","{\"taskId\":"+second.id()+"}").getStatus());
        assertNull(service.activeStage(id,id));
    }
    @Test void studentCatalogReportsCentralActiveStageAndNeverMarksCompletedInProgress() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        int active=centralNamed("Current central",5),done=centralNamed("Completed central",6);
        enrollment.release(teacher,scope,topic,null,true);
        service.activateCentralStage(id,active);
        Student.get(id).changeTaskStatus(Task.get(done),Task.STATUS_COMPLETED);
        db.writeTransaction(c->{exec(c,"INSERT INTO student_active_curriculum_stages(student,subject,semester,central_task,flexible_task) VALUES(?,?,?,?,NULL) ON CONFLICT(student,subject) DO UPDATE SET central_task=excluded.central_task,flexible_task=NULL,semester=excluded.semester",id,id,id,done);return null;});
        var inconsistent=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertEquals("CENTRAL",inconsistent.getAsJsonObject("activeStage").get("type").getAsString());
        assertEquals(done,inconsistent.getAsJsonObject("activeStage").get("taskId").getAsInt());
        assertEquals(0,countInProgress(inconsistent.getAsJsonArray("centralTasks")));
        service.activateCentralStage(id,active);
        var catalog=jsonResponse(Student.get(id),"/my-curriculum-catalog",body());
        assertEquals("CENTRAL",catalog.getAsJsonObject("activeStage").get("type").getAsString());
        assertEquals(active,catalog.getAsJsonObject("activeStage").get("taskId").getAsInt());
        assertEquals(1,countInProgress(catalog.getAsJsonArray("centralTasks")));
        assertEquals(0,countInProgress(catalog.getAsJsonArray("flexibleTasks")));
        var activeEntry=byId(catalog.getAsJsonArray("centralTasks"),active);
        assertTrue(activeEntry.get("active").getAsBoolean());assertFalse(activeEntry.get("completed").getAsBoolean());assertTrue(activeEntry.get("inProgress").getAsBoolean());
    }
    @Test void studentFlexibleActiveStagesKeepSubjectsIndependentAndBlockForeignContext() throws Exception {
        var enrollment=new CurriculumEnrollment(service);
        var math=service.create(teacher,scope,"Math flexible",5);
        enrollment.release(teacher,scope,null,null,null,math.id(),true);
        int germanCentral=otherSubjectCentral("German central",7);
        service.activateCentralStage(id,germanCentral);
        assertEquals(Status.OK,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+math.id()+"}").getStatus());
        assertEquals(math.id(),service.activeStage(id,id).taskId());
        assertEquals(germanCentral,service.activeStage(id,id+1).taskId());
        assertEquals("FLEXIBLE",jsonResponse(Student.get(id),"/my-curriculum-catalog",body()).getAsJsonObject("activeStage").get("type").getAsString());
        assertEquals("CENTRAL",jsonResponse(Student.get(id),"/my-curriculum-catalog","{\"subjectId\":"+(id+1)+",\"semesterId\":"+id+"}").getAsJsonObject("activeStage").get("type").getAsString());

        var foreign=service.create(other,secondScope(),"Foreign",5);
        new CurriculumEnrollment(service).release(other,secondScope(),null,null,null,foreign.id(),true);
        assertEquals(Status.FORBIDDEN,request(Student.get(id),"/begin-flexible-task","{\"taskId\":"+foreign.id()+"}").getStatus());
    }
    @Test void publicationCannotBeChangedByOtherTeacherAndCompletedCoinsRemain() throws Exception {
        var enrollment=new CurriculumEnrollment(service);int task=central(7);
        assertThrows(CurriculumException.class,()->enrollment.release(other,scope,topic,null,true));
        enrollment.release(teacher,scope,topic,null,true);Student.get(id).changeTaskStatus(Task.get(task),Task.STATUS_COMPLETED);
        enrollment.release(teacher,scope,topic,null,false);
        assertFalse(enrollment.canAccessTask(id,task,false));assertTrue(enrollment.canAccessTask(id,task,true));
        assertEquals(7L,service.studentProgress(Student.get(id),id,id).get("totalTokens"));
        assertEquals(1,((List<?>)service.studentCatalog(Student.get(id),id,id).get("centralTasks")).size());
    }
    @Test void enrollmentAndPublicationRoutesRepeatRoleAndInputChecks() throws Exception {
        for(String path:List.of("/curriculum-enrollment-catalog","/set-curriculum-subject-type","/assign-grade-curriculum","/curriculum-wpf-roster","/assign-curriculum-wpf")) {
            assertNotEquals(Status.OK,request(Teacher.get(id),path,body()).getStatus());
            assertEquals(Status.FORBIDDEN,request(Student.get(id),path,body()).getStatus());
        }
        assertEquals(Status.FORBIDDEN,request(Student.get(id),"/set-curriculum-release",body()).getStatus());
        assertEquals(Status.BAD_REQUEST,request(Teacher.get(id),"/set-curriculum-release",body()).getStatus());
    }
}
