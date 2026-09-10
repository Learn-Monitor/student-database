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
        var previous=rows(c,"SELECT grade FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=? UNION SELECT grade FROM student_curriculum_contexts WHERE teacher=? AND subject=? AND class=? AND semester=?",
                s.teacherId(),s.subjectId(),s.classId(),s.semesterId(),s.teacherId(),s.subjectId(),s.classId(),s.semesterId());
        if(previous.size()>1) throw error(409,"context_conflict","Context contains inconsistent historical grades.");
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
    /** Assignment is administrative; teacher/class and teacher/subject memberships must exist even for admins. */
    public void assign(Actor actor, int studentId, Scope scope) throws SQLException {
        admin(actor);
        transaction(c -> {
            int grade = authorize(c, new Actor(false, scope.teacherId()), scope, true);
            if (integer(require(c,"SELECT class FROM students WHERE id=?",studentId),"class") != scope.classId())
                throw error(403,"forbidden","Student does not belong to this context's class.");
            var previous=rows(c,"SELECT grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",studentId,scope.subjectId(),scope.semesterId());
            if(!previous.isEmpty() && integer(previous.get(0),"grade")!=grade)
                throw error(409,"context_conflict","A transfer must preserve the semester's central curriculum grade.");
            // A change of context must not combine or silently discard previously completed flexible work.
            compatibleCompletions(c, studentId, scope, grade);
            limit(List.of(Budget.of(scope,grade,central(c,scope.subjectId(),grade,scope.semesterId()),flexible(c,scope))));
            write(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,?) "
                    + "ON CONFLICT(student,subject,semester) DO UPDATE SET teacher=excluded.teacher,class=excluded.class,grade=excluded.grade",
                    studentId,scope.subjectId(),scope.semesterId(),scope.teacherId(),scope.classId(),grade);
            return null;
        });
    }
    private static final String ACTIVE_COMPLETION = " NOT EXISTS (SELECT 1 FROM curriculum_completion_transfers m WHERE m.student=x.student AND m.source_task=x.flexible_task) ";
    public record Transfer(int sourceTaskId,int targetTaskId,int tokens) {}
    private static Scope assignmentScope(Map<String,Object> assignment,int subject,int semester) {
        return new Scope(integer(assignment,"teacher"),subject,integer(assignment,"class"),semester);
    }
    private static List<FlexibleTask> activeTasks(Connection c,int student,int subject,int semester) throws SQLException {
        return rows(c,"SELECT t.* FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE x.student=? AND t.subject=? AND t.semester=? AND " + ACTIVE_COMPLETION,
                student,subject,semester).stream().map(Curriculum::task).toList();
    }
    private static Map<String,Object> transferSource(Connection c,int student,Scope target) throws SQLException {
        var found=rows(c,"SELECT teacher,class,grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",student,target.subjectId(),target.semesterId());
        if(!found.isEmpty())return found.get(0);
        // Explicit recovery of unassigned legacy data: sentinel is checked again at commit, never persisted.
        return Map.of("teacher",0,"class",0,"grade",authorize(c,new Actor(false,target.teacherId()),target,true));
    }
    /** Read-only preview; explicit per-completion mapping is required on submission. */
    public Map<String,Object> transferPreview(Actor actor,int student,Scope target) throws SQLException {
        admin(actor);
        return transaction(c->{
            var from=transferSource(c,student,target);
            validateTransferTarget(c,student,target,integer(from,"grade"));
            return Map.of("source",assignmentScope(from,target.subjectId(),target.semesterId()),
                    "completions",activeTasks(c,student,target.subjectId(),target.semesterId()),
                    "targets",rows(c,"SELECT t.* FROM flexible_tasks t WHERE t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=? "
                            + "AND NOT EXISTS (SELECT 1 FROM completed_flexible_tasks x WHERE x.student=? AND x.flexible_task=t.id) ORDER BY t.id",
                            target.teacherId(),target.subjectId(),target.classId(),target.semesterId(),student).stream().map(Curriculum::task).toList());
        });
    }
    private static int validateTransferTarget(Connection c,int student,Scope target,int previousGrade) throws SQLException {
        int grade=authorize(c,new Actor(false,target.teacherId()),target,true);
        if(integer(require(c,"SELECT class FROM students WHERE id=?",student),"class")!=target.classId())
            throw error(403,"forbidden","Student does not belong to this context's class.");
        if(grade!=previousGrade) throw error(409,"context_conflict","A transfer must preserve the semester's central curriculum grade.");
        limit(List.of(Budget.of(target,grade,central(c,target.subjectId(),grade,target.semesterId()),flexible(c,target))));
        return grade;
    }
    /** Atomically transfer every active completion to an explicitly selected, equally valued target task. */
    public void transfer(Actor actor,int student,Scope expectedSource,Scope target,List<Transfer> transfers) throws SQLException {
        admin(actor);
        if(expectedSource.subjectId()!=target.subjectId() || expectedSource.semesterId()!=target.semesterId() || expectedSource.equals(target))
            throw error(400,"invalid_input","Transfer requires a different context in the same subject and semester.");
        if(transfers==null) throw error(400,"invalid_input","Explicit completion mappings required.");
        transaction(c->{
            var from=transferSource(c,student,target);
            if(!assignmentScope(from,target.subjectId(),target.semesterId()).equals(expectedSource))
                throw error(409,"context_conflict","Assignment changed; reload the transfer preview.");
            int grade=validateTransferTarget(c,student,target,integer(from,"grade"));
            var completed=activeTasks(c,student,target.subjectId(),target.semesterId());
            Map<Integer,FlexibleTask> sources=new HashMap<>();for(var task:completed)sources.put(task.id(),task);
            Set<Integer> seenSources=new HashSet<>(),seenTargets=new HashSet<>();
            if(transfers.size()!=sources.size()) throw error(409,"context_conflict","Every active completion must be transferred exactly once.");
            for(var mapping:transfers) {
                if(mapping==null || !seenSources.add(mapping.sourceTaskId()) || !seenTargets.add(mapping.targetTaskId()) || !sources.containsKey(mapping.sourceTaskId()))
                    throw error(409,"context_conflict","Completion mapping is incomplete or duplicated.");
                var source=sources.get(mapping.sourceTaskId());
                var destination=task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",mapping.targetTaskId()));
                if(!destination.scope().equals(target) || destination.grade()!=grade || source.grade()!=grade)
                    throw error(409,"context_conflict","Completion mapping belongs to a different context.");
                if(mapping.tokens()!=source.tokens() || mapping.tokens()!=destination.tokens())
                    throw error(409,"context_conflict","Transferred tasks must retain the previewed token value.");
                if(number(c,"SELECT COUNT(*) FROM completed_flexible_tasks WHERE student=? AND flexible_task=?",student,destination.id())>0)
                    throw error(409,"context_conflict","Target task already has completion history.");
                write(c,"INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?)",student,destination.id());
                write(c,"INSERT INTO curriculum_completion_transfers(student,source_task,target_task) VALUES(?,?,?)",student,source.id(),destination.id());
            }
            compatibleCompletions(c,student,target,grade);
            write(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,?) "
                    + "ON CONFLICT(student,subject,semester) DO UPDATE SET teacher=excluded.teacher,class=excluded.class,grade=excluded.grade",
                    student,target.subjectId(),target.semesterId(),target.teacherId(),target.classId(),grade);
            return null;
        });
    }
    private static void compatibleCompletions(Connection c, int studentId, Scope scope, int grade) throws SQLException {
        if (number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task "
                + "WHERE x.student=? AND t.subject=? AND t.semester=? AND " + ACTIVE_COMPLETION + " AND (t.owner_teacher<>? OR t.class<>? OR t.grade<>?)",
                studentId,scope.subjectId(),scope.semesterId(),scope.teacherId(),scope.classId(),grade)>0)
            throw error(409,"context_conflict","Completed flexible tasks belong to a different context; an explicit correction is required.");
    }
    private static Map<String,Object> assigned(Connection c,int studentId,int subject,int semester) throws SQLException {
        var found=rows(c,"SELECT teacher,class,grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",studentId,subject,semester);
        if(found.isEmpty()) throw error(409,"context_unassigned","No curriculum context is assigned for this subject and semester.");
        return found.get(0);
    }
    private static int requireAssignment(Connection c,int studentId,Scope scope) throws SQLException {
        var assignment=assigned(c,studentId,scope.subjectId(),scope.semesterId());
        if(integer(assignment,"teacher")!=scope.teacherId() || integer(assignment,"class")!=scope.classId())
            throw error(403,"forbidden","Student is assigned to another curriculum context.");
        int grade=integer(assignment,"grade");
        compatibleCompletions(c,studentId,scope,grade);
        return grade;
    }
    /** Minimal class roster for explicit administrative assignment; no credentials. */
    public List<Map<String,Object>> students(Actor actor,Scope scope) throws SQLException {
        admin(actor);
        return transaction(c->{authorize(c,new Actor(false,scope.teacherId()),scope,false);
            return rows(c,"SELECT s.id,s.first_name,s.last_name,a.teacher AS teacherId,a.class AS classId FROM students s "
                    + "LEFT JOIN student_curriculum_contexts a ON a.student=s.id AND a.subject=? AND a.semester=? "
                    + "WHERE s.class=? ORDER BY s.last_name,s.first_name,s.id",scope.subjectId(),scope.semesterId(),scope.classId());});
    }
    /** Completions store identity only. The definition's current tokens always apply. */
    public void complete(Actor actor,int taskId,int studentId) throws SQLException {
        transaction(c->{FlexibleTask task=task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",taskId));
            authorize(c,actor,task.scope(),false);
            var student=require(c,"SELECT class FROM students WHERE id=?",studentId);
            if(integer(student,"class")!=task.classId()) throw error(403,"forbidden","Student does not belong to this task's class.");
            int grade=requireAssignment(c,studentId,task.scope());
            if(number(c,"SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=? AND source_task=?",studentId,taskId)>0)
                throw error(409,"context_conflict","This completion was already transferred.");
            if(grade!=task.grade()) throw error(409,"context_conflict","Context contains inconsistent historical grades.");
            limit(List.of(Budget.of(task.scope(),grade,central(c,task.subjectId(),grade,task.semesterId()),flexible(c,task.scope()))));
            write(c,"INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?) ON CONFLICT(student,flexible_task) DO NOTHING",studentId,taskId);return null;});
    }
    public long completedTokens(int studentId,Scope scope) throws SQLException {
        return transaction(c->{requireAssignment(c,studentId,scope);return flexibleCompleted(c,studentId,scope);});
    }
    public record CompletedCentralTask(int id, String name, int tokens, int niveau, int topicId, String topicName) {}
    public record CompletedFlexibleTask(int id, String name, int tokens) {}

    private static List<CompletedFlexibleTask> completedFlexibleTasks(Connection c,int studentId,Scope scope) throws SQLException {
        return rows(c,"SELECT t.id,t.name,t.tokens FROM flexible_tasks t JOIN completed_flexible_tasks x ON x.flexible_task=t.id WHERE x.student=? AND t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=? AND " + ACTIVE_COMPLETION + " ORDER BY t.id",
                studentId,scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId()).stream()
                .map(r->new CompletedFlexibleTask(integer(r,"id"),(String)r.get("name"),integer(r,"tokens"))).toList();
    }
    private static long flexibleCompleted(Connection c,int studentId,Scope scope) throws SQLException {
        return completedFlexibleTasks(c,studentId,scope).stream().mapToLong(CompletedFlexibleTask::tokens).sum();
    }
    private static Map<String,Object> progress(Connection c,int studentId,Scope scope) throws SQLException {
        int grade=requireAssignment(c,studentId,scope);
        limit(List.of(Budget.of(scope,grade,central(c,scope.subjectId(),grade,scope.semesterId()),flexible(c,scope))));
        List<CompletedCentralTask> completedCentralTasks=rows(c,
                "SELECT t.id,t.name,t.tokens,t.niveau,p.id AS topicId,p.name AS topicName FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.student=? AND x.status=2 AND p.subject=? AND p.grade=? AND p.semester=? ORDER BY t.id",
                studentId,scope.subjectId(),grade,scope.semesterId()).stream()
                .map(r->new CompletedCentralTask(integer(r,"id"),(String)r.get("name"),integer(r,"tokens"),integer(r,"niveau"),integer(r,"topicId"),(String)r.get("topicName"))).toList();
        List<CompletedFlexibleTask> completedFlexibleTasks=completedFlexibleTasks(c,studentId,scope);
        // Totals are derived from the exact returned identities in this transaction, never a second query/cache.
        long central=completedCentralTasks.stream().mapToLong(CompletedCentralTask::tokens).sum();
        long flexible=completedFlexibleTasks.stream().mapToLong(CompletedFlexibleTask::tokens).sum();
        return Map.of("centralTokens",central,"flexibleTokens",flexible,"totalTokens",central+flexible,
                "completedCentralTasks",completedCentralTasks,"completedFlexibleTasks",completedFlexibleTasks);
    }
    /** Assigned total for staff; both their scope access and the student's explicit assignment apply. */
    public Map<String,Object> progress(Actor actor, int studentId, Scope scope) throws SQLException {
        return transaction(c -> {authorize(c,actor,scope,false);return progress(c,studentId,scope);});
    }
    /** Student identity comes exclusively from the session; the client cannot choose a teacher or class. */
    public Map<String,Object> studentProgress(User user,int subject,int semester) throws SQLException {
        if(user==null || user==User.ANONYMOUS) throw error(401,"unauthorized","Please sign in.");
        if(!user.isStudent()) throw error(403,"forbidden","Student session required.");
        int studentId=user.asStudent().getId();
        return transaction(c->{require(c,"SELECT id FROM students WHERE id=?",studentId);
            var assignment=assigned(c,studentId,subject,semester);
            return progress(c,studentId,new Scope(integer(assignment,"teacher"),subject,integer(assignment,"class"),semester));});
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
