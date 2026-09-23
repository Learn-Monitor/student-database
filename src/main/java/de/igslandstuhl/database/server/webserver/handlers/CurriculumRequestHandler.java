package de.igslandstuhl.database.server.webserver.handlers;

import de.igslandstuhl.database.api.TaskLevel;
import de.igslandstuhl.database.api.SchoolYear;
import de.igslandstuhl.database.api.curriculum.*;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;
import java.sql.SQLException;
import java.util.*;

/** Existing JSON POST/session conventions, with authorization repeated in the service. */
public final class CurriculumRequestHandler {
    private CurriculumRequestHandler() {}
    public static void registerHandlers() {
        for(String path:List.of("/curriculum-catalog","/curriculum-structure","/curriculum-budget","/curriculum-progress",
                "/flexible-tasks","/add-flexible-task","/edit-flexible-task","/complete-flexible-task",
                "/flexible-curriculum-structure","/add-flexible-topic","/rename-flexible-topic","/curriculum-releases","/set-curriculum-release",
                "/curriculum-teacher-roster"))
            HttpHandler.registerPostRequestHandler(path,AccessLevel.TEACHER,CurriculumRequestHandler::handle);
        for(String path:List.of("/rename-topic","/edit-task","/add-curriculum-topic","/add-curriculum-task",
                "/central-curriculum-overview","/preview-central-curriculum-import","/import-central-curriculum",
                "/curriculum-students","/assign-curriculum-context","/curriculum-transfer-preview","/transfer-curriculum-context","/curriculum-enrollment-catalog","/set-curriculum-subject-type","/assign-grade-curriculum","/assign-individual-grade-teacher","/curriculum-wpf-roster","/assign-curriculum-wpf","/create-curriculum-semester","/activate-curriculum-semester"))
            HttpHandler.registerPostRequestHandler(path,AccessLevel.ADMIN,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/set-curriculum-stage-assessment",AccessLevel.TEACHER,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/curriculum-student-progress-detail",AccessLevel.TEACHER,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/my-curriculum-progress",AccessLevel.STUDENT,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/my-curriculum-catalog",AccessLevel.STUDENT,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/my-curriculum-subjects",AccessLevel.STUDENT,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/begin-flexible-task",AccessLevel.STUDENT,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/cancel-flexible-task",AccessLevel.STUDENT,CurriculumRequestHandler::handle);
    }
    private static int integer(APIPostRequest rq,String key) {
        return integer(rq.getJson().get(key),key);
    }
    private static int integer(Object raw,String key) {
        if(!(raw instanceof Number) && !(raw instanceof String text && text.matches("[0-9]+")))
            throw new CurriculumException(400,"invalid_input",key+" must be an integer.");
        try { int result = new java.math.BigDecimal(raw.toString()).intValueExact();
            if (result < 0) throw new ArithmeticException();
            return result; }
        catch(ArithmeticException e) { throw new CurriculumException(400,"invalid_input",key+" is out of range."); }
    }
    private static boolean bool(APIPostRequest rq,String key) {
        if(!(rq.getJson().get(key) instanceof Boolean b)) throw new CurriculumException(400,"invalid_input",key+" must be boolean.");
        return b;
    }
    private static List<?> list(APIPostRequest rq,String key) {
        if(!(rq.getJson().get(key) instanceof List<?> values))throw new CurriculumException(400,"invalid_input",key+" must be a list.");
        return values;
    }
    private static String name(APIPostRequest rq) {
        Object value=rq.getJson().get("name");
        if(!(value instanceof String s)) throw new CurriculumException(400,"invalid_input","name must be a string.");
        return s;
    }
    private static String string(APIPostRequest rq,String key) {
        Object value=rq.getJson().get(key);
        if(!(value instanceof String s)) throw new CurriculumException(400,"invalid_input",key+" must be a string.");
        return s;
    }
    private static Integer optionalTopic(APIPostRequest rq) {
        return !rq.containsKey("topicId") || rq.getJson().get("topicId")==null ? null : integer(rq,"topicId");
    }
    private static Curriculum.Scope scope(APIPostRequest rq,Curriculum.Actor actor) {
        return scope(rq,actor,false);
    }
    private static Curriculum.Scope scope(APIPostRequest rq,Curriculum.Actor actor,boolean progress) {
        int teacher=actor.admin()?integer(rq,"teacherId"):actor.teacherId();
        if(!actor.admin() && rq.containsKey("teacherId") && integer(rq,"teacherId")!=teacher)
            throw new CurriculumException(403,"forbidden","Cannot impersonate another teacher.");
        return new Curriculum.Scope(teacher,integer(rq,"subjectId"),integer(rq,"classId"),progress?effectiveSemesterId(rq):integer(rq,"semesterId"));
    }
    private static int effectiveSemesterId(APIPostRequest rq) {
        if (rq.containsKey("semesterId")) return integer(rq,"semesterId");
        SchoolYear year=SchoolYear.getCurrentYear(false);
        var semester=year==null?null:year.getCurrentSemester();
        if (semester==null)
            throw new CurriculumException(409,"current_semester_unavailable","No current semester is configured.");
        return semester.getId();
    }
    public static PostResponse handle(APIPostRequest rq) {
        try {
            if (rq.getJson() == null) throw new CurriculumException(400,"invalid_input","JSON object required.");
            Curriculum service=Curriculum.current();
            CentralCurriculumImport centralImport=new CentralCurriculumImport(service);
            if(rq.getPath().equals("/my-curriculum-subjects")) {
                if(!rq.getJson().isEmpty()) throw new CurriculumException(400,"invalid_input","No client scope is accepted.");
                if(rq.getUser()==null || rq.getUser()==de.igslandstuhl.database.api.User.ANONYMOUS)
                    throw new CurriculumException(401,"unauthorized","Please sign in.");
                if(!rq.getUser().isStudent()) throw new CurriculumException(403,"forbidden","Student session required.");
                return PostResponse.json(service.studentCurrentSubjects(rq.getUser().asStudent().getId()),rq);
            }
            if(rq.getPath().equals("/begin-flexible-task") || rq.getPath().equals("/cancel-flexible-task")) {
                if(rq.getJson().keySet().stream().anyMatch(key -> !key.equals("taskId")))
                    throw new CurriculumException(400,"invalid_input","Only taskId is accepted.");
                if(rq.getUser()==null || rq.getUser()==de.igslandstuhl.database.api.User.ANONYMOUS)
                    throw new CurriculumException(401,"unauthorized","Please sign in.");
                if(!rq.getUser().isStudent())
                    throw new CurriculumException(403,"forbidden","Student session required.");
                int studentId=rq.getUser().asStudent().getId(), taskId=integer(rq,"taskId");
                if(rq.getPath().equals("/begin-flexible-task"))service.activateFlexibleStage(studentId,taskId);
                else service.deactivateFlexibleStage(studentId,taskId);
                return PostResponse.json(Map.of("ok",true),rq);
            }
            if(rq.getPath().equals("/my-curriculum-progress") || rq.getPath().equals("/my-curriculum-catalog")) {
                if(rq.containsKey("studentId") || rq.containsKey("teacherId") || rq.containsKey("classId") || rq.containsKey("grade"))
                    throw new CurriculumException(400,"invalid_input","Student context is derived from the session and assignment.");
                // Preserve session rejection before resolving optional server-side context.
                if(rq.getUser()==null || rq.getUser()==de.igslandstuhl.database.api.User.ANONYMOUS)
                    throw new CurriculumException(401,"unauthorized","Please sign in.");
                if(!rq.getUser().isStudent())
                    throw new CurriculumException(403,"forbidden","Student session required.");
                int subject=integer(rq,"subjectId"),semester=effectiveSemesterId(rq);
                if(rq.getPath().equals("/my-curriculum-catalog"))
                    return PostResponse.jsonWithNulls(service.studentCatalog(rq.getUser(),subject,semester),rq);
                return PostResponse.json(service.studentProgress(rq.getUser(),subject,semester),rq);
            }
            if(rq.getPath().equals("/set-curriculum-stage-assessment")) {
                Set<String> allowed=Set.of("studentId","subjectId","classId","semesterId","stageType","stageId","status");
                if(!allowed.containsAll(rq.getJson().keySet()) || rq.getJson().size()!=allowed.size())
                    throw new CurriculumException(400,"invalid_input","Assessment payload contains unexpected or missing fields.");
                Curriculum.Actor actor=Curriculum.Actor.from(rq.getUser());
                if(actor.admin()) throw new CurriculumException(403,"forbidden","Teacher required for student assessment.");
                Curriculum.StageAssessment assessment=service.setStageAssessment(actor,integer(rq,"studentId"),scope(rq,actor),
                        Curriculum.ActiveStageType.valueOf(string(rq,"stageType")),integer(rq,"stageId"),
                        Curriculum.AssessmentStatus.valueOf(string(rq,"status")));
                return PostResponse.json(Map.of("status",assessment.status().name(),"earned",assessment.earned()),rq);
            }
            if(rq.getPath().equals("/curriculum-student-progress-detail")) {
                Set<String> allowed=Set.of("studentId","subjectId","classId","semesterId");
                if(!allowed.containsAll(rq.getJson().keySet()) || rq.getJson().size()!=allowed.size())
                    throw new CurriculumException(400,"invalid_input","Student progress payload contains unexpected or missing fields.");
                Curriculum.Actor actor=Curriculum.Actor.from(rq.getUser());
                if(actor.admin()) throw new CurriculumException(403,"forbidden","Teacher session required.");
                return PostResponse.jsonWithNulls(service.studentProgressDetail(actor,integer(rq,"studentId"),scope(rq,actor)),rq);
            }
            Curriculum.Actor actor=Curriculum.Actor.from(rq.getUser());
            CurriculumEnrollment enrollment=new CurriculumEnrollment(service);
            Object result;
            boolean includeNulls=false;
            switch(rq.getPath()) {
                case "/curriculum-enrollment-catalog" -> result=enrollment.catalog(actor);
                case "/create-curriculum-semester" -> result=enrollment.createNextSemester(actor);
                case "/activate-curriculum-semester" -> {enrollment.activateSemester(actor,integer(rq,"semesterId"));result=Map.of("ok",true);}
                case "/set-curriculum-subject-type" -> {
                    if(rq.containsKey("mode")) {
                        Object group=rq.getJson().get("assignmentGroup");
                        enrollment.subjectType(actor,integer(rq,"subjectId"),String.valueOf(rq.getJson().get("mode")),group==null?null:String.valueOf(group));
                    } else enrollment.subjectType(actor,integer(rq,"subjectId"),bool(rq,"wpf"));
                    result=Map.of("ok",true);
                }
                case "/remove-grade-curriculum-subject" -> {enrollment.removeGradeSubject(actor,integer(rq,"grade"),integer(rq,"semesterId"),integer(rq,"subjectId"));result=Map.of("ok",true);}
                case "/assign-individual-grade-teacher" -> {
                    Set<String> allowed=Set.of("grade","semesterId","subjectId","teacherId");
                    if(!allowed.containsAll(rq.getJson().keySet()) || rq.getJson().size()!=allowed.size()) throw new CurriculumException(400,"invalid_input","Individual grade-teacher payload contains unexpected or missing fields.");
                    enrollment.assignIndividualGradeTeacher(actor,integer(rq,"grade"),integer(rq,"semesterId"),integer(rq,"subjectId"),integer(rq,"teacherId"));
                    result=Map.of("ok",true);
                }
                case "/assign-grade-curriculum" -> {
                    List<Integer> subjects=new ArrayList<>();for(Object value:list(rq,"subjectIds"))subjects.add(integer(value,"subjectId"));
                    List<CurriculumEnrollment.Teaching> teaching=new ArrayList<>();
                    for(Object value:list(rq,"teaching")) {
                        if(!(value instanceof Map<?,?> m))throw new CurriculumException(400,"invalid_input","Invalid teaching assignment.");
                        teaching.add(new CurriculumEnrollment.Teaching(integer(m.get("classId"),"classId"),integer(m.get("subjectId"),"subjectId"),integer(m.get("teacherId"),"teacherId")));
                    }
                    result=enrollment.assignGrade(actor,integer(rq,"grade"),integer(rq,"semesterId"),subjects,teaching);
                }
                case "/curriculum-wpf-roster" -> result=rq.containsKey("assignmentGroup")
                        ? enrollment.individualRoster(actor,integer(rq,"classId"),integer(rq,"semesterId"),String.valueOf(rq.getJson().get("assignmentGroup")))
                        : enrollment.wpfRoster(actor,integer(rq,"classId"),integer(rq,"semesterId"));
                case "/assign-curriculum-wpf" -> {
                    Set<String> allowed=Set.of("studentId","subjectId","classId","semesterId","assignmentGroup","expectedSubjectId");
                    if(!allowed.containsAll(rq.getJson().keySet()) || rq.getJson().size()!=allowed.size()) throw new CurriculumException(400,"invalid_input","Individual assignment payload contains unexpected or missing fields.");
                    enrollment.assignIndividual(actor,integer(rq,"studentId"),integer(rq,"subjectId"),integer(rq,"classId"),integer(rq,"semesterId"),String.valueOf(rq.getJson().get("assignmentGroup")),rq.getJson().get("expectedSubjectId")==null?null:integer(rq,"expectedSubjectId"));
                    result=Map.of("ok",true);
                }
                case "/curriculum-releases" -> result=enrollment.releases(actor,scope(rq,actor));
                case "/set-curriculum-release" -> {enrollment.release(actor,scope(rq,actor),
                        rq.containsKey("topicId")?integer(rq,"topicId"):null,
                        rq.containsKey("taskId")?integer(rq,"taskId"):null,
                        rq.containsKey("flexibleTopicId")?integer(rq,"flexibleTopicId"):null,
                        rq.containsKey("flexibleTaskId")?integer(rq,"flexibleTaskId"):null,
                        bool(rq,"active"));result=Map.of("ok",true);}

                case "/curriculum-transfer-preview" -> result=service.transferPreview(actor,integer(rq,"studentId"),scope(rq,actor));
                case "/transfer-curriculum-context" -> {
                    if(!actor.admin()) throw new CurriculumException(403,"forbidden","Administrator required.");
                    var target=scope(rq,actor);
                    var source=new Curriculum.Scope(integer(rq,"sourceTeacherId"),target.subjectId(),integer(rq,"sourceClassId"),target.semesterId());
                    if(!(rq.getJson().get("transfers") instanceof List<?> mappings))
                        throw new CurriculumException(400,"invalid_input","Explicit completion mappings required.");
                    List<Curriculum.Transfer> transfers=new ArrayList<>();
                    for(var entry:mappings) {
                        if(!(entry instanceof Map<?,?> mapping)) throw new CurriculumException(400,"invalid_input","Invalid transfer mapping.");
                        transfers.add(new Curriculum.Transfer(integer(mapping.get("sourceTaskId"),"sourceTaskId"),integer(mapping.get("targetTaskId"),"targetTaskId"),integer(mapping.get("tokens"),"tokens")));
                    }
                    service.transfer(actor,integer(rq,"studentId"),source,target,transfers);result=Map.of("ok",true);
                }
                case "/curriculum-students" -> result=service.students(actor,scope(rq,actor));
                case "/curriculum-teacher-roster" -> {result=service.teacherRoster(actor,scope(rq,actor));includeNulls=true;}
                case "/assign-curriculum-context" -> {service.assign(actor,integer(rq,"studentId"),scope(rq,actor));result=Map.of("ok",true);}
                case "/curriculum-catalog" -> result=service.catalog(actor);
                case "/curriculum-structure" -> result=service.centralStructure(actor,integer(rq,"subjectId"),integer(rq,"grade"),integer(rq,"semesterId"));
                case "/central-curriculum-overview" -> result=centralImport.overview(actor,integer(rq,"grade"),integer(rq,"semesterId"));
                case "/preview-central-curriculum-import" -> result=centralImport.preview(actor,integer(rq,"grade"),integer(rq,"semesterId"),string(rq,"csv"));
                case "/import-central-curriculum" -> result=centralImport.importCsv(actor,integer(rq,"grade"),integer(rq,"semesterId"),string(rq,"csv"));
                case "/curriculum-budget" -> result=service.budget(actor,scope(rq,actor));
                case "/curriculum-progress" -> result=service.progress(actor,integer(rq,"studentId"),scope(rq,actor,true));
                case "/flexible-tasks" -> result=service.list(actor,scope(rq,actor));
                case "/flexible-curriculum-structure" -> result=service.flexibleStructure(actor,scope(rq,actor));
                case "/add-flexible-topic" -> result=Map.of("id",service.createFlexibleTopic(actor,scope(rq,actor),name(rq)));
                case "/rename-flexible-topic" -> {service.renameFlexibleTopic(actor,integer(rq,"topicId"),name(rq));result=Map.of("ok",true);}
                case "/add-flexible-task" -> result=service.create(actor,scope(rq,actor),name(rq),integer(rq,"tokens"),optionalTopic(rq));
                case "/edit-flexible-task" -> result=service.edit(actor,integer(rq,"taskId"),name(rq),integer(rq,"tokens"),rq.containsKey("topicId"),optionalTopic(rq));
                case "/complete-flexible-task" -> {service.complete(actor,integer(rq,"taskId"),integer(rq,"studentId"));result=Map.of("ok",true);}
                case "/rename-topic" -> {service.renameTopic(actor,integer(rq,"topicId"),name(rq));result=Map.of("ok",true);}
                case "/edit-task" -> {service.editTask(actor,integer(rq,"taskId"),name(rq),integer(rq,"tokens"));result=Map.of("ok",true);}
                case "/add-curriculum-topic" -> result=Map.of("id",service.createTopic(actor,integer(rq,"subjectId"),integer(rq,"grade"),integer(rq,"semesterId"),integer(rq,"number"),name(rq)));
                case "/add-curriculum-task" -> {
                    if(!actor.admin()) throw new CurriculumException(403,"forbidden","Administrator required.");
                    int level=rq.containsKey("level") ? integer(rq,"level") : TaskLevel.LEVEL1.getNumber();
                    int stageNumber=rq.containsKey("stageNumber") ? integer(rq,"stageNumber") : 0;
                    result=Map.of("id",service.createCentralTask(integer(rq,"topicId"),name(rq),TaskLevel.get(level),stageNumber,integer(rq,"tokens")));
                }
                default -> throw new CurriculumException(404,"not_found","Unknown curriculum operation.");
            }
            return includeNulls ? PostResponse.jsonWithNulls(result,rq) : PostResponse.json(result,rq);
        } catch(CurriculumException e) {
            Map<String,Object> body=new LinkedHashMap<>();body.put("error",e.code);body.put("message",e.getMessage());
            if(!e.affectedContexts.isEmpty()) body.put("affectedContexts",e.affectedContexts);
            Status status=switch(e.status){case 400->Status.BAD_REQUEST;case 401->Status.UNAUTHORIZED;case 403->Status.FORBIDDEN;case 404->Status.NOT_FOUND;default->Status.CONFLICT;};
            return PostResponse.json(status,body,rq);
        } catch(IllegalArgumentException | com.google.gson.JsonParseException e) {
            return PostResponse.json(Status.BAD_REQUEST,Map.of("error","invalid_input","message","Invalid curriculum input."),rq);
        } catch(SQLException e) {
            return PostResponse.json(Status.INTERNAL_SERVER_ERROR,Map.of("error","database_error","message","Curriculum operation failed."),rq);
        }
    }
}
