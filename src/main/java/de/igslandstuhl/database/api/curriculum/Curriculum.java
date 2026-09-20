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
    public enum ActiveStageType { CENTRAL, FLEXIBLE }
    public enum AssessmentStatus { PASSED, FAILED_ONCE, FAILED_TWICE, LOCKED }
    public record ActiveStage(ActiveStageType type, int taskId, int subjectId, int semesterId, String name) {}
    public record PartnerCandidate(int id, String name) {}
    public record StudentSubject(int id, String name) {}
    public record StageAssessment(AssessmentStatus status, boolean earned) {}
    public record StudentStageProgress(ActiveStageType type, int stageId, Integer topicId, String topicName,
                                      String name, int tokens, AssessmentStatus status, boolean earned,
                                      boolean inProgress) {}
    public record StudentProgressDetail(int studentId, String studentName, int subjectId, int semesterId,
                                        List<StudentStageProgress> stages) {}
    private record CentralTask(int id, int subjectId, int semesterId, int grade, int topicId, String name) {}
    static CurriculumException error(int status, String code, String message) {
        return new CurriculumException(status, code, message);
    }
    public static String validName(String value) {
        if (value == null || value.strip().isEmpty() || value.strip().length() > 200
                || value.chars().anyMatch(Character::isISOControl))
            throw error(400, "invalid_input", "Name must contain 1–200 printable characters.");
        return value.strip();
    }
    private static void tokens(int value) {
        if (value < 0 || value > HARD_LIMIT) throw error(400, "invalid_input", "Tokens must be between 0 and 105.");
    }
    private static int positive(int value, String name) {
        if (value < 1) throw error(400, "invalid_input", name + " must be positive.");
        return value;
    }
    static void admin(Actor actor) {
        if (!actor.admin()) throw error(403, "forbidden", "Only administrators may change central curriculum.");
    }
    static List<Map<String,Object>> rows(Connection c, String sql, Object... args) throws SQLException {
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
    static long number(Connection c, String sql, Object... args) throws SQLException {
        return ((Number)rows(c,sql,args).get(0).values().iterator().next()).longValue();
    }
    static int integer(Map<String,Object> row,String key) { return ((Number)row.get(key)).intValue(); }
    static Map<String,Object> require(Connection c,String sql,Object...args) throws SQLException {
        var result=rows(c,sql,args);
        if(result.isEmpty()) throw error(404,"not_found","Requested curriculum object does not exist.");
        return result.get(0);
    }
    static int write(Connection c,String sql,Object...args) throws SQLException {
        try(PreparedStatement s=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) s.setObject(i+1,args[i]);
            return s.executeUpdate();
        }
    }
    <T> T transaction(SQLiteConnection.Transaction<T> work) throws SQLException {
        return transaction(work, result -> {});
    }
    <T> T transaction(SQLiteConnection.Transaction<T> work, java.util.function.Consumer<T> committed) throws SQLException {
        try { return database.writeTransaction(work, committed); }
        catch(SQLException e) {
            if(e.getErrorCode()==19) throw error(409,"conflict","The name already exists in this scope or a referenced object changed.");
            if(e.getErrorCode()==5 || e.getErrorCode()==6) throw error(409,"conflict","Curriculum changed concurrently; please retry.");
            throw e;
        }
    }
    static int authorize(Connection c, Actor actor, Scope s, boolean creating) throws SQLException {
        if(!actor.admin() && actor.teacherId()!=s.teacherId()) throw error(403,"forbidden","This context belongs to another teacher.");
        require(c,"SELECT id FROM teachers WHERE id=?",s.teacherId());
        require(c,"SELECT id FROM subjects WHERE id=?",s.subjectId());
        require(c,"SELECT id FROM semesters WHERE id=?",s.semesterId());
        var classRow=require(c,"SELECT grade,COALESCE(active,1) AS active FROM classes WHERE id=?",s.classId());
        int grade=integer(classRow,"grade");
        if(creating && (s.classId()==SchoolClass.UNASSIGNED_CLASS_ID || grade==0 || integer(classRow,"active")==0))
            throw error(409,"context_unassigned","This class is not available for a current curriculum context.");
        if(!actor.admin() && !managedTeacher(c,s,grade) && !legacyTeacher(c,s))
            throw error(403,"forbidden","Teacher must be assigned to this managed or legacy curriculum context.");
        var previous=rows(c,"SELECT grade FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=? UNION SELECT grade FROM student_curriculum_contexts WHERE teacher=? AND subject=? AND class=? AND semester=? UNION SELECT grade FROM flexible_topics WHERE owner_teacher=? AND subject=? AND class=? AND semester=?",
                s.teacherId(),s.subjectId(),s.classId(),s.semesterId(),s.teacherId(),s.subjectId(),s.classId(),s.semesterId(),s.teacherId(),s.subjectId(),s.classId(),s.semesterId());
        if(previous.size()>1) throw error(409,"context_conflict","Context contains inconsistent historical grades.");
        if(!previous.isEmpty()) {
            int stored=integer(previous.get(0),"grade");
            if(creating && stored!=grade) throw error(409,"conflict","Class grade changed; existing semester context is historical.");
            return stored;
        }
        return grade;
    }
    static boolean individualSubject(Connection c,int subject) throws SQLException {
        return number(c,"SELECT COUNT(*) FROM curriculum_subject_types WHERE subject=? AND mode='INDIVIDUAL'",subject)>0;
    }
    static String assignmentGroup(Connection c,int subject) throws SQLException {
        var rows=rows(c,"SELECT assignment_group FROM curriculum_subject_types WHERE subject=? AND mode='INDIVIDUAL'",subject);
        if(rows.isEmpty() || rows.get(0).get("assignment_group")==null)
            throw error(400,"invalid_input","Individual subject requires an assignment group.");
        return String.valueOf(rows.get(0).get("assignment_group"));
    }
    static boolean managedTeacher(Connection c,Scope s,int grade) throws SQLException {
        if(individualSubject(c,s.subjectId()))
            return number(c,"SELECT COUNT(*) FROM curriculum_grade_teachers WHERE semester=? AND grade=? AND subject=? AND teacher=?",
                    s.semesterId(),grade,s.subjectId(),s.teacherId())>0;
        return number(c,"SELECT COUNT(*) FROM curriculum_class_teachers WHERE semester=? AND class=? AND subject=? AND teacher=?",
                s.semesterId(),s.classId(),s.subjectId(),s.teacherId())>0;
    }
    private static boolean legacyTeacher(Connection c,Scope s) throws SQLException {
        return number(c,"SELECT COUNT(*) FROM teacher_classes WHERE teacher_id=? AND class_id=?",s.teacherId(),s.classId())>0
                && number(c,"SELECT COUNT(*) FROM teacher_subjects WHERE teacher_id=? AND subject_id=?",s.teacherId(),s.subjectId())>0;
    }
    static long central(Connection c,int subject,int grade,int semester) throws SQLException {
        return number(c,"SELECT COALESCE(SUM(t.tokens),0) FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=?",subject,grade,semester);
    }
    static long flexible(Connection c,Scope s) throws SQLException {
        return number(c,"SELECT COALESCE(SUM(tokens),0) FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=?",
                s.teacherId(),s.subjectId(),s.classId(),s.semesterId());
    }
    static void limit(List<Budget> budgets) {
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
            if (integer(old,"tokens") != tokenValue && number(c,"SELECT COUNT(*) FROM taskstats WHERE task=? AND status=?",id,Task.STATUS_COMPLETED)>0)
                throw error(409,"completion_history_conflict","Der Münzwert kann nicht geändert werden, weil bereits Leistungen bestätigt wurden.");
            centralLimit(c,integer(old,"topic"),(long)tokenValue-integer(old,"tokens"));
            write(c,"UPDATE tasks SET name=?,tokens=? WHERE id=?",value,tokenValue,id);return null;}, ignored -> Task.refreshDefinition(id,value,tokenValue));
    }
    /** Core insertion path also used by legacy curriculum imports. */
    public int createCentralTask(int topicId,String name,TaskLevel level,int tokenValue) throws SQLException {
        return createCentralTask(topicId,name,level,0,tokenValue);
    }
    public int createCentralTask(int topicId,String name,TaskLevel level,int stageNumber,int tokenValue) throws SQLException {
        String value=validName(name);tokens(tokenValue);
        if(level==null || level==TaskLevel.SPECIAL) throw error(400,"invalid_input","A central task requires a regular task level.");
        if(stageNumber<0) throw error(400,"invalid_input","Stage number cannot be negative.");
        int id=transaction(c->{centralLimit(c,topicId,tokenValue);
            int number=stageNumber>0?stageNumber:(int)number(c,"SELECT COALESCE(MAX(stage_number),0)+1 FROM tasks WHERE topic=?",topicId);
            write(c,"INSERT INTO tasks(topic,name,niveau,stage_number,tokens) VALUES(?,?,?,?,?)",topicId,value,level.getNumber(),number,tokenValue);
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
        return create(actor,scope,name,tokenValue,null);
    }
    public FlexibleTask create(Actor actor,Scope scope,String name,int tokenValue,Integer topicId) throws SQLException {
        String value=validName(name);tokens(tokenValue);
        return transaction(c->{int grade=authorize(c,actor,scope,true);
            limit(List.of(Budget.of(scope,grade,central(c,scope.subjectId(),grade,scope.semesterId()),flexible(c,scope)+tokenValue)));
            write(c,"INSERT INTO flexible_tasks(owner_teacher,subject,class,semester,grade,name,tokens) VALUES(?,?,?,?,?,?,?)",
                    scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId(),grade,value,tokenValue);
            var created=task(require(c,"SELECT * FROM flexible_tasks WHERE id=last_insert_rowid()"));
            setFlexibleTopic(c,created,topicId);
            return created;});
    }
    public FlexibleTask edit(Actor actor,int id,String name,int tokenValue) throws SQLException {
        return edit(actor,id,name,tokenValue,false,null);
    }
    /** An omitted topic preserves the old association; explicit null removes it. */
    public FlexibleTask edit(Actor actor,int id,String name,int tokenValue,boolean updateTopic,Integer topicId) throws SQLException {
        String value=validName(name);tokens(tokenValue);
        return transaction(c->{FlexibleTask old=task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",id));
            authorize(c,actor,old.scope(),false);
            if(old.tokens()!=tokenValue && number(c,"SELECT COUNT(*) FROM completed_flexible_tasks WHERE flexible_task=?",id)>0)
                throw error(409,"completion_history_conflict","Der Münzwert kann nicht geändert werden, weil bereits Leistungen bestätigt wurden.");
            limit(List.of(Budget.of(old.scope(),old.grade(),central(c,old.subjectId(),old.grade(),old.semesterId()),flexible(c,old.scope())-old.tokens()+tokenValue)));
            write(c,"UPDATE flexible_tasks SET name=?,tokens=? WHERE id=?",value,tokenValue,id);
            if(updateTopic) setFlexibleTopic(c,old,topicId);
            return task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",id));});
    }

    private static void setFlexibleTopic(Connection c,FlexibleTask task,Integer topicId) throws SQLException {
        if(topicId==null) {
            write(c,"DELETE FROM flexible_task_topics WHERE flexible_task=?",task.id());
            return;
        }
        var topic=require(c,"SELECT * FROM flexible_topics WHERE id=?",topicId);
        if(integer(topic,"owner_teacher")!=task.ownerTeacher() || integer(topic,"subject")!=task.subjectId()
                || integer(topic,"class")!=task.classId() || integer(topic,"semester")!=task.semesterId()
                || integer(topic,"grade")!=task.grade())
            throw error(403,"forbidden","Topic does not belong to this task's context.");
        write(c,"INSERT INTO flexible_task_topics(flexible_task,flexible_topic) VALUES(?,?) ON CONFLICT(flexible_task) DO UPDATE SET flexible_topic=excluded.flexible_topic",task.id(),topicId);
    }
    private static List<Map<String,Object>> flexibleTopics(Connection c,Scope scope) throws SQLException {
        return rows(c,"SELECT id,name FROM flexible_topics WHERE owner_teacher=? AND subject=? AND class=? AND semester=? ORDER BY id",
                scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId());
    }
    private static List<Map<String,Object>> plannedFlexibleTasks(Connection c,Scope scope) throws SQLException {
        return rows(c,"SELECT t.id,t.name,t.tokens,p.id AS topicId,p.name AS topicName FROM flexible_tasks t "
                + "LEFT JOIN flexible_task_topics m ON m.flexible_task=t.id LEFT JOIN flexible_topics p ON p.id=m.flexible_topic "
                + "AND p.owner_teacher=t.owner_teacher AND p.subject=t.subject AND p.class=t.class AND p.semester=t.semester AND p.grade=t.grade "
                + "WHERE t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=? ORDER BY t.id",
                scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId());
    }
    public Map<String,Object> flexibleStructure(Actor actor,Scope scope) throws SQLException {
        return transaction(c->{authorize(c,actor,scope,false);
            return Map.of("topics",flexibleTopics(c,scope),"tasks",plannedFlexibleTasks(c,scope));});
    }
    public int createFlexibleTopic(Actor actor,Scope scope,String name) throws SQLException {
        String value=validName(name);
        return transaction(c->{int grade=authorize(c,actor,scope,true);
            write(c,"INSERT INTO flexible_topics(owner_teacher,subject,class,semester,grade,name) VALUES(?,?,?,?,?,?)",
                    scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId(),grade,value);
            return (int)number(c,"SELECT last_insert_rowid()");});
    }
    public void renameFlexibleTopic(Actor actor,int topicId,String name) throws SQLException {
        String value=validName(name);
        transaction(c->{var topic=require(c,"SELECT * FROM flexible_topics WHERE id=?",topicId);
            authorize(c,actor,new Scope(integer(topic,"owner_teacher"),integer(topic,"subject"),integer(topic,"class"),integer(topic,"semester")),false);
            write(c,"UPDATE flexible_topics SET name=? WHERE id=?",value,topicId);return null;});
    }
    /** Assignment is administrative; teacher/class and teacher/subject memberships must exist even for admins. */
    public void assign(Actor actor, int studentId, Scope scope) throws SQLException {
        admin(actor);
        transaction(c -> { assign(c,studentId,scope); return null; });
    }
    static void assign(Connection c,int studentId,Scope scope) throws SQLException {
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
    }
    private static final String ACTIVE_COMPLETION = " NOT EXISTS (SELECT 1 FROM curriculum_completion_transfers m WHERE m.student=x.student AND m.source_task=x.flexible_task) ";
    public record Transfer(int sourceTaskId,int targetTaskId,int tokens) {}
    static Scope assignmentScope(Map<String,Object> assignment,int subject,int semester) {
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
    static void compatibleCompletions(Connection c, int studentId, Scope scope, int grade) throws SQLException {
        if (number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task "
                + "WHERE x.student=? AND t.subject=? AND t.semester=? AND " + ACTIVE_COMPLETION + " AND (t.owner_teacher<>? OR t.class<>? OR t.grade<>?)",
                studentId,scope.subjectId(),scope.semesterId(),scope.teacherId(),scope.classId(),grade)>0)
            throw error(409,"context_conflict","Completed flexible tasks belong to a different context; an explicit correction is required.");
    }
    static Map<String,Object> assigned(Connection c,int studentId,int subject,int semester) throws SQLException {
        CurriculumEnrollment.requireSelectedWpf(c,studentId,subject,semester);
        var found=rows(c,"SELECT teacher,class,grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",studentId,subject,semester);
        if(found.isEmpty()) throw error(409,"context_unassigned","No curriculum context is assigned for this subject and semester.");
        int currentClass=integer(require(c,"SELECT class FROM students WHERE id=?",studentId),"class");
        int assignedClass=integer(found.get(0),"class");
        if(currentClass==SchoolClass.UNASSIGNED_CLASS_ID || currentClass!=assignedClass)
            throw error(409,"context_unassigned","No current curriculum context is assigned for this subject and semester.");
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
    private static CentralTask centralTask(Connection c,int taskId) throws SQLException {
        var task=require(c,"SELECT t.id,t.name,p.subject,p.grade,p.semester,p.id AS topic FROM tasks t JOIN topics p ON p.id=t.topic WHERE t.id=?",taskId);
        if(task.get("semester")==null) throw error(409,"context_unassigned","Task is not assigned to a managed semester.");
        return new CentralTask(integer(task,"id"),integer(task,"subject"),integer(task,"semester"),integer(task,"grade"),integer(task,"topic"),(String)task.get("name"));
    }
    private static int currentSemester(Connection c) throws SQLException {
        var found=rows(c,"SELECT y.current_semester FROM school_years y JOIN semesters s ON s.id=y.current_semester "
                + "WHERE y.start_date IS NOT NULL AND y.end_date IS NOT NULL AND date('now') BETWEEN date(y.start_date) AND date(y.end_date) "
                + "AND y.current_semester IS NOT NULL ORDER BY y.id DESC LIMIT 1");
        if(found.isEmpty()) throw error(409,"current_semester_unavailable","No current semester is configured.");
        return integer(found.get(0),"current_semester");
    }
    public List<StudentSubject> studentCurrentSubjects(int studentId) throws SQLException {
        return transaction(c -> {
            int semester = currentSemester(c);
            return rows(c,"SELECT DISTINCT s.id,s.name FROM student_curriculum_contexts x "
                    + "JOIN subjects s ON s.id=x.subject WHERE x.student=? AND x.semester=? "
                    + "ORDER BY s.name,s.id",studentId,semester).stream()
                    .map(r -> new StudentSubject(integer(r,"id"),(String) r.get("name"))).toList();
        });
    }
    public List<PartnerCandidate> partnerCandidates(int studentId,int subjectId) throws SQLException {
        return transaction(c->{
            int semester=currentSemester(c);
            var context=rows(c,"SELECT grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",studentId,subjectId,semester);
            if(context.isEmpty()) throw error(403,"forbidden","Student is not assigned to this subject in the current semester.");
            int grade=integer(context.get(0),"grade");
            var active=rows(c,"SELECT central_task,flexible_task FROM student_active_curriculum_stages WHERE student=? AND subject=? AND semester=?",studentId,subjectId,semester);
            if(active.isEmpty()) return List.of();
            var stage=active.get(0);
            String stageColumn=stage.get("central_task")!=null?"central_task":"flexible_task";
            int taskId=integer(stage,stageColumn);
            return rows(c,"SELECT s.id,(s.first_name || ' ' || s.last_name) AS name "
                    + "FROM student_active_curriculum_stages a "
                    + "JOIN students s ON s.id=a.student "
                    + "JOIN student_curriculum_contexts x ON x.student=a.student AND x.subject=a.subject AND x.semester=a.semester "
                    + "JOIN student_subject_requests r ON r.student=a.student AND r.subject=a.subject AND r.semester=a.semester AND r.request_type='PARTNER' "
                    + "WHERE a.subject=? AND a.semester=? AND a.student<>? AND COALESCE(s.active,1)=1 AND x.grade=? "
                    + "AND a." + stageColumn + "=? AND a." + ("central_task".equals(stageColumn)?"flexible_task":"central_task") + " IS NULL "
                    + "ORDER BY s.last_name,s.first_name,s.id",
                    subjectId,semester,studentId,grade,taskId).stream()
                    .map(r->new PartnerCandidate(integer(r,"id"),(String)r.get("name"))).toList();
        });
    }
    private static int authorizeAssessmentRead(Connection c, Actor actor, int studentId, Scope scope) throws SQLException {
        if(!actor.admin() && actor.teacherId()!=scope.teacherId())
            throw error(403,"forbidden","This context belongs to another teacher.");
        require(c,"SELECT id FROM teachers WHERE id=?",scope.teacherId());
        require(c,"SELECT id FROM subjects WHERE id=?",scope.subjectId());
        require(c,"SELECT id FROM semesters WHERE id=?",scope.semesterId());
        int grade=integer(require(c,"SELECT grade FROM classes WHERE id=?",scope.classId()),"grade");
        if(!managedTeacher(c,scope,grade))
            throw error(403,"forbidden","Teacher must be assigned to this managed curriculum context.");
        var context=rows(c,"SELECT teacher,class,grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",
                studentId,scope.subjectId(),scope.semesterId());
        if(context.isEmpty() || integer(context.get(0),"teacher")!=scope.teacherId()
                || integer(context.get(0),"class")!=scope.classId() || integer(context.get(0),"grade")!=grade)
            throw error(403,"forbidden","Student is not assigned to this curriculum context.");
        return grade;
    }
    private static int validateAssessmentStage(Connection c, Actor actor, int studentId, Scope scope,
                                               ActiveStageType stageType, int stageId) throws SQLException {
        int grade=authorizeAssessmentRead(c,actor,studentId,scope);
        if(stageType==null || stageId<1) throw error(400,"invalid_input","Assessment stage is invalid.");
        if(stageType==ActiveStageType.CENTRAL) {
            require(c,"SELECT t.id FROM tasks t JOIN topics p ON p.id=t.topic WHERE t.id=? AND p.subject=? AND p.semester=? AND p.grade=?",
                    stageId,scope.subjectId(),scope.semesterId(),grade);
        } else {
            require(c,"SELECT id FROM flexible_tasks WHERE id=? AND owner_teacher=? AND subject=? AND class=? AND semester=? AND grade=?",
                    stageId,scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId(),grade);
        }
        return grade;
    }
    private static boolean earned(Connection c,int studentId,Scope scope,ActiveStageType stageType,int stageId,int grade) throws SQLException {
        return stageType==ActiveStageType.CENTRAL
                ? number(c,"SELECT COUNT(*) FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.student=? AND x.task=? AND x.status=? AND p.subject=? AND p.semester=? AND p.grade=?",
                        studentId,stageId,Task.STATUS_COMPLETED,scope.subjectId(),scope.semesterId(),grade)>0
                : number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE x.student=? AND x.flexible_task=? AND t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=? AND t.grade=? AND " + ACTIVE_COMPLETION,
                        studentId,stageId,scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId(),grade)>0;
    }
    private static AssessmentStatus assessmentOverride(Connection c,int studentId,Scope scope,ActiveStageType stageType,int stageId) throws SQLException {
        var override=rows(c,"SELECT status FROM student_curriculum_stage_assessments WHERE student=? AND subject=? AND semester=? AND stage_type=? AND stage_id=?",
                studentId,scope.subjectId(),scope.semesterId(),stageType.name(),stageId);
        return override.isEmpty()?null:AssessmentStatus.valueOf(String.valueOf(override.get(0).get("status")));
    }
    public StageAssessment stageAssessment(Actor actor, int studentId, Scope scope,
                                           ActiveStageType stageType, int stageId) throws SQLException {
        return transaction(c -> {
            int grade=validateAssessmentStage(c,actor,studentId,scope,stageType,stageId);
            boolean earned=earned(c,studentId,scope,stageType,stageId,grade);
            AssessmentStatus status=assessmentOverride(c,studentId,scope,stageType,stageId);
            if(status==null && earned) status=AssessmentStatus.PASSED;
            return new StageAssessment(status,earned);
        });
    }
    private static StageAssessment stageAssessment(Connection c, int studentId, Scope scope,
                                                  ActiveStageType stageType, int stageId, int grade) throws SQLException {
        boolean earned=earned(c,studentId,scope,stageType,stageId,grade);
        AssessmentStatus status=assessmentOverride(c,studentId,scope,stageType,stageId);
        if(status==null && earned) status=AssessmentStatus.PASSED;
        return new StageAssessment(status,earned);
    }
    /** Canonical teacher read model for every central and flexible stage in one exact context. */
    public StudentProgressDetail studentProgressDetail(Actor actor, int studentId, Scope scope) throws SQLException {
        return transaction(c -> {
            int grade=authorizeAssessmentRead(c,actor,studentId,scope);
            var student=require(c,"SELECT first_name,last_name FROM students WHERE id=?",studentId);
            String studentName=student.get("first_name")+" "+student.get("last_name");
            var activeRows=rows(c,"SELECT central_task,flexible_task FROM student_active_curriculum_stages "
                    + "WHERE student=? AND subject=? AND semester=?",studentId,scope.subjectId(),scope.semesterId());
            ActiveStageType activeType=null; int activeId=0;
            if(!activeRows.isEmpty()) {
                var active=activeRows.get(0);
                if(active.get("central_task")!=null) {
                    activeType=ActiveStageType.CENTRAL; activeId=integer(active,"central_task");
                } else if(active.get("flexible_task")!=null) {
                    activeType=ActiveStageType.FLEXIBLE; activeId=integer(active,"flexible_task");
                }
            }
            List<StudentStageProgress> stages=new ArrayList<>();
            for(var row:rows(c,"SELECT t.id,t.name,t.tokens,p.id AS topicId,p.name AS topicName "
                    + "FROM tasks t JOIN topics p ON p.id=t.topic "
                    + "WHERE p.subject=? AND p.grade=? AND p.semester=? "
                    + "ORDER BY p.number,t.stage_number,t.id",scope.subjectId(),grade,scope.semesterId())) {
                int stageId=integer(row,"id");
                StageAssessment assessment=stageAssessment(c,studentId,scope,ActiveStageType.CENTRAL,stageId,grade);
                stages.add(new StudentStageProgress(ActiveStageType.CENTRAL,stageId,integer(row,"topicId"),
                        (String)row.get("topicName"),(String)row.get("name"),integer(row,"tokens"),
                        assessment.status(),assessment.earned(),activeType==ActiveStageType.CENTRAL && activeId==stageId));
            }
            for(var row:rows(c,"SELECT t.id,t.name,t.tokens,p.id AS topicId,p.name AS topicName "
                    + "FROM flexible_tasks t LEFT JOIN flexible_task_topics m ON m.flexible_task=t.id "
                    + "LEFT JOIN flexible_topics p ON p.id=m.flexible_topic AND p.owner_teacher=t.owner_teacher "
                    + "AND p.subject=t.subject AND p.class=t.class AND p.semester=t.semester AND p.grade=t.grade "
                    + "WHERE t.owner_teacher=? AND t.subject=? AND t.class=? AND t.semester=? AND t.grade=? "
                    + "ORDER BY p.id IS NULL,p.id,t.id",scope.teacherId(),scope.subjectId(),scope.classId(),scope.semesterId(),grade)) {
                int stageId=integer(row,"id");
                StageAssessment assessment=stageAssessment(c,studentId,scope,ActiveStageType.FLEXIBLE,stageId,grade);
                Integer topicId=row.get("topicId")==null?null:integer(row,"topicId");
                stages.add(new StudentStageProgress(ActiveStageType.FLEXIBLE,stageId,topicId,(String)row.get("topicName"),
                        (String)row.get("name"),integer(row,"tokens"),assessment.status(),assessment.earned(),
                        activeType==ActiveStageType.FLEXIBLE && activeId==stageId));
            }
            return new StudentProgressDetail(studentId,studentName,scope.subjectId(),scope.semesterId(),List.copyOf(stages));
        });
    }
    public StageAssessment setStageAssessment(Actor actor, int studentId, Scope scope,
                                              ActiveStageType stageType, int stageId,
                                              AssessmentStatus status) throws SQLException {
        if(actor==null || actor.admin()) throw error(403,"forbidden","Teacher required for student assessment.");
        if(status==null) throw error(400,"invalid_input","Assessment status is required.");
        transaction(c -> {
            if(stageType==ActiveStageType.FLEXIBLE && status==AssessmentStatus.PASSED) {
                if(!actor.admin() && actor.teacherId()!=scope.teacherId())
                    throw error(403,"forbidden","This context belongs to another teacher.");
                if(number(c,"SELECT COUNT(*) FROM curriculum_completion_transfers WHERE student=? AND source_task=?",studentId,stageId)>0)
                    throw error(409,"context_conflict","Transferred flexible completion cannot be reactivated.");
            }
            int grade=validateAssessmentStage(c,actor,studentId,scope,stageType,stageId);
            if(status==AssessmentStatus.PASSED) {
                if(stageType==ActiveStageType.CENTRAL) {
                    setCentralStatus(c,studentId,stageId,Task.STATUS_COMPLETED);
                    write(c,"DELETE FROM student_curriculum_stage_assessments WHERE student=? AND subject=? AND semester=? AND stage_type=? AND stage_id=?",
                            studentId,scope.subjectId(),scope.semesterId(),stageType.name(),stageId);
                    write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=? AND semester=? AND central_task=?",
                            studentId,scope.subjectId(),scope.semesterId(),stageId);
                } else {
                    write(c,"INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?) ON CONFLICT(student,flexible_task) DO NOTHING",studentId,stageId);
                    write(c,"DELETE FROM student_curriculum_stage_assessments WHERE student=? AND subject=? AND semester=? AND stage_type=? AND stage_id=?",
                            studentId,scope.subjectId(),scope.semesterId(),stageType.name(),stageId);
                    write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=? AND semester=? AND flexible_task=?",
                            studentId,scope.subjectId(),scope.semesterId(),stageId);
                }
            } else {
                write(c,"INSERT INTO student_curriculum_stage_assessments(student,subject,semester,stage_type,stage_id,status,last_updated) VALUES(?,?,?,?,?,?,CURRENT_TIMESTAMP) "
                        + "ON CONFLICT(student,subject,semester,stage_type,stage_id) DO UPDATE SET status=excluded.status,last_updated=CURRENT_TIMESTAMP",
                        studentId,scope.subjectId(),scope.semesterId(),stageType.name(),stageId,status.name());
            }
            return null;
        });
        if(stageType==ActiveStageType.CENTRAL && status==AssessmentStatus.PASSED) {
            Student student=Student.get(studentId);Task task=Task.get(stageId);
            if(student!=null && task!=null) student.applyTaskStatusCache(task,Task.STATUS_COMPLETED);
        }
        return stageAssessment(actor,studentId,scope,stageType,stageId);
    }
    private static void putActiveCentral(Connection c,int studentId,CentralTask task) throws SQLException {
        write(c,"INSERT INTO student_active_curriculum_stages(student,subject,semester,central_task,flexible_task,last_updated) VALUES(?,?,?,?,NULL,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(student,subject) DO UPDATE SET semester=excluded.semester,central_task=excluded.central_task,flexible_task=NULL,last_updated=CURRENT_TIMESTAMP",
                studentId,task.subjectId(),task.semesterId(),task.id());
    }
    private static void putActiveFlexible(Connection c,int studentId,FlexibleTask task) throws SQLException {
        write(c,"INSERT INTO student_active_curriculum_stages(student,subject,semester,central_task,flexible_task,last_updated) VALUES(?,?,?,NULL,?,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(student,subject) DO UPDATE SET semester=excluded.semester,central_task=NULL,flexible_task=excluded.flexible_task,last_updated=CURRENT_TIMESTAMP",
                studentId,task.subjectId(),task.semesterId(),task.id());
    }
    private static void clearActiveCentral(Connection c,int studentId,CentralTask task) throws SQLException {
        write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=? AND central_task=?",studentId,task.subjectId(),task.id());
    }
    private static void resetCentralInProgress(Connection c,int studentId,int subjectId) throws SQLException {
        write(c,"UPDATE taskstats SET status=0,last_updated=CURRENT_TIMESTAMP WHERE student=? AND status=1 AND task IN("
                + "SELECT t.id FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=?)",studentId,subjectId);
    }
    private static void setCentralStatus(Connection c,int studentId,int taskId,int status) throws SQLException {
        write(c,"INSERT INTO taskstats(student,task,status,last_updated) VALUES(?,?,?,CURRENT_TIMESTAMP) "
                + "ON CONFLICT(student,task) DO UPDATE SET status=excluded.status,last_updated=CURRENT_TIMESTAMP",studentId,taskId,status);
    }
    public ActiveStage activeStage(int studentId,int subjectId) throws SQLException {
        return transaction(c->{
            var rows=rows(c,"SELECT a.subject,a.semester,a.central_task,a.flexible_task,ct.name AS centralName,ft.name AS flexibleName "
                    + "FROM student_active_curriculum_stages a LEFT JOIN tasks ct ON ct.id=a.central_task LEFT JOIN flexible_tasks ft ON ft.id=a.flexible_task "
                    + "WHERE a.student=? AND a.subject=?",studentId,subjectId);
            if(rows.isEmpty())return null;
            var row=rows.get(0);
            if(row.get("central_task")!=null)return new ActiveStage(ActiveStageType.CENTRAL,integer(row,"central_task"),integer(row,"subject"),integer(row,"semester"),(String)row.get("centralName"));
            return new ActiveStage(ActiveStageType.FLEXIBLE,integer(row,"flexible_task"),integer(row,"subject"),integer(row,"semester"),(String)row.get("flexibleName"));
        });
    }
    public void activateCentralStage(int studentId,int taskId) throws SQLException {
        CentralTask activated=transaction(c->{
            CentralTask task=centralTask(c,taskId);
            var assignment=assigned(c,studentId,task.subjectId(),task.semesterId());
            if(integer(assignment,"grade")!=task.grade()) throw error(403,"forbidden","Task does not belong to the student's assigned curriculum grade.");
            Scope scope=assignmentScope(assignment,task.subjectId(),task.semesterId());
            authorize(c,new Actor(false,scope.teacherId()),scope,false);
            if(!CurriculumEnrollment.released(c,scope,task.id(),task.topicId()))
                throw error(403,"forbidden","This task has not been released for the student's class.");
            resetCentralInProgress(c,studentId,task.subjectId());
            putActiveCentral(c,studentId,task);
            setCentralStatus(c,studentId,task.id(),Task.STATUS_IN_PROGRESS);
            return task;
        });
        if(activated==null) throw error(404,"not_found","Requested curriculum object does not exist.");
        Student student=Student.get(studentId);
        Task cached=Task.get(activated.id());
        if(student!=null && cached!=null) student.selectOnlyTaskForSubject(cached);
    }
    public void activateFlexibleStage(int studentId,int taskId) throws SQLException {
        FlexibleTask activated=transaction(c->{
            var row=require(c,"SELECT t.*,p.flexible_topic AS topic FROM flexible_tasks t LEFT JOIN flexible_task_topics p ON p.flexible_task=t.id WHERE t.id=?",taskId);
            FlexibleTask task=task(row);
            var student=require(c,"SELECT class FROM students WHERE id=?",studentId);
            if(integer(student,"class")!=task.classId()) throw error(403,"forbidden","Student does not belong to this task's class.");
            int grade=requireAssignment(c,studentId,task.scope());
            if(grade!=task.grade()) throw error(409,"context_conflict","Context contains inconsistent historical grades.");
            if(!CurriculumEnrollment.flexibleReleased(c,task.scope(),task.id(),row.get("topic")==null?null:integer(row,"topic")))
                throw error(403,"forbidden","This flexible task has not been released for the student's class.");
            resetCentralInProgress(c,studentId,task.subjectId());
            putActiveFlexible(c,studentId,task);
            return task;
        });
        if(activated==null) throw error(404,"not_found","Requested curriculum object does not exist.");
        Student student=Student.get(studentId);
        Subject subject=Subject.get(activated.subjectId());
        if(student!=null && subject!=null) student.clearSelectedTasksForSubject(subject);
    }
    public void deactivateFlexibleStage(int studentId,int taskId) throws SQLException {
        FlexibleTask deactivated=transaction(c->{
            FlexibleTask task=task(require(c,"SELECT * FROM flexible_tasks WHERE id=?",taskId));
            var student=require(c,"SELECT class FROM students WHERE id=?",studentId);
            if(integer(student,"class")!=task.classId()) throw error(403,"forbidden","Student does not belong to this task's class.");
            int grade=requireAssignment(c,studentId,task.scope());
            if(grade!=task.grade()) throw error(409,"context_conflict","Context contains inconsistent historical grades.");
            write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=? AND flexible_task=?",studentId,task.subjectId(),task.id());
            return task;
        });
        if(deactivated==null) throw error(404,"not_found","Requested curriculum object does not exist.");
    }
    public void clearActiveStage(int studentId,int subjectId) throws SQLException {
        transaction(c->{write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=?",studentId,subjectId);return null;});
    }
    public void changeCentralStageStatus(int studentId,int taskId,int newStatus) throws SQLException {
        if(newStatus==Task.STATUS_IN_PROGRESS) {
            activateCentralStage(studentId,taskId);
            return;
        }
        CentralTask changed=transaction(c->{
            CentralTask task=centralTask(c,taskId);
            setCentralStatus(c,studentId,task.id(),newStatus);
            clearActiveCentral(c,studentId,task);
            return task;
        });
        if(changed==null) throw error(404,"not_found","Requested curriculum object does not exist.");
        Student student=Student.get(studentId);
        Task cached=Task.get(changed.id());
        if(student!=null && cached!=null) student.applyTaskStatusCache(cached,newStatus);
    }
    /** Minimal class roster for explicit administrative assignment; no credentials. */
    public List<Map<String,Object>> students(Actor actor,Scope scope) throws SQLException {
        admin(actor);
        return transaction(c->{authorize(c,new Actor(false,scope.teacherId()),scope,false);
            return rows(c,"SELECT s.id,s.first_name,s.last_name,a.teacher AS teacherId,a.class AS classId FROM students s "
                    + "LEFT JOIN student_curriculum_contexts a ON a.student=s.id AND a.subject=? AND a.semester=? "
                    + "WHERE s.class=? ORDER BY s.last_name,s.first_name,s.id",scope.subjectId(),scope.semesterId(),scope.classId());});
    }
    /** Canonical teacher read model for one managed curriculum context; no legacy teacher fallbacks. */
    public List<Map<String,Object>> teacherRoster(Actor actor,Scope scope) throws SQLException {
        return transaction(c->{
            int grade=authorizeTeacherRoster(c,actor,scope);
            var roster=rows(c,"SELECT DISTINCT s.id,s.first_name AS firstName,s.last_name AS lastName "
                    + "FROM student_curriculum_contexts x JOIN students s ON s.id=x.student "
                    + "WHERE x.teacher=? AND x.class=? AND x.subject=? AND x.semester=? AND x.grade=? AND s.active=1 "
                    + "ORDER BY s.last_name,s.first_name,s.id",
                    scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),grade);
            for(var student:roster) {
                int studentId=integer(student,"id");
                student.put("name",student.get("firstName")+" "+student.get("lastName"));
                student.put("activeStage",teacherRosterActiveStage(c,studentId,scope));
                student.put("signals",teacherRosterSignals(c,studentId,scope));
            }
            return roster;
        });
    }
    private static int authorizeTeacherRoster(Connection c,Actor actor,Scope scope) throws SQLException {
        if(!actor.admin() && actor.teacherId()!=scope.teacherId()) throw error(403,"forbidden","This context belongs to another teacher.");
        require(c,"SELECT id FROM teachers WHERE id=?",scope.teacherId());
        require(c,"SELECT id FROM subjects WHERE id=?",scope.subjectId());
        require(c,"SELECT id FROM semesters WHERE id=?",scope.semesterId());
        var classRow=require(c,"SELECT grade,COALESCE(active,1) AS active FROM classes WHERE id=?",scope.classId());
        if(scope.classId()==SchoolClass.UNASSIGNED_CLASS_ID || integer(classRow,"grade")==0 || integer(classRow,"active")==0)
            throw error(409,"context_unassigned","This class is not available for a current curriculum context.");
        int grade=integer(classRow,"grade");
        if(individualSubject(c,scope.subjectId())) {
            if(number(c,"SELECT COUNT(*) FROM student_curriculum_contexts WHERE teacher=? AND class=? AND subject=? AND semester=? AND grade=?",
                    scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),grade)==0)
                throw error(403,"forbidden","Teacher must be assigned to this canonical curriculum context.");
        } else if(number(c,"SELECT COUNT(*) FROM curriculum_class_teachers WHERE semester=? AND class=? AND subject=? AND teacher=?",
                scope.semesterId(),scope.classId(),scope.subjectId(),scope.teacherId())==0) {
            throw error(403,"forbidden","Teacher must be assigned to this canonical curriculum context.");
        }
        return grade;
    }
    private static Map<String,Object> teacherRosterActiveStage(Connection c,int studentId,Scope scope) throws SQLException {
        var stages=rows(c,"SELECT a.central_task,a.flexible_task,ct.name AS centralName,ft.name AS flexibleName "
                + "FROM student_active_curriculum_stages a LEFT JOIN tasks ct ON ct.id=a.central_task LEFT JOIN flexible_tasks ft ON ft.id=a.flexible_task "
                + "WHERE a.student=? AND a.subject=? AND a.semester=?",
                studentId,scope.subjectId(),scope.semesterId());
        if(stages.isEmpty()) return null;
        var stage=stages.get(0);
        Map<String,Object> out=new LinkedHashMap<>();
        if(stage.get("central_task")!=null) {
            out.put("type",ActiveStageType.CENTRAL.name());
            out.put("taskId",integer(stage,"central_task"));
            out.put("name",stage.get("centralName"));
        } else {
            out.put("type",ActiveStageType.FLEXIBLE.name());
            out.put("taskId",integer(stage,"flexible_task"));
            out.put("name",stage.get("flexibleName"));
        }
        return out;
    }
    private static Map<String,Object> teacherRosterSignals(Connection c,int studentId,Scope scope) throws SQLException {
        Set<String> requests=new HashSet<>();
        for(var row:rows(c,"SELECT request_type FROM student_subject_requests WHERE student=? AND subject=? AND semester=?",
                studentId,scope.subjectId(),scope.semesterId())) requests.add(String.valueOf(row.get("request_type")));
        Map<String,Object> signals=new LinkedHashMap<>();
        signals.put("help",requests.contains("HELP"));
        signals.put("partner",requests.contains("PARTNER"));
        signals.put("experiment",requests.contains("EXPERIMENT"));
        signals.put("exam",requests.contains("EXAM"));
        return signals;
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
        return Map.of("semesterId",scope.semesterId(),"centralTokens",central,"flexibleTokens",flexible,"totalTokens",central+flexible,
                "completedCentralTasks",completedCentralTasks,"completedFlexibleTasks",completedFlexibleTasks);
    }
    /** Assigned total for staff; both their scope access and the student's explicit assignment apply. */
    public Map<String,Object> progress(Actor actor, int studentId, Scope scope) throws SQLException {
        return transaction(c -> {authorize(c,actor,scope,false);return progress(c,studentId,scope);});
    }
    /** Central task status changes must follow the student's managed curriculum assignment. */
    public void authorizeCentralTaskChange(Actor actor, int studentId, int taskId, boolean requireRelease) throws SQLException {
        transaction(c -> {
            var task=require(c,"SELECT t.id,p.subject,p.grade,p.semester,p.id AS topic FROM tasks t JOIN topics p ON p.id=t.topic WHERE t.id=?",taskId);
            Object semester=task.get("semester");
            if(semester==null) return null;
            int subject=integer(task,"subject"), semesterId=integer(task,"semester"), grade=integer(task,"grade");
            var assignment=assigned(c,studentId,subject,semesterId);
            if(integer(assignment,"grade")!=grade) throw error(403,"forbidden","Task does not belong to the student's assigned curriculum grade.");
            var scope=assignmentScope(assignment,subject,semesterId);
            authorize(c,actor,scope,false);
            if(requireRelease && !CurriculumEnrollment.released(c,scope,taskId,integer(task,"topic")))
                throw error(403,"forbidden","This task has not been released for the student's class.");
            return null;
        });
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

    /** Plans and earned values are one consistent read of the assigned historical context. */
    public Map<String,Object> studentCatalog(User user,int subject,int semester) throws SQLException {
        if(user==null || user==User.ANONYMOUS) throw error(401,"unauthorized","Please sign in.");
        if(!user.isStudent()) throw error(403,"forbidden","Student session required.");
        int studentId=user.asStudent().getId();
        return transaction(c->{
            require(c,"SELECT id FROM students WHERE id=?",studentId);
            var assignment=assigned(c,studentId,subject,semester);
            var scope=assignmentScope(assignment,subject,semester);
            int grade=integer(assignment,"grade");
            var earned=progress(c,studentId,scope);
            Set<Integer> centralDone=new HashSet<>(),flexibleDone=new HashSet<>();
            for(Object entry:(List<?>)earned.get("completedCentralTasks")) centralDone.add(((CompletedCentralTask)entry).id());
            for(Object entry:(List<?>)earned.get("completedFlexibleTasks")) flexibleDone.add(((CompletedFlexibleTask)entry).id());
            var activeRows=rows(c,"SELECT a.subject,a.semester,a.central_task,a.flexible_task,ct.name AS centralName,ft.name AS flexibleName "
                    + "FROM student_active_curriculum_stages a LEFT JOIN tasks ct ON ct.id=a.central_task LEFT JOIN flexible_tasks ft ON ft.id=a.flexible_task "
                    + "WHERE a.student=? AND a.subject=? AND a.semester=?",studentId,subject,semester);
            ActiveStage activeStage=null;
            if(!activeRows.isEmpty()) {
                var row=activeRows.get(0);
                activeStage=row.get("central_task")!=null
                        ? new ActiveStage(ActiveStageType.CENTRAL,integer(row,"central_task"),integer(row,"subject"),integer(row,"semester"),(String)row.get("centralName"))
                        : new ActiveStage(ActiveStageType.FLEXIBLE,integer(row,"flexible_task"),integer(row,"subject"),integer(row,"semester"),(String)row.get("flexibleName"));
            }
            var centralTasks=rows(c,"SELECT t.id,t.name,t.tokens,t.niveau,p.id AS topicId,p.name AS topicName FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=? ORDER BY p.number,t.id",subject,grade,semester);
            var visibleCentral=new ArrayList<Map<String,Object>>();
            for(var task:centralTasks) {
                boolean active=CurriculumEnrollment.released(c,scope,integer(task,"id"),integer(task,"topicId"));
                task.put("active",active);
                if(active || centralDone.contains(integer(task,"id")))visibleCentral.add(task);
            }
            centralTasks=visibleCentral;
            var visibleTopics=new ArrayList<Map<String,Object>>();
            for(var topic:rows(c,"SELECT id,name,number FROM topics WHERE subject=? AND grade=? AND semester=? ORDER BY number,id",subject,grade,semester)) {
                int topicId=integer(topic,"id");
                if(CurriculumEnrollment.topicReleased(c,scope,topicId) || centralTasks.stream().anyMatch(t->integer(t,"topicId")==topicId))visibleTopics.add(topic);
            }
            var flexibleTasks=plannedFlexibleTasks(c,scope);
            var visibleFlexibleTasks=new ArrayList<Map<String,Object>>();
            for(var task:flexibleTasks) {
                int taskId=integer(task,"id");
                boolean active=CurriculumEnrollment.flexibleReleased(c,scope,taskId,task.get("topicId")==null?null:integer(task,"topicId"));
                task.put("active",active);
                if(active || flexibleDone.contains(taskId))visibleFlexibleTasks.add(task);
            }
            flexibleTasks=visibleFlexibleTasks;
            var flexibleTopics=flexibleTopics(c,scope);
            var visibleFlexibleTopics=new ArrayList<Map<String,Object>>();
            for(var topic:flexibleTopics) {
                int topicId=integer(topic,"id");
                if(CurriculumEnrollment.flexibleTopicReleased(c,scope,topicId) || flexibleTasks.stream().anyMatch(t->t.get("topicId")!=null && integer(t,"topicId")==topicId))
                    visibleFlexibleTopics.add(topic);
            }
            ActiveStage finalActiveStage=activeStage;
            centralTasks.forEach(t->{int taskId=integer(t,"id");boolean completed=centralDone.contains(taskId);
                t.put("completed",completed);
                t.put("inProgress",!completed && finalActiveStage!=null && finalActiveStage.type()==ActiveStageType.CENTRAL && finalActiveStage.taskId()==taskId);
            });
            flexibleTasks.forEach(t->{int taskId=integer(t,"id");boolean completed=flexibleDone.contains(taskId);
                t.put("completed",completed);
                t.put("inProgress",!completed && finalActiveStage!=null && finalActiveStage.type()==ActiveStageType.FLEXIBLE && finalActiveStage.taskId()==taskId);
            });
            long centralPlanned=central(c,subject,grade,semester),flexiblePlanned=flexible(c,scope);
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("semesterId",semester);
            out.put("activeStage",activeStage==null?null:Map.of("type",activeStage.type().name(),"taskId",activeStage.taskId(),"subjectId",activeStage.subjectId(),"semesterId",activeStage.semesterId(),"name",activeStage.name()));
            out.put("centralTopics",visibleTopics);
            out.put("centralTasks",centralTasks);
            out.put("flexibleTopics",visibleFlexibleTopics);
            out.put("flexibleTasks",flexibleTasks);
            out.put("planned",Map.of("centralTokens",centralPlanned,"flexibleTokens",flexiblePlanned,"totalTokens",centralPlanned+flexiblePlanned,"regularLimit",REGULAR_LIMIT,"hardLimit",HARD_LIMIT,"unreleasedCentralTokens",centralPlanned-centralTasks.stream().mapToLong(t->integer(t,"tokens")).sum()));
            out.put("progress",earned);
            return out;
        });
    }

    /** Fresh catalog restricted to the actor's assignments; no credentials or student data. */
    public Map<String,Object> catalog(Actor actor) throws SQLException {
        return transaction(c->{Map<String,Object> out=new LinkedHashMap<>();out.put("admin",actor.admin());out.put("enrollmentEnabled",true);out.put("teacherId",actor.teacherId());
            out.put("subjects",rows(c,actor.admin()?"SELECT id,name FROM subjects ORDER BY name":"SELECT s.id,s.name FROM subjects s JOIN teacher_subjects t ON t.subject_id=s.id WHERE t.teacher_id=? ORDER BY s.name",actor.admin()?new Object[]{}:new Object[]{actor.teacherId()}));
            out.put("classes",rows(c,actor.admin()?"SELECT id,label,grade FROM classes WHERE active=1 AND id<>0 ORDER BY grade,label":"SELECT s.id,s.label,s.grade FROM classes s JOIN teacher_classes t ON t.class_id=s.id WHERE t.teacher_id=? AND s.active=1 AND s.id<>0 ORDER BY s.grade,s.label",actor.admin()?new Object[]{}:new Object[]{actor.teacherId()}));
            out.put("semesters",rows(c,"SELECT id,label,school_year FROM semesters ORDER BY school_year,position"));
            if(!actor.admin()) out.put("contexts",teacherContexts(c,actor.teacherId()));
            if(actor.admin()) out.put("teachers",rows(c,"SELECT id,first_name,last_name FROM teachers ORDER BY id"));
            return out;});
    }
    private static List<Map<String,Object>> teacherContexts(Connection c,int teacher) throws SQLException {
        var contexts=rows(c,
                "SELECT DISTINCT ct.semester AS semesterId,sem.label AS semesterLabel,CASE WHEN y.current_semester=sem.id THEN 1 ELSE 0 END AS activeSemester,"
                        + "cl.id AS classId,cl.label AS classLabel,cl.grade AS grade,s.id AS subjectId,s.name AS subjectName,"
                        + "COALESCE(t.mode,'REGULAR') AS subjectMode,t.assignment_group AS assignmentGroup "
                        + "FROM curriculum_class_teachers ct "
                        + "JOIN semesters sem ON sem.id=ct.semester JOIN school_years y ON y.id=sem.school_year "
                        + "JOIN classes cl ON cl.id=ct.class JOIN subjects s ON s.id=ct.subject "
                        + "LEFT JOIN curriculum_subject_types t ON t.subject=ct.subject "
                        + "WHERE ct.teacher=? AND cl.id<>0 AND cl.grade<>0 AND cl.active=1 AND COALESCE(t.mode,'REGULAR')='REGULAR' "
                        + "UNION "
                        + "SELECT DISTINCT sc.semester AS semesterId,sem.label AS semesterLabel,CASE WHEN y.current_semester=sem.id THEN 1 ELSE 0 END AS activeSemester,"
                        + "cl.id AS classId,cl.label AS classLabel,cl.grade AS grade,s.id AS subjectId,s.name AS subjectName,"
                        + "COALESCE(t.mode,'REGULAR') AS subjectMode,t.assignment_group AS assignmentGroup "
                        + "FROM student_curriculum_contexts sc "
                        + "JOIN semesters sem ON sem.id=sc.semester JOIN school_years y ON y.id=sem.school_year "
                        + "JOIN classes cl ON cl.id=sc.class JOIN subjects s ON s.id=sc.subject "
                        + "LEFT JOIN curriculum_subject_types t ON t.subject=sc.subject "
                        + "WHERE sc.teacher=? AND cl.id<>0 AND cl.grade<>0 AND cl.active=1 AND COALESCE(t.mode,'REGULAR')='INDIVIDUAL' "
                        + "ORDER BY semesterId,grade,classLabel,subjectName",teacher,teacher);
        contexts.forEach(row -> row.put("activeSemester",integer(row,"activeSemester")==1));
        return contexts;
    }
    public Map<String,Object> centralStructure(Actor actor,int subject,int grade,int semester) throws SQLException {
        return transaction(c->{
            require(c,"SELECT id FROM subjects WHERE id=?",subject);require(c,"SELECT id FROM semesters WHERE id=?",semester);
            Map<String,Object> out=new LinkedHashMap<>();
            out.put("topics",rows(c,"SELECT id,name,number FROM topics WHERE subject=? AND grade=? AND semester=? ORDER BY number,id",subject,grade,semester));
            out.put("tasks",rows(c,"SELECT t.id,t.topic,t.stage_number AS stageNumber,t.name,t.tokens,t.niveau FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=? ORDER BY p.number,t.stage_number,t.id",subject,grade,semester));
            out.put("centralTokens",central(c,subject,grade,semester));out.put("regularLimit",REGULAR_LIMIT);out.put("hardLimit",HARD_LIMIT);return out;});
    }
}
