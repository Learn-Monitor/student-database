package de.igslandstuhl.database.server.webserver.requests;

import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import com.google.gson.reflect.TypeToken;

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
import de.igslandstuhl.database.server.Server;

public class APIPostRequest extends PostRequest {
    public APIPostRequest(HttpHeader header, String body, String ipAddress, boolean secureConnection) {
        super(header, body, ipAddress, secureConnection);
    }
    public Student getCurrentStudent() {
        User user = Server.getInstance().getWebServer().getSessionManager().getSessionUser(this);
        if (user == null || user == User.ANONYMOUS) {
            return null; // User is not logged in
        }
        if (user instanceof Student student) {
            return student;
        } else if ((user.isTeacher() || user.isAdmin()) && containsKey("studentId")) {
            return Student.get(getInt("studentId"));
        }
        return null;
    }
    public Teacher getCurrentTeacher() {
        User user = Server.getInstance().getWebServer().getSessionManager().getSessionUser(this);
        if (user == null || user == User.ANONYMOUS) {
            return null; // User is not logged in
        }
        if (user instanceof Teacher teacher) {
            return teacher;
        } else if (user.isAdmin() && containsKey("teacherId")) {
            return Teacher.get(getInt("teacherId"));
        }
        return null;
    }
    public User getUser() {
        return Server.getInstance().getWebServer().getSessionManager().getSessionUser(this);
    }
    public Subject getSubject() {
        return Subject.get(getInt("subjectId"));
    }
    public Topic getTopic() {
        return Topic.get(getInt("topicId"));
    }
    public SubjectRequest getSubjectRequest() {
        return SubjectRequest.fromGermanTranslation(getString("subjectRequest"));
    }
    public Room getRoom() {
        return Room.getRoom(getString("room"));
    }
    public Task getTask() {
        return Task.get(getInt("taskId"));
    }
    public SchoolClass getSchoolClass() {
        return SchoolClass.get(getInt("classId"));
    }
    @SuppressWarnings("unchecked")
    public <T extends APIObject> T getAPIObject(TypeToken<T> type) {
        Type rawType = type.getType();
        if (rawType.getTypeName().contains("User")) {
            return (T) getUser();
        } else if (rawType.getTypeName().contains("Student")) {
            return (T) getCurrentStudent();
        } else if (rawType.getTypeName().contains("Teacher")) {
            return (T) getCurrentTeacher();
        } else if (rawType.getTypeName().contains("Admin")) {
            return (T) getUser().asAdmin();
        } else if (rawType.getTypeName().contains("Subject")) {
            return (T) getSubject();
        } else if (rawType.getTypeName().contains("Topic")) {
            return (T) getTopic();
        } else if (rawType.getTypeName().contains("SubjectRequest")) {
            return (T) getSubjectRequest();
        } else if (rawType.getTypeName().contains("Task")) {
            return (T) getTask();
        } else if (rawType.getTypeName().contains("Room")) {
            return (T) getRoom();
        } else if (rawType.getTypeName().contains("SchoolClass")) {
            return (T) getSchoolClass();
        } else {
            return null;
        }
    }
    public List<Task> getTaskList() {
        List<Integer> ids = new LinkedList<>();
        for (Object o : getList("ids")) {
            if (o instanceof Number n) {
                ids.add(n.intValue());
            }
        }
        return Task.getTasksByIds(ids);
    }
    public static APIPostRequest fromPostRequest(PostRequest request) {
        if (request instanceof APIPostRequest rq) return rq;
        else return new APIPostRequest(request.getHeader(), request.getBodyAsString(), request.getIP(), request.isSecureConnection());
    }
}
