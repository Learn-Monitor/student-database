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
                "/flexible-tasks","/add-flexible-task","/edit-flexible-task","/complete-flexible-task"))
            HttpHandler.registerPostRequestHandler(path,AccessLevel.TEACHER,CurriculumRequestHandler::handle);
        for(String path:List.of("/rename-topic","/edit-task","/add-curriculum-topic","/add-curriculum-task","/curriculum-students","/assign-curriculum-context","/curriculum-transfer-preview","/transfer-curriculum-context"))
            HttpHandler.registerPostRequestHandler(path,AccessLevel.ADMIN,CurriculumRequestHandler::handle);
        HttpHandler.registerPostRequestHandler("/my-curriculum-progress",AccessLevel.STUDENT,CurriculumRequestHandler::handle);
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
    private static String name(APIPostRequest rq) {
        Object value=rq.getJson().get("name");
        if(!(value instanceof String s)) throw new CurriculumException(400,"invalid_input","name must be a string.");
        return s;
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
            if(rq.getPath().equals("/my-curriculum-progress")) {
                if(rq.containsKey("studentId") || rq.containsKey("teacherId") || rq.containsKey("classId") || rq.containsKey("grade"))
                    throw new CurriculumException(400,"invalid_input","Student context is derived from the session and assignment.");
                // Preserve session rejection before resolving optional server-side context.
                if(rq.getUser()==null || rq.getUser()==de.igslandstuhl.database.api.User.ANONYMOUS)
                    throw new CurriculumException(401,"unauthorized","Please sign in.");
                if(!rq.getUser().isStudent())
                    throw new CurriculumException(403,"forbidden","Student session required.");
                return PostResponse.json(service.studentProgress(rq.getUser(),integer(rq,"subjectId"),effectiveSemesterId(rq)),rq);
            }
            Curriculum.Actor actor=Curriculum.Actor.from(rq.getUser());
            Object result;
            switch(rq.getPath()) {
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
                case "/assign-curriculum-context" -> {service.assign(actor,integer(rq,"studentId"),scope(rq,actor));result=Map.of("ok",true);}
                case "/curriculum-catalog" -> result=service.catalog(actor);
                case "/curriculum-structure" -> result=service.centralStructure(actor,integer(rq,"subjectId"),integer(rq,"grade"),integer(rq,"semesterId"));
                case "/curriculum-budget" -> result=service.budget(actor,scope(rq,actor));
                case "/curriculum-progress" -> result=service.progress(actor,integer(rq,"studentId"),scope(rq,actor,true));
                case "/flexible-tasks" -> result=service.list(actor,scope(rq,actor));
                case "/add-flexible-task" -> result=service.create(actor,scope(rq,actor),name(rq),integer(rq,"tokens"));
                case "/edit-flexible-task" -> result=service.edit(actor,integer(rq,"taskId"),name(rq),integer(rq,"tokens"));
                case "/complete-flexible-task" -> {service.complete(actor,integer(rq,"taskId"),integer(rq,"studentId"));result=Map.of("ok",true);}
                case "/rename-topic" -> {service.renameTopic(actor,integer(rq,"topicId"),name(rq));result=Map.of("ok",true);}
                case "/edit-task" -> {service.editTask(actor,integer(rq,"taskId"),name(rq),integer(rq,"tokens"));result=Map.of("ok",true);}
                case "/add-curriculum-topic" -> result=Map.of("id",service.createTopic(actor,integer(rq,"subjectId"),integer(rq,"grade"),integer(rq,"semesterId"),integer(rq,"number"),name(rq)));
                case "/add-curriculum-task" -> {
                    if(!actor.admin()) throw new CurriculumException(403,"forbidden","Administrator required.");
                    result=Map.of("id",service.createCentralTask(integer(rq,"topicId"),name(rq),TaskLevel.get(integer(rq,"level")),integer(rq,"tokens")));
                }
                default -> throw new CurriculumException(404,"not_found","Unknown curriculum operation.");
            }
            return PostResponse.json(result,rq);
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
