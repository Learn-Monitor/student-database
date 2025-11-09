package de.igslandstuhl.database.server.webserver;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

import org.owasp.html.PolicyFactory;
import org.owasp.html.Sanitizers;

import com.google.gson.reflect.TypeToken;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.APIObject;
import de.igslandstuhl.database.api.Room;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.SubjectRequest;
import de.igslandstuhl.database.api.Task;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.Topic;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.api.results.GenerationResult;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.webserver.requests.APIPostRequest;
import de.igslandstuhl.database.server.webserver.requests.HttpHandler;
import de.igslandstuhl.database.server.webserver.requests.PostRequest;
import de.igslandstuhl.database.server.webserver.responses.HttpResponse;
import de.igslandstuhl.database.server.webserver.responses.PostResponse;
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

    /**
     * Handles the POST request based on the path specified in the request.
     * It routes the request to the appropriate handler method based on the path.
     * @param request
     * @param in
     * @param out
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
            e.printStackTrace();
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
        return PostResponse.getResource(WebResourceHandler.locationFromPath(path, student), email, request);
    }
    private static PostResponse handleTeacherGetData(APIPostRequest request) {
        String path = request.getPath().replace("teacher-", "my");
        Teacher teacher = request.getCurrentTeacher();
        String email = teacher.getEmail(); // Email is the username for the teacher
        return PostResponse.getResource(WebResourceHandler.locationFromPath(path, User.getUser(email)), email, request);
    }
    private static PostResponse handleTaskChange(APIPostRequest request, int newStatus) throws IOException, SQLException {
        Student student = request.getCurrentStudent();
        if (student == null) return PostResponse.unauthorized(request);
        Task task = request.getTask();
        if (task == null) return PostResponse.notFound("Task not found", request);;
        student.changeTaskStatus(task, newStatus);
        return PostResponse.ok("Task status changed successfully", ContentType.TEXT_PLAIN, request);
    }
    public static void registerTaskChangeHandler(String path, AccessLevel accessLevel, int taskStatus) {
        HttpHandler.registerPostRequestHandler(path, accessLevel, (rq) -> {
            Task task = Task.get(rq.getInt("taskId"));
            Student student = rq.getCurrentStudent();
            if (student == null) return PostResponse.unauthorized("Not logged in or invalid session", rq);
            if (task == null) return PostResponse.notFound("Task not found", rq);
            try {
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
        HttpHandler.registerPostRequestHandler("/login", AccessLevel.PUBLIC, (rq) -> {
            String username = prepare(rq.getString("username"), false);
            // Do not sanitize / url-decode password to allow special characters like %
            // This is safe as we calculate the hash value anyways
            String password = rq.getString("password");
            // Check login credentials in the database
            if (Server.getInstance().isValidUser(username, password)) {
                SessionManager manager = Server.getInstance().getWebServer().getSessionManager();
                Session session = manager.getSession(rq);
                manager.addSessionUser(session, username);
                return PostResponse.ok("Login successful", ContentType.TEXT_PLAIN, rq, session.createSessionCookie());
            } else {
                return PostResponse.unauthorized("Wrong credentials!", rq);
            }
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
        HttpHandler.registerPostRequestHandler("/add-rooms", AccessLevel.ADMIN, (rq) ->
            handleBatchInsertCSV(rq, "rooms", ContentType.JSON, t -> {
                try {
                    return Room.generateRoomsFromCSV(t);
                } catch (SQLException e) {
                    throw new IllegalStateException(e);
                }
            }, Arrays::toString)
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
            return PostResponse.redirect("/manage_subjects", rq);
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
            return PostResponse.ok(JSONUtils.toJSON(rq.getTaskList()), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/update-room", AccessLevel.USER, (rq) -> {
            Student student = rq.getCurrentStudent();
            if (student == null) return PostResponse.unauthorized(rq);
            Room room = rq.getRoom();
            if (room == null) return PostResponse.badRequest("Room not found", rq);
            student.setCurrentRoom(room);
            return PostResponse.ok("Changed current room", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/begin-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_IN_PROGRESS));
        HttpHandler.registerPostRequestHandler("/complete-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_COMPLETED));
        HttpHandler.registerPostRequestHandler("/cancel-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_NOT_STARTED));
        HttpHandler.registerPostRequestHandler("/reopen-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_NOT_STARTED));
        HttpHandler.registerPostRequestHandler("/lock-task", AccessLevel.USER, (rq) -> handleTaskChange(rq, Task.STATUS_LOCKED));
        HttpHandler.registerPostRequestHandler("/student-data", AccessLevel.TEACHER, PostRequestHandler::handleStudentGetData);
        HttpHandler.registerPostRequestHandler("/rooms", AccessLevel.TEACHER, PostRequestHandler::handleStudentGetData);
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
                    .addProperty("graduationLevel", student.getGraduationLevel())
                    .addProperty("room", student.getCurrentRoom() != null ? student.getCurrentRoom().getLabel() : "None");
                    if (rq.getJson().containsKey("subjectId") && rq.getSubject() != null) {
                        Set<SubjectRequest> subjectRequests = student.getCurrentRequests(rq.getSubject());
                        builder.addProperty("experiment",subjectRequests.stream().anyMatch(r -> r == SubjectRequest.EXPERIMENT))
                        .addProperty("help", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.HELP))
                        .addProperty("test", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.EXAM))
                        .addProperty("partner", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.PARTNER));
                    }
                }),
                ContentType.JSON, rq
            );
        });
        HttpHandler.registerPostRequestHandler("/get-students-by-room", AccessLevel.TEACHER, (rq) -> {
            Room room = rq.getRoom();
            List<Student> students = Student.getByRoom(room);
            return PostResponse.ok(
                JSONUtils.toJSON(students, (student, builder) -> {
                    builder
                    .addProperty("id", student.getId())
                    .addProperty("name", student.getFirstName() + " " + student.getLastName())
                    .addProperty("actionRequired", student.isActionRequired())
                    .addProperty("graduationLevel", student.getGraduationLevel())
                    .addProperty("room", student.getCurrentRoom() != null ? student.getCurrentRoom().getLabel() : "None");
                    if (rq.getJson().containsKey("subjectId") && rq.getSubject() != null) {
                        Set<SubjectRequest> subjectRequests = student.getCurrentRequests(rq.getSubject());
                        builder.addProperty("experiment",subjectRequests.stream().anyMatch(r -> r == SubjectRequest.EXPERIMENT))
                        .addProperty("help", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.HELP))
                        .addProperty("test", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.EXAM))
                        .addProperty("partner", subjectRequests.stream().anyMatch(r -> r == SubjectRequest.PARTNER));
                    }
                }),
                ContentType.JSON, rq
            );
        });
        HttpHandler.registerPostRequestHandler("/grade-list", AccessLevel.PUBLIC, (rq) -> {
            return PostResponse.ok(JSONUtils.toJSON(rq.getSubject().getGrades()), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/topic-list", AccessLevel.STUDENT, (rq) -> {
            return PostResponse.ok(JSONUtils.toJSON(rq.getSubject().getTopics(rq.getInt("grade"))), ContentType.JSON, rq);
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
                                        .filter((s) -> s.getSchoolClass().getGrade() == schoolClass.getGrade())
                                        .filter((s) -> s.getCurrentTopic(subject).equals(topic)
                                            && s.getSelectedTasks().stream().filter((t) -> t.getTopic().equals(topic)).anyMatch((t) -> student.getSelectedTasks().contains(t))
                                            && s.getCurrentRequests(subject).stream().anyMatch((r) -> r == SubjectRequest.PARTNER))
                                            .toList();
            return PostResponse.ok(JSONUtils.toJSON(students, (partner, builder) -> {
                builder.addProperty("id", partner.getId())
                .addProperty("name", partner.getFirstName() + " " + partner.getLastName())
                .addProperty("room", partner.getCurrentRoom() != null ? partner.getCurrentRoom().getLabel() : "None");
            }), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/delete-subject", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/manage_subjects", rq), (subject) -> subject.delete())            
        );
        HttpHandler.registerPostRequestHandler("/edit-subject", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<Subject>() {}, PostResponse.redirect("/manage_subjects", rq), (subject) -> subject.edit(prepare(rq.getString("name"))))
        );
        HttpHandler.registerPostRequestHandler("/delete-classs", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<SchoolClass>() {}, PostResponse.redirect("/manage_classes", rq), (schoolClass) -> schoolClass.delete())            
        );
        HttpHandler.registerPostRequestHandler("/edit-class", AccessLevel.ADMIN, (rq) -> 
            handleObjectAction(rq, new TypeToken<SchoolClass>() {}, PostResponse.redirect("/manage_classes", rq), (schoolClass) -> schoolClass.edit(prepare(rq.getString("name")), rq.getInt("grade")))
        );
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

        HttpHandler.registerPostRequestHandler("/get-module", AccessLevel.USER, (rq) -> {
            return PostResponse.ok(Registry.moduleRegistry().get(rq.getString("key")).toJSON(), ContentType.JSON, rq);
        });
        HttpHandler.registerPostRequestHandler("/toggle-module", AccessLevel.ADMIN, (rq) -> {
            Registry.moduleRegistry().get(rq.getString("key")).toggle();
            return PostResponse.ok("Module toggled", ContentType.TEXT_PLAIN, rq);
        });
        HttpHandler.registerPostRequestHandler("/toggle-module-setting", AccessLevel.ADMIN, (rq) -> {
            String[] key = rq.getString("key").split(":");
            Registry.moduleRegistry().get(key[0]).toggleSetting(key[1]);
            return PostResponse.ok("Module setting toggled", ContentType.TEXT_PLAIN, rq);
        });
    }
}