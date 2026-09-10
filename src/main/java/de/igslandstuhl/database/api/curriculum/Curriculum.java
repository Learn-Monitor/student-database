package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.*;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import java.sql.*;
import java.util.*;

/** Transactional curriculum operations. Budgets are read from SQL, never from caches. */
public final class Curriculum {
    public static final int REGULAR_LIMIT = 100, HARD_LIMIT = 105;
    private final SQLiteConnection database;
    public Curriculum(SQLiteConnection database) { this.database = database; }
    public static Curriculum current() { return new Curriculum(Server.getInstance().getConnection()); }

    public record Actor(boolean admin, int teacherId) {
        public static Actor from(User user) {
            if (user == null || user == User.ANONYMOUS) throw error(401, "unauthorized", "Please sign in.");
            if (user.isAdmin()) return new Actor(true, 0);
            if (user.isTeacher()) return new Actor(false, user.asTeacher().getId());
            throw error(403, "forbidden", "Teacher or administrator required.");
        }
    }
    public record Scope(int teacherId, int subjectId, int classId, int semesterId) {}
    public record Budget(int teacherId, int subjectId, int classId, int semesterId, int grade,
                         long centralTokens, long flexibleTokens, long totalTokens,
                         int regularLimit, int hardLimit, long remainingRegular, long remainingHard) {
        static Budget of(Scope s, int grade, long central, long flexible) {
            long total = central + flexible;
            return new Budget(s.teacherId(), s.subjectId(), s.classId(), s.semesterId(), grade,
                    central, flexible, total, REGULAR_LIMIT, HARD_LIMIT, REGULAR_LIMIT-total, HARD_LIMIT-total);
        }
    }
    public record FlexibleTask(int id, int ownerTeacher, int subjectId, int classId, int semesterId,
                               int grade, String name, int tokens) {
        Scope scope() { return new Scope(ownerTeacher, subjectId, classId, semesterId); }
    }
    private static CurriculumException error(int status, String code, String message) {
        return new CurriculumException(status, code, message);
    }
    public static String validName(String value) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > 200
                || value.chars().anyMatch(Character::isISOControl))
            throw error(400, "invalid_input", "Name must contain 1–200 printable characters.");
        return value.strip();
    }
    private static void tokens(int value) {
        if (value < 0) throw error(400, "invalid_input", "Tokens cannot be negative.");
    }
    private static void admin(Actor actor) {
        if (!actor.admin()) throw error(403, "forbidden", "Only administrators may change central curriculum.");
    }
    private static List<Map<String,Object>> rows(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            try(ResultSet r=s.executeQuery()) {
                List<Map<String,Object>> out=new ArrayList<>();
                while(r.next()) {
                    Map<String,Object> row=new LinkedHashMap<>();
                    for(int i=1;i<=r.getMetaData().getColumnCount();i++) row.put(r.getMetaData().getColumnLabel(i),r.getObject(i));
                    out.add(row);
                }
                return out;
            }
        }
    }
    private static long number(Connection c, String sql, Object... args) throws SQLException {
        return ((Number)rows(c,sql,args).get(0).values().iterator().next()).longValue();
    }
    private static int integer(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    private static Map<String,Object> require(Connection c,String sql,Object...args) throws SQLException {
        var result=rows(c,sql,args);
        if(result.isEmpty()) throw error(404,"not_found","Requested curriculum object does not exist.");
        return result.get(0);
    }
    private static int write(Connection c,String sql,Object...args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            return s.executeUpdate();
        }
    }
    private <T> T transaction(SQLiteConnection.Transaction<T> work) throws SQLException {
        return transaction(work, result -> {});
    }
    private <T> T transaction(SQLiteConnection.Transaction<T> work, java.util.function.Consumer<T> committed) throws SQLException {
        try { return database.writeTransaction(work, committed); }
        catch(SQLException e) {
            if(e.getErrorCode()==19) throw error(409,"conflict","The name already exists in this scope or a referenced object changed.");
            if(e.getErrorCode()==5 || e.getErrorCode()==6) throw error(409,"conflict","Curriculum changed concurrently; please retry.");
            throw e;
        }
    }
    private static int authorize(Connection c, Actor actor, Scope s, boolean creating) throws SQLException {
        if(!actor.admin() && actor.teacherId()!=s.teacherId()) throw error(403,"forbidden","This context belongs to another teacher.");
        require(c,"SELECT id FROM teachers WHERE id=?",s.teacherId());
        require(c,"SELECT id FROM subjects WHERE id=?",s.subjectId());
        require(c,"SELECT id FROM semesters WHERE id=?",s.semesterId());
        int grade=integer(require(c,"SELECT grade FROM classes WHERE id=?",s.classId()),"grade");
        // The existing schema models teacher-class and teacher-subject independently.
        if(!actor.admin() && (number(c,"SELECT COUNT(*) FROM teacher_classes WHERE teacher_id=? AND class_id=?",s.teacherId(),s.classId())==0
                || number(c,"SELECT COUNT(*) FROM teacher_subjects WHERE teacher_id=? AND subject_id=?",s.teacherId(),s.subjectId())==0))
            throw error(403,"forbidden","Teacher must be assigned to both this class and this subject.");
        var previous=rows(c,"SELECT DISTINCT grade FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=?",
                s.teacherId(),s.subjectId(),s.classId(),s.semesterId());
        if(!previous.isEmpty()) {
            int stored=integer(previous.get(0),"grade");
            if(creating && stored!=grade) throw error(409,"conflict","Class grade changed; existing semester context is historical.");
            return stored;
        }
        return grade;
    }
    private static long central(Connection c,int subject,int grade,int semester) throws SQLException {
        return number(c,"SELECT COALESCE(SUM(t.tokens),0) FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=?",subject,grade,semester);
    }
    private static long flexible(Connection c,Scope s) throws SQLException {
        return number(c,"SELECT COALESCE(SUM(tokens),0) FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=?",
                s.teacherId(),s.subjectId(),s.classId(),s.semesterId());
    }
    private static void limit(List<Budget> budgets) {
        var exceeded=budgets.stream().filter(b->b.totalTokens()>HARD_LIMIT).toList();
        if(!exceeded.isEmpty()) throw new CurriculumException(409,"budget_exceeded","The 105-token limit would be exceeded in the listed contexts.",exceeded);
    }
    private static void centralLimit(Connection c,int topicId,long delta) throws SQLException {
        var topic=require(c,"SELECT subject,grade,semester FROM topics WHERE id=?",topicId);
        if(topic.get("semester")==null) {
            if(delta>0) throw error(409,"conflict","Assign a semester before increasing an archived topic's budget.");
            return;
        }
        int subject=integer(topic,"subject"),grade=integer(topic,"grade"),semester=integer(topic,"semester");
        long total=central(c,subject,grade,semester)+delta;
        List<Budget> budgets=new ArrayList<>();
        budgets.add(Budget.of(new Scope(0,subject,0,semester),grade,total,0));
        for(var row:rows(c,"SELECT owner_teacher,class,SUM(tokens) AS tokens FROM flexible_tasks WHERE subject=? AND grade=? AND semester=? GROUP BY owner_teacher,class",subject,grade,semester))
            budgets.add(Budget.of(new Scope(integer(row,"owner_teacher"),subject,integer(row,"class"),semester),grade,total,((Number)row.get("tokens")).longValue()));
        limit(budgets);
    }
    public Budget budget(Actor actor,Scope scope) throws SQLException {
        return transaction(c->{int grade=authorize(c,actor,scope,false);return Budget.of(scope,grade,central(c,scope.subjectId(),grade,scope.semesterId()),flexible(c,scope));});
    }
    public void renameTopic(Actor actor,int id,String name) throws SQLException {
        admin(actor);String value=validName(name);
        transaction(c->{require(c,"SELECT id FROM topics WHERE id=?",id);write(c,"UPDATE topics SET name=? WHERE id=?",value,id);return null;}, ignored -> Topic.refreshName(id,value));
    }
    public void editTask(Actor actor,int id,String name,int tokenValue) throws SQLException {
        admin(actor);String value=validName(name);tokens(tokenValue);
        transaction(c->{var old=require(c,"SELECT topic,tokens FROM tasks WHERE id=?",id);
            centralLimit(c,integer(old,"topic"),(long)tokenValue-integer(old,"tokens"));
            write(c,"UPDATE tasks SET name=?,tokens=? WHERE id=?",value,tokenValue,id);return null;}, ignored -> Task.refreshDefinition(id,value,tokenValue));
    }
    /** Core insertion path also used by legacy curriculum imports. */
    public int createCentralTask(int topicId,String name,TaskLevel level,int tokenValue) throws SQLException {
        String value=validName(name);tokens(tokenValue);
        if(level==null || level==TaskLevel.SPECIAL) throw error(400,"invalid_input","A central task requires a regular task level.");
        int id=transaction(c->{centralLimit(c,topicId,tokenValue);
            write(c,"INSERT INTO tasks(topic,name,niveau,tokens) VALUES(?,?,?,?)",topicId,value,level.getNumber(),tokenValue);
            return (int)number(c,"SELECT last_insert_rowid()");});
        Topic.invalidateTaskLists(topicId);return id;
    }
    public int createTopic(Actor actor,int subject,int grade,int semester,int number,String name) throws SQLException {
        admin(actor);String value=validName(name);
        if(grade<1 || grade>13 || number<1) throw error(400,"invalid_input","Invalid grade or topic number.");
        return transaction(c->{require(c,"SELECT id FROM subjects WHERE id=?",subject);require(c,"SELECT id FROM semesters WHERE id=?",semester);
            write(c,"INSERT INTO topics(subject,grade,semester,number,name) VALUES(?,?,?,?,?)",subject,grade,semester,number,value);
            return (int)number(c,"SELECT last_insert_rowid()");});
    }
    private static FlexibleTask task(Map<String,Object> r) {
        return new FlexibleTask(integer(r,"id"),integer(r,"owner_teacher"),integer(r,"subject"),integer(r,"class"),integer(r,"semester"),integer(r,"grade"),(String)r.get("name"),integer(r,"tokens"));
    }
    public List<FlexibleTask> list(Actor actor,Scope scope) throws SQLException {
        return transaction(c->{authorize(c,actor,scope,false);return rows(c,"SELECT * FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=? ORDER BY id",
                scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId()).stream().map(Curriculum::task).toList();});
    }
    public FlexibleTask create(Actor actor,Scope scope,String name,int tokenValue) throws SQLException {
        String value=validName(name);tokens(tokenValue);
        return transaction(c->{int grade=authorize(c,actor,scope,true);
            limit(List.of(Budget.of(scope,grade,central(c,scope.subjectId(),grade,scope.semesterId()),flexible(c,scope)+tokenValue)));
            write(c,"INSERT INTO flexible_tasks(owner_teacher,subject,class,semester,grade,name,tokens) VALUES(?,?,?,?,?,?,?)",
                    scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId(),grade,value,tokenValue);
            return task(require(c,"SELECT * FROM flexible_tasks WHERE id=last_insert_rowid()"));});
    }
    public FlexibleTask edit(Actor actor,int id,String name,int tokenValue) throws SQLException {
        String value=validName(name);tokens(tokenValue);
        return transaction(c->{FlexibleTask old=task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",id));
            authorize(c,actor,old.scope(),false);
            limit(List.of(Budget.of(old.scope(),old.grade(),central(c,old.subjectId(),old.grade(),old.semesterId()),flexible(c,old.scope())-old.tokens()+tokenValue)));
            write(c,"UPDATE flexible_tasks SET name=?,tokens=? WHERE id=?",value,tokenValue,id);
            return task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",id));});
    }
    /** Completions store identity only. The definition's current tokens always apply. */
    public void complete(Actor actor,int taskId,int studentId) throws SQLException {
        transaction(c->{FlexibleTask task=task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",taskId));
            authorize(c,actor,task.scope(),false);
            var student=require(c,"SELECT class FROM students WHERE id=?",studentId);
            if(integer(student,"class")!=task.classId()) throw error(403,"forbidden","Student does not belong to this task's class.");
            write(c,"INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?) ON CONFLICT(student,flexible_task) DO NOTHING",studentId,taskId);return null;});
    }
    public long completedTokens(int studentId,Scope scope) throws SQLException {
        return transaction(c->number(c,"SELECT COALESCE(SUM(t.tokens),0) FROM flexible_tasks t JOIN completed_flexible_tasks x ON x.flexible_task=t.id WHERE x.student=? AND t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=?",
                studentId,scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId()));
    }
    /** Scoped total for reports: current central definitions plus this teacher's flexible definitions. */
    public Map<String,Long> progress(Actor actor, int studentId, Scope scope) throws SQLException {
        return transaction(c -> {
            int grade = authorize(c, actor, scope, false);
            if (integer(require(c, "SELECT class FROM students WHERE id=?", studentId), "class") != scope.classId())
                throw error(403, "forbidden", "Student does not belong to this context's class.");
            long central = number(c, "SELECT COALESCE(SUM(t.tokens),0) FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.student=? AND x.status=2 AND p.subject=? AND p.grade=? AND p.semester=?",
                    studentId, scope.subjectId(), grade, scope.semesterId());
            long flexible = number(c, "SELECT COALESCE(SUM(t.tokens),0) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE x.student=? AND t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=?",
                    studentId, scope.teacherId(), scope.subjectId(), scope.classId(), scope.semesterId());
            return Map.of("centralTokens", central, "flexibleTokens", flexible, "totalTokens", central + flexible);
        });
    }

    /** Fresh catalog restricted to the actor's assignments; no credentials or student data. */
    public Map<String,Object> catalog(Actor actor) throws SQLException {
        return transaction(c->{Map<String,Object> out=new LinkedHashMap<>();out.put("admin",actor.admin());out.put("teacherId",actor.teacherId());
            out.put("subjects",rows(c,actor.admin()?"SELECT id,name FROM subjects ORDER BY name":"SELECT s.id,s.name FROM subjects s JOIN teacher_subjects t ON t.subject_id=s.id WHERE t.teacher_id=? ORDER BY s.name",actor.admin()?new Object[]{}:new Object[]{actor.teacherId()}));
            out.put("classes",rows(c,actor.admin()?"SELECT id,label,grade FROM classes ORDER BY grade,label":"SELECT s.id,s.label,s.grade FROM classes s JOIN teacher_classes t ON t.class_id=s.id WHERE t.teacher_id=? ORDER BY s.grade,s.label",actor.admin()?new Object[]{}:new Object[]{actor.teacherId()}));
            out.put("semesters",rows(c,"SELECT id,label,school_year FROM semesters ORDER BY school_year,position"));
            if(actor.admin()) out.put("teachers",rows(c,"SELECT id,first_name,last_name FROM teachers ORDER BY id"));
            return out;});
    }
    public Map<String,Object> centralStructure(Actor actor,int subject,int grade,int semester) throws SQLException {
        return transaction(c->{
            require(c,"SELECT id FROM subjects WHERE id=?",subject);require(c,"SELECT id FROM semesters WHERE id=?",semester);
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("topics",rows(c,"SELECT id,name,number FROM topics WHERE subject=? AND grade=? AND semester=? ORDER BY number",subject,grade,semester));
            out.put("tasks",rows(c,"SELECT t.id,t.topic,t.name,t.tokens,t.niveau FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=? ORDER BY p.number,t.id",subject,grade,semester));
            out.put("centralTokens",central(c,subject,grade,semester));out.put("regularLimit",REGULAR_LIMIT);out.put("hardLimit",HARD_LIMIT);return out;});
    }
}
