package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.*;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.sql.*;
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
    }
    static void exec(Connection c,String sql,Object...args)throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)){for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);s.executeUpdate();}
    }
    long scalar(String sql,Object...args)throws SQLException {
        try(PreparedStatement s=db.getSQLConnection().prepareStatement(sql)) {for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);try(ResultSet r=s.executeQuery()){assertTrue(r.next());return r.getLong(1);}}
    }
    int central(int tokens)throws SQLException{return service.createCentralTask(topic,"Central",TaskLevel.LEVEL1,tokens);}
    @Test void topicRenameKeepsIdAndAllCachedReferences()throws Exception {
        Topic before=Topic.get(topic);Subject subject=before.getSubject();subject.getTopics(5);
        service.renameTopic(admin,topic,"Renamed");
        assertSame(before,Topic.get(topic));assertEquals(topic,before.getId());assertEquals("Renamed",before.getName());
        assertEquals("Renamed",subject.getTopics(5).stream().filter(t->t.getId()==topic).findFirst().orElseThrow().getName());
    }
    @Test void centralEditPreservesCompletionAndRetroactiveValue()throws Exception {
        int taskId=central(6);Task task=Task.get(taskId);Topic.get(topic).getTasks();
        Student student=Student.get(id);student.changeTaskStatus(task,Task.STATUS_COMPLETED);
        long rowid=scalar("SELECT rowid FROM taskstats WHERE student=? AND task=?",id,taskId);
        assertEquals(6,student.getCurrentProgress(Subject.get(id)));
        service.editTask(admin,taskId,"Changed",4);
        assertSame(task,Task.get(taskId));assertEquals("Changed",task.getName());assertEquals(4,task.getTokens());
        assertEquals(4,student.getCurrentProgress(Subject.get(id)));
        assertEquals(rowid,scalar("SELECT rowid FROM taskstats WHERE student=? AND task=?",id,taskId));
        assertEquals(Task.STATUS_COMPLETED,scalar("SELECT status FROM taskstats WHERE student=? AND task=?",id,taskId));
        assertTrue(student.getCompletedTasks().contains(task)); // HashSet membership must survive rename.
        student.changeTaskStatus(task,Task.STATUS_IN_PROGRESS);assertFalse(student.getCompletedTasks().contains(task));
    }
    @Test void flexibleCompletionUsesCurrentDefinitionWithoutSnapshot()throws Exception {
        var task=service.create(teacher,scope,"Flexible",6);service.complete(teacher,task.id(),id);
        long rowid=scalar("SELECT rowid FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",id,task.id());
        assertEquals(6,service.completedTokens(id,scope));
        var updated=service.edit(teacher,task.id(),"Renamed",4);
        assertEquals(task.id(),updated.id());assertEquals(4,service.completedTokens(id,scope));
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
        service.complete(other, another.id(), id);
        assertEquals(12L, service.progress(teacher,id,scope).get("totalTokens"));
        service.editTask(admin,central,"Central revised",4);
        service.edit(teacher,own.id(),"Own revised",4);
        assertEquals(8L, service.progress(teacher,id,scope).get("totalTokens"));
        assertEquals(24L, service.progress(other,id,anotherScope).get("totalTokens"));
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
    private de.igslandstuhl.database.server.webserver.responses.PostResponse request(User user,String path,String body) {
        var rq=new de.igslandstuhl.database.server.webserver.requests.APIPostRequest(
                new de.igslandstuhl.database.server.webserver.requests.HttpHeader("POST "+path+" HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: "+body.length()+"\r\n"),body,"127.0.0.1",true) {
            @Override public User getUser(){return user;}
        };
        return de.igslandstuhl.database.server.webserver.handlers.CurriculumRequestHandler.handle(rq);
    }
}
