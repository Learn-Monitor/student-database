package de.igslandstuhl.database.server.webserver.handlers;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.APIObject;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.SubjectRequest;
import de.igslandstuhl.database.api.Task;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.Topic;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.api.results.GenerationResult;
import de.igslandstuhl.database.api.curriculum.Curriculum;
import de.igslandstuhl.database.api.curriculum.CurriculumException;
import de.igslandstuhl.database.plugins.config.BoolSetting;
import de.igslandstuhl.database.plugins.config.IntSetting;
import de.igslandstuhl.database.plugins.config.PluginConfig;
import de.igslandstuhl.database.plugins.config.PluginSetting;
import de.igslandstuhl.database.plugins.config.ShortAnswerSetting;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.ContentType;
import de.igslandstuhl.database.server.webserver.Status;
import de.igslandstuhl.database.server.webserver.access.AccessLevel;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;
import de.igslandstuhl.database.server.webserver.sessions.Session;
import de.igslandstuhl.database.server.webserver.sessions.SessionManager;
import de.igslandstuhl.database.utils.JSONUtils;
import de.igslandstuhl.database.utils.ThrowingConsumer;

public class PostRequestHandler {
    /**
     * The singleton instance of the PostRequestHandler class.
     * This instance is used to handle POST requests in the web server.
     * It ensures that only one instance of the handler is created, providing a consistent interface for processing requests.
     */
    private static final PostRequestHandler instance = new PostRequestHandler();
    /**
     * Returns the singleton instance of the PostRequestHandler class.
     * This method provides access to the handler instance, ensuring that only one instance is used throughout the application.
     *
     * @return The singleton instance of the PostRequestHandler class.
     */
    public static PostRequestHandler getInstance() {
        return instance;
    }
    /**
     * Private constructor to prevent instantiation.
     * This constructor is private to ensure that the PostRequestHandler class cannot be instantiated directly,
     * enforcing the singleton pattern.
     */
    private PostRequestHandler() {
        // Private constructor to prevent instantiation
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(PostRequestHandler.class);

    /**
     * Handles the POST request based on the path specified in the request.
     * It routes the request to the appropriate handler method based on the path.
     * @param request
     * @throws IOException
     */
    public HttpResponse handlePostRequest(PostRequest request) throws IOException {
        String path = request.getPath();

        HttpHandler<APIPostRequest> handler = Registry.postRequestHandlerRegistry().get(path);
        if (handler == null) return PostResponse.notFound("Unknown post request path: " + path, request);

        APIPostRequest rq = APIPostRequest.fromPostRequest(request);
        
        return handler.handleHttpRequest(rq);
    }
    private static String prepare(String webInput) {
        return prepare(webInput, true);
    }
    private static String prepare(String webInput, boolean sanitize) {
        try {
            webInput = URLDecoder.decode(webInput, StandardCharsets.UTF_8.name());
        } catch (UnsupportedEncodingException e) {
            LOGGER.error("Encoding is not supported by URLDecoder", e);
        }
                ;
        if (sanitize) {
            // Sanitize HTML to prevent XSS attacks
            PolicyFactory sanitizer = Sanitizers.FORMATTING
                    .and(Sanitizers.BLOCKS)
                    .and(Sanitizers.LINKS)
                    .and(Sanitizers.STYLES);

            webInput = sanitizer.sanitize(webInput);
        }
        return webInput;
    }
    private static PostResponse handleStudentGetData(APIPostRequest request) {
        String path = request.getPath().replace("student-", "my");
        Student student = request.getCurrentStudent();
        String email = student.getEmail(); // Email is the username for the student
        return PostResponse.getResource(WebResourceHandler.locationFromPath(path, student), email, request, path);
    }
    private static PostResponse handleTeacherGetData(APIPostRequest request) {
        String path = request.getPath().replace("teacher-", "my");
        Teacher teacher = request.getCurrentTeacher();
        String email = teacher.getEmail(); // Email is the username for the teacher
        return PostResponse.getResource(WebResourceHandler.locationFromPath(path, User.getUser(email)), email, request, path);
    }
    private static boolean visibleTask(Student student,Task task) {
        try {return new de.igslandstuhl.database.api.curriculum.CurriculumEnrollment(de.igslandstuhl.database.api.curriculum.Curriculum.current()).canAccessTask(student.getId(),task.getId(),true);}
        catch(de.igslandstuhl.database.api.curriculum.CurriculumException e) {return false;}
        catch(SQLException e) {throw new IllegalStateException("Could not resolve task visibility",e);}
    }
    private static boolean visibleTopic(Student student,Topic topic) {
        if(topic.getSemester()==null)return true;
        try {
            var catalog=de.igslandstuhl.database.api.curriculum.Curriculum.current().studentCatalog(student,topic.getSubject().getId(),topic.getSemester().getId());
            return ((List<?>)catalog.get("centralTopics")).stream().anyMatch(value->((Number)((Map<?,?>)value).get("id")).intValue()==topic.getId());
        } catch(de.igslandstuhl.database.api.curriculum.CurriculumException e) {return false;}
        catch(SQLException e) {throw new IllegalStateException("Could not resolve topic visibility",e);}
    }
    private static boolean publishedForStudent(APIPostRequest request,Student student,Task task) throws SQLException {
        if(!request.getUser().isStudent())return true;
        try {
            return new de.igslandstuhl.database.api.curriculum.CurriculumEnrollment(de.igslandstuhl.database.api.curriculum.Curriculum.current()).canAccessTask(student.getId(),task.getId(),false);
        } catch(de.igslandstuhl.database.api.curriculum.CurriculumException e) {return false;}
    }
    static PostResponse handleTaskChange(APIPostRequest request, int newStatus) throws IOException, SQLException {
        Student student = request.getCurrentStudent();
        if (student == null) return PostResponse.unauthorized(request);
        Task task = request.getTask();
        if (task == null) return PostResponse.notFound("Task not found", request);;
        int currentStatus = student.getTaskStatus(task);
        User user = request.getUser();
        if (user != null && user.isStudent() && newStatus == Task.STATUS_COMPLETED)
            return PostResponse.forbidden("Leistungen werden durch die zuständige Lehrkraft bestätigt.", request);
        if(!publishedForStudent(request,student,task))return PostResponse.forbidden("This task has not been released for your class.",request);
        if (user != null && (user.isTeacher() || user.isAdmin())) {
            try {
                Curriculum.Actor actor = new Curriculum.Actor(user.isAdmin(), user.isTeacher() ? user.asTeacher().getId() : 0);
                new Curriculum(Server.getInstance().getConnection()).authorizeCentralTaskChange(actor, student.getId(), task.getId(), newStatus == Task.STATUS_COMPLETED);
            } catch (CurriculumException e) {
                return curriculumError(e, request);
            }
        }
        if (newStatus == Task.STATUS_LOCKED && currentStatus == Task.STATUS_COMPLETED)
            return PostResponse.json(Status.CONFLICT, Map.of("error", "confirmed_completion", "message", "Abgeschlossene Leistungen können nicht durch Sperren zurückgesetzt werden."), request);
        try {
            new Curriculum(Server.getInstance().getConnection()).changeCentralStageStatus(student.getId(), task.getId(), newStatus);
        } catch (CurriculumException e) {
            return curriculumError(e, request);
        }
        return PostResponse.ok("Task status changed successfully", ContentType.TEXT_PLAIN, request);
    }
    private static PostResponse curriculumError(CurriculumException e, APIPostRequest request) {
        Status status = switch(e.status) {
            case 400 -> Status.BAD_REQUEST;
            case 401 -> Status.UNAUTHORIZED;
            case 403 -> Status.FORBIDDEN;
            case 404 -> Status.NOT_FOUND;
            default -> Status.CONFLICT;
        };
        return PostResponse.json(status, Map.of("error", e.code, "message", e.getMessage()), request);
    }
    static PostResponse handleEditStudentProfile(APIPostRequest rq) {
        try {
            int studentId = requiredInt(rq, "id");
            Student student = Student.get(studentId);
            if (student == null) return PostResponse.badRequest("Schüler nicht gefunden", rq);
            String firstName = requiredPrepared(rq, "firstName", true);
            String lastName = requiredPrepared(rq, "lastName", true);
            String loginName = requiredPrepared(rq, "email", false);
            if (firstName.isBlank() || lastName.isBlank() || loginName.isBlank())
                return PostResponse.badRequest("Pflichtfelder dürfen nicht leer sein.", rq);
            int classId = requiredInt(rq, "classId");
            SchoolClass schoolClass = SchoolClass.get(classId);
            if (schoolClass == null || !activeClass(classId))
                return PostResponse.badRequest("Ungültige Zielklasse.", rq);
            GraduationLevel graduationLevel = GraduationLevel.of(requiredInt(rq, "graduationLevel"));
            String password = rq.containsKey("password") ? rq.getString("password") : "";
            student.updateProfile(firstName, lastName, loginName, password, schoolClass, graduationLevel);
            return PostResponse.redirect("/manage_students", rq);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PostResponse.badRequest("Ungültige Schülerprofildaten.", rq);
        } catch (SQLException e) {
            LOGGER.warn("Could not update student profile", e);
            return PostResponse.badRequest("Schülerprofil konnte nicht gespeichert werden.", rq);
        }
    }
    static PostResponse handleEditTeacherProfile(APIPostRequest rq) {
        try {
            int teacherId = requiredInt(rq, "id");
            Teacher teacher = Teacher.get(teacherId);
            if (teacher == null) return PostResponse.badRequest("Lehrkraft nicht gefunden", rq);
            String firstName = requiredPrepared(rq, "firstName", true);
            String lastName = requiredPrepared(rq, "lastName", true);
            String loginName = requiredPrepared(rq, "email", false);
            if (firstName.isBlank() || lastName.isBlank() || loginName.isBlank())
                return PostResponse.badRequest("Pflichtfelder dürfen nicht leer sein.", rq);
            if (!loginName.equals(teacher.getEmail()))
                return PostResponse.badRequest("Loginname kann hier nicht geändert werden.", rq);
            String password = rq.containsKey("password") ? rq.getString("password") : "";
            boolean passwordChanged = password != null && !password.isEmpty();
            teacher.updateProfile(firstName, lastName, loginName, password);
            if (passwordChanged)
                Server.getInstance().getWebServer().getSessionManager().invalidateUserSessions(teacher.getUsername());
            return PostResponse.redirect("/manage_teachers", rq);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PostResponse.badRequest("Ungültige Lehrkraftprofildaten.", rq);
        } catch (SQLException e) {
            LOGGER.warn("Could not update teacher profile", e);
            return PostResponse.badRequest("Lehrkraftprofil konnte nicht gespeichert werden.", rq);
        }
    }
    static PostResponse handleArchiveStudent(APIPostRequest rq) {
        try {
            Student student = Student.get(requiredInt(rq, "id"));
            if (student == null) return PostResponse.badRequest("Schüler nicht gefunden", rq);
            Student archived = student.archive();
            Server.getInstance().getWebServer().getSessionManager().invalidateUserSessions(archived.getUsername());
            return PostResponse.json(Map.of("id", archived.getId(), "active", archived.isActive()), rq);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PostResponse.badRequest("Ungültige Schüler-ID.", rq);
        } catch (SQLException e) {
            LOGGER.warn("Could not archive student", e);
            return PostResponse.badRequest("Schüler konnte nicht archiviert werden.", rq);
        }
    }
    static PostResponse handleReactivateStudent(APIPostRequest rq) {
        try {
            Student student = Student.get(requiredInt(rq, "id"));
            if (student == null) return PostResponse.badRequest("Schüler nicht gefunden", rq);
            Student reactivated = student.reactivate();
            return PostResponse.json(Map.of("id", reactivated.getId(), "active", reactivated.isActive()), rq);
        } catch (IllegalArgumentException | NullPointerException e) {
            return PostResponse.badRequest("Ungültige Schüler-ID.", rq);
        } catch (SQLException e) {
            LOGGER.warn("Could not reactivate student", e);
            return PostResponse.badRequest("Schüler konnte nicht reaktiviert werden.", rq);
        }
    }
    private static String requiredPrepared(APIPostRequest rq, String key, boolean sanitize) {
        if (!rq.containsKey(key)) throw new IllegalArgumentException("Missing field: " + key);
        String value = rq.getString(key);
        if (value == null) throw new IllegalArgumentException("Missing field: " + key);
        return prepare(value, sanitize);
    }
    private static int requiredInt(APIPostRequest rq, String key) {
        if (!rq.containsKey(key)) throw new IllegalArgumentException("Missing field: " + key);
        return rq.getInt(key);
    }
    private static boolean activeClass(int classId) throws SQLException {
        try (PreparedStatement statement = Server.getInstance().getConnection().getSQLConnection().prepareStatement(
                "SELECT COUNT(*) FROM classes WHERE id=? AND COALESCE(active,1)=1")) {
            statement.setInt(1, classId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) > 0;
            }
        }
    }
    public static void registerTaskChangeHandler(String path, AccessLevel accessLevel, int taskStatus) {
        HttpHandler.registerPostRequestHandler(path, accessLevel, (rq) -> {
            Task task = Task.get(rq.getInt("taskId"));
            Student student = rq.getCurrentStudent();
            if (student == null) return PostResponse.unauthorized("Not logged in or invalid session", rq);
            if (task == null) return PostResponse.notFound("Task not found", rq);
            try {
                if(!publishedForStudent(rq,student,task))return PostResponse.forbidden("This task has not been released for your class.",rq);
                student.changeTaskStatus(task, taskStatus);
                return PostResponse.ok("Task status changed successfully", ContentType.TEXT_PLAIN, rq);
            } catch (SQLException e) {
                return PostResponse.internalServerError("Database error: " + e.getMessage(), rq);
            }
        });
    }
    public static <T> PostResponse handleBatchInsertJson(PostRequest rq, String key, ContentType contentType, Function<Map<String,Object>, T> factory, Function<List<T>, String> serializer) {
        try {
            @SuppressWarnings("unchecked")
            List<Map<String,Object>> rawItems = (List<Map<String,Object>>) rq.getJson().get(key);
            List<T> entities = rawItems.stream().map(factory).toList();
            return PostResponse.ok(serializer.apply(entities), contentType, rq);
        } catch (Exception e) {
            return PostResponse.badRequest("Could not add " + key + ": " + e, rq);
        }
    }
    public static <T> PostResponse handleBatchInsertCSV(PostRequest rq, String key, ContentType contentType, Function<String, T[]> factory, Function<T[], String> serializer) {
        try {
            T[] entities = factory.apply(rq.getBodyAsString().replace("csv=", ""));
            return PostResponse.ok(serializer.apply(entities), contentType, rq);
        } catch (Exception e) {
            return PostResponse.badRequest("Could not add " + key + ": " + e, rq);
        }
    }
    public static <T> String csvResult(GenerationResult<T>[] results) {
        return Arrays.stream(results).map(GenerationResult::toCSVRow).reduce("", (r1,r2) -> r1+"\n"+r2);
    }
    public static <T extends APIObject> PostResponse handleObjectAction(APIPostRequest rq, TypeToken<T> type, PostResponse successMessage, ThrowingConsumer<T> handler) throws Exception {
        T object = rq.getAPIObject(type);
        handler.accept(object);
        return successMessage;
    }
    public static void registerHandlers() {
        CurriculumRequestHandler.registerHandlers();
        LOGGER.info("Registering Post Request Handlers...");
        HttpHandler.registerPostRequestHandler("/login", AccessLevel.PUBLIC, (rq) -> {
            String username = prepare(rq.getString("username"), false);
            // Do not sanitize / url-decode password to allow special characters like %
            // This is safe as we calculate the hash value anyways
            String password = rq.getString("password");
            final String next;
            try { next = rq.containsKey("next") ? safeLoginNext(rq.getString("next")) : "/dashboard"; }
            catch (IllegalArgumentException e) { return PostResponse.badRequest("Ungültiges Weiterleitungsziel", rq); }
            // Check login credentials in the database
            if (Server.getInstance().isValidUser(username, password)) {
                SessionManager manager = Server.getInstance().getWebServer().getSessionManager();
                Session session = manager.getSession(rq);
                manager.addSessionUser(session, username);
                return PostResponse.redirect(next, rq, session.createSessionCookie());
            } else {
                return PostResponse.unauthorized("Wrong credentials!", rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/editor", AccessLevel.ADMIN, (rq) -> {
            return PostResponse.redirect("/editor", rq);
        });


        HttpHandler.registerPostRequestHandler("/add-students", AccessLevel.ADMIN, (rq) ->
            handleBatchInsertCSV(rq, "students", ContentType.CSV, t -> {
                try {
                    return Student.generateStudentsFromCSV(t);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }, PostRequestHandler::csvResult)
        );
        HttpHandler.registerPostRequestHandler("/add-teachers", AccessLevel.ADMIN, (rq) ->
            handleBatchInsertCSV(rq, "teachers", ContentType.CSV, t -> {
                try {
                    return Teacher.generateTeachersFromCSV(t);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }, PostRequestHandler::csvResult)
        );
        HttpHandler.registerPostRequestHandler("/add-teacher", AccessLevel.ADMIN, (rq) -> {
            String firstName = prepare(rq.getString("firstName"));
            String lastName = prepare(rq.getString("lastName"));
            String email = prepare(rq.getString("email"), false);
            String password = Teacher.generateRandomPassword(12, (rq.getContentLength() << 4 + firstName.length() + lastName.length()) << 7 + System.currentTimeMillis() * new Random().nextInt());
            Teacher teacher = Teacher.registerTeacher(firstName, lastName, email, password);
            return PostResponse.ok(teacher.toString().replace("}", "") + ", \"password\": " + password + "}", ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/add-subject", AccessLevel.ADMIN, (rq) -> {
            Subject.addSubject(rq.getString("name"));
            return PostResponse.redirect("/manage_subjects", rq);
        });
        HttpHandler.registerPostRequestHandler("/add-class", AccessLevel.ADMIN, (rq) -> {
            SchoolClass.addClass(rq.getString("className"), rq.getInt("grade"));
            return PostResponse.redirect("/manage_classes", rq);
        });
        HttpHandler.registerPostRequestHandler("/lpt-file", AccessLevel.ADMIN, (rq) -> {
            String file = prepare(rq.getBodyAsString().replaceFirst("file=", "").replace("Â", ""));
            Application.getInstance().readFile(file);
            return PostResponse.ok("File data stored", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/subject-request", AccessLevel.USER, (rq) -> {
            Student student = rq.getCurrentStudent();
            Subject subject = rq.getSubject();
            SubjectRequest subjectRequest = rq.getSubjectRequest();
            if (student != null) {
                if (rq.getBoolean("remove")) {
                    student.removeSubjectRequest(subject, subjectRequest);
                    return PostResponse.ok("Removed request", ContentType.TEXT_PLAIN, rq);
                } else {
                    student.addSubjectRequest(subject, subjectRequest);
                    return PostResponse.ok("Added request", ContentType.TEXT_PLAIN, rq);
                }
            } else {
                return PostResponse.unauthorized(rq);
            }
        });
        HttpHandler.registerPostRequestHandler("/current-topic", AccessLevel.USER, (rq) -> {
            Student student = rq.getCurrentStudent();
            Subject subject = rq.getSubject();
            if (student == null) return PostResponse.unauthorized(rq);
            Topic topic = student.getCurrentTopic(subject);
            if (topic == null) return PostResponse.badRequest("No current topic for this subject.", rq);
            if(rq.getUser().isStudent() && !visibleTopic(rq.getUser().asStudent(),topic))return PostResponse.badRequest("Current topic is not released.",rq);
            return PostResponse.ok(topic.toJSON(), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/change-current-topic", AccessLevel.TEACHER, (rq) -> {
            Student student = rq.getCurrentStudent();
            if (student == null) return PostResponse.unauthorized(rq);
            Subject subject = rq.getSubject();
            Topic topic = rq.getTopic();
            if (subject == null || topic == null) return PostResponse.badRequest("Subject or topic id not found", rq);
            student.setCurrentTopic(subject, topic);
            return PostResponse.ok("Current topic changed successfully", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/tasks", AccessLevel.USER, (rq) -> {
            var tasks=rq.getTaskList();
            if(rq.getUser().isStudent())tasks=tasks.stream().filter(t->visibleTask(rq.getUser().asStudent(),t)).toList();
            return PostResponse.ok(JSONUtils.toJSON(tasks), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/begin-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_IN_PROGRESS));
        HttpHandler.registerPostRequestHandler("/complete-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_COMPLETED));
        HttpHandler.registerPostRequestHandler("/cancel-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_NOT_STARTED));
        HttpHandler.registerPostRequestHandler("/reopen-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_NOT_STARTED));
        HttpHandler.registerPostRequestHandler("/lock-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_LOCKED));
        HttpHandler.registerPostRequestHandler("/student-data", AccessLevel.TEACHER, PostRequestHandler::handleStudentGetData);
        HttpHandler.registerPostRequestHandler("/student-subjects", AccessLevel.TEACHER, PostRequestHandler::handleStudentGetData);
        HttpHandler.registerPostRequestHandler("/teacher-classes", AccessLevel.ADMIN, PostRequestHandler::handleTeacherGetData);
        HttpHandler.registerPostRequestHandler("/teacher-subjects", AccessLevel.ADMIN, PostRequestHandler::handleTeacherGetData);
        HttpHandler.registerPostRequestHandler("/student-list", AccessLevel.TEACHER, (rq) -> {
            SchoolClass schoolClass = rq.getSchoolClass();
            if (schoolClass == null) return PostResponse.notFound("School class not found", rq);
            if (rq.getUser().isTeacher() && !rq.getUser().asTeacher().getClassIds().contains(schoolClass.getId()))
                return PostResponse.forbidden("You are not allowed to access this class's student list.", rq);
            List<Student> students = schoolClass.getStudents();
            return PostResponse.ok(
                JSONUtils.toJSON(students, (student, builder) -> {
                    builder
                    .addProperty("id", student.getId())
                    .addProperty("name", student.getFirstName() + " " + student.getLastName())
                    .addProperty("actionRequired", student.isActionRequired())
                    .addProperty("graduationLevel", student.getGraduationLevel());
                    if (rq.getJson().containsKey("subjectId") && rq.getSubject() != null) {
                        Set<SubjectRequest> subjectRequests = student.getCurrentRequests(rq.getSubject());
                        builder.addProperty("experiment",subjectRequests.stream().anyMatch(r -> r == SubjectRequest.EXPERIMENT))
                        .addProperty("help", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.HELP))
                        .addProperty("test", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.EXAM))
                        .addProperty("partner", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.PARTNER));
                        String currentTask = student.getSelectedTasks().stream()
                            .filter(task -> task.getSubject() != null && task.getSubject().equals(rq.getSubject()))
                            .map(Task::getName)
                            .collect(Collectors.joining(", "));
                        builder.addProperty("currentTask", currentTask);
                    }
                }),
                ContentType.JSON, rq
            );
        });
        HttpHandler.registerPostRequestHandler("/grade-list", AccessLevel.PUBLIC, (rq) -> {
            return PostResponse.ok(JSONUtils.toJSON(rq.getSubject().getGrades()), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/topic-list", AccessLevel.STUDENT, (rq) -> {
            var topics=rq.getSubject().getTopics(rq.getInt("grade"));
            if(rq.getUser().isStudent())topics=topics.stream().filter(t->visibleTopic(rq.getUser().asStudent(),t)).toList();
            return PostResponse.ok(JSONUtils.toJSON(topics), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/class-subjects", AccessLevel.ADMIN, (rq) -> {
            SchoolClass schoolClass = rq.getSchoolClass();
            if (schoolClass == null) return PostResponse.notFound("School class not found", rq);
            List<Subject> subjects = schoolClass.getSubjects();
            return PostResponse.ok(JSONUtils.toJSON(subjects, (subject, builder) -> {
                builder.addProperty("id", subject.getId()).addProperty("name", subject.getName());
            }), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/search-partner", AccessLevel.USER, (rq) -> {
            SchoolClass schoolClass = rq.getSchoolClass();
            Subject subject = rq.getSubject();
            Topic topic = rq.getTopic();
            Student student = rq.getCurrentStudent();

            List<Student> students = Student.getAll().stream()
                                        .filter((s) -> s.getId() != student.getId())
                                        .filter((s) -> s.getSchoolClass() != null && s.getSchoolClass().getGrade() == schoolClass.getGrade())
                                        .filter((s) -> topic != null && topic.equals(s.getCurrentTopic(subject))
                                            && s.getSelectedTasks().stream().filter((t) -> topic.equals(t.getTopic())).anyMatch((t) -> student.getSelectedTasks().contains(t))
                                            && s.getCurrentRequests(subject).stream().anyMatch((r) -> r == SubjectRequest.PARTNER))
                                            .toList();
            return PostResponse.ok(JSONUtils.toJSON(students, (partner, builder) -> {
                builder.addProperty("id", partner.getId())
                .addProperty("name", partner.getFirstName() + " " + partner.getLastName());
            }), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/delete-subject", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/manage_subjects", rq), (subject) -> subject.delete())            
        );
        HttpHandler.registerPostRequestHandler("/edit-subject", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/manage_subjects", rq), (subject) -> subject.edit(prepare(rq.getString("name"))))
        );
        HttpHandler.registerPostRequestHandler("/delete-class", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<SchoolClass>() {}, PostResponse.redirect("/manage_classes", rq), (schoolClass) -> schoolClass.delete())            
        );
        HttpHandler.registerPostRequestHandler("/archive-student", AccessLevel.ADMIN, PostRequestHandler::handleArchiveStudent);
        HttpHandler.registerPostRequestHandler("/reactivate-student", AccessLevel.ADMIN, PostRequestHandler::handleReactivateStudent);
        HttpHandler.registerPostRequestHandler("/edit-class", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<SchoolClass>() {}, PostResponse.redirect("/manage_classes", rq), (schoolClass) -> schoolClass.edit(prepare(rq.getString("name")), rq.getInt("grade")))
        );
        HttpHandler.registerPostRequestHandler("/edit-student-profile", AccessLevel.ADMIN, PostRequestHandler::handleEditStudentProfile);
        HttpHandler.registerPostRequestHandler("/edit-teacher-profile", AccessLevel.ADMIN, PostRequestHandler::handleEditTeacherProfile);
        HttpHandler.registerPostRequestHandler("/add-subject-to-class", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<SchoolClass>() {}, PostResponse.redirect("/class", rq), (schoolClass) -> schoolClass.addSubject(rq.getSubject()))
        );
        HttpHandler.registerPostRequestHandler("/add-grade-to-subject", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/subject", rq), (subject) -> subject.addToGrade(rq.getInt("grade")))
        );
        HttpHandler.registerPostRequestHandler("/delete-grade-from-subject", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/subject", rq), (subject) -> subject.removeFromGrade(rq.getInt("grade")))
        );
        HttpHandler.registerPostRequestHandler("/delete-topics", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/subject", rq), (subject) -> subject.getTopics(rq.getInt("grade")).forEach((topic) -> {
                try {
                    topic.delete();
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }))
        );
        HttpHandler.registerPostRequestHandler("/add-class-to-teacher", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Teacher>() {}, PostResponse.redirect("/teacher", rq), (teacher) -> teacher.addClass(rq.getSchoolClass()))
        );
        HttpHandler.registerPostRequestHandler("/add-subject-to-teacher", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Teacher>() {}, PostResponse.redirect("/teacher", rq), (teacher) -> teacher.addSubject(rq.getSubject()))
        );
        HttpHandler.registerPostRequestHandler("/change-graduation-level", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Student>() {}, PostResponse.ok("Successfully changed graduation level", ContentType.TEXT_PLAIN, rq), (student) -> student.changeGraduationLevel(rq.getInt("graduationLevel")))
        );

        HttpHandler.registerPostRequestHandler("/get-plugin", AccessLevel.USER, (rq) -> {
            return PostResponse.ok(Registry.pluginRegistry().get(rq.getString("key")).toJSON(), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/toggle-plugin", AccessLevel.ADMIN, (rq) -> {
            Registry.pluginRegistry().get(rq.getString("key")).toggle();
            return PostResponse.ok("Plugin toggled", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/toggle-plugin-setting", AccessLevel.ADMIN, (rq) -> {
            String[] key = rq.getString("key").split(":");
            Registry.pluginRegistry().get(key[0]).getConfig().toggleSetting(key[1]);
            return PostResponse.ok("Plugin setting toggled", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/set-plugin-setting", AccessLevel.ADMIN, (rq) -> {
            String[] key = rq.getString("key").split(":");
            PluginConfig<?> config = Registry.pluginRegistry().get(key[0]).getConfig();
            PluginSetting<?> setting = config.getSetting(key[1]);
            if (setting instanceof BoolSetting boolSetting) {
                boolSetting.setValue(rq.getBoolean("value"));
            } else if (setting instanceof IntSetting intSetting) {
                intSetting.setValue(rq.getInt("value"));
            } else if (setting instanceof ShortAnswerSetting shortAnswerSetting) {
                shortAnswerSetting.setValue(rq.getString("value"));
            } else {
                return PostResponse.badRequest("Setting not found", rq);}
            return PostResponse.ok("Plugin setting set", ContentType.TEXT_PLAIN, rq);
        });

        HttpHandler.registerPostRequestHandler("/student-results-csv", AccessLevel.TEACHER, (rq) -> {
            Student student = rq.getCurrentStudent();
            if (student == null) return PostResponse.badRequest("There is no current student", rq);
            return PostResponse.ok(student.getResultsCSV(), ContentType.CSV, rq);
        });
        HttpHandler.registerPostRequestHandler("/completed-tasks", AccessLevel.TEACHER, (rq) -> {
            SchoolClass schoolClass = rq.getSchoolClass();
            Subject subject = rq.getSubject();
            if (schoolClass == null) return PostResponse.badRequest("No school class specified", rq);
            if (subject == null) return PostResponse.badRequest("No subject specified", rq);
            return PostResponse.ok(schoolClass.getCompletedTasksCSV(subject), ContentType.CSV, rq);
        });
        HttpHandler.registerPostRequestHandler("/class-results", AccessLevel.TEACHER, (rq) -> {
            SchoolClass schoolClass = rq.getSchoolClass();
            Subject subject = rq.getSubject();
            if (schoolClass == null) return PostResponse.badRequest("No school class specified", rq);
            if (subject == null) return PostResponse.badRequest("No subject specified", rq);
            return PostResponse.ok(schoolClass.getResultsCSV(subject), ContentType.CSV, rq);
        });
        HttpHandler.registerPostRequestHandler("/grade-results", AccessLevel.ADMIN, (rq) -> {
            int grade = rq.getInt("grade");
            Subject subject = rq.getSubject(); 
            return PostResponse.ok(SchoolClass.getResultsCSV(grade, subject), ContentType.CSV, rq);
        });
        HttpHandler.registerPostRequestHandler("/logout", AccessLevel.USER, (rq) -> {
            Server.getInstance().getWebServer().getSessionManager().logout(rq);
            return PostResponse.redirect("/login", rq);
        });
    }

    /** Validates the optional post-login target and prevents open redirects. */
    private static String safeLoginNext(String next) {
        if (next == null || next.length() > 4096) throw new IllegalArgumentException("Ungültiges Weiterleitungsziel");
        try { next = URLDecoder.decode(next, StandardCharsets.UTF_8); }
        catch (IllegalArgumentException e) { throw new IllegalArgumentException("Ungültiges Weiterleitungsziel"); }
        if (next.length() > 2048 || next.indexOf('\r') >= 0 || next.indexOf('\n') >= 0
                || next.indexOf('\\') >= 0 || !next.startsWith("/") || next.startsWith("//")
                || next.regionMatches(true, 0, "/http:", 0, 6)
                || next.regionMatches(true, 0, "/https:", 0, 7)) {
            throw new IllegalArgumentException("Ungültiges Weiterleitungsziel");
        }
        return next;
    }
}
