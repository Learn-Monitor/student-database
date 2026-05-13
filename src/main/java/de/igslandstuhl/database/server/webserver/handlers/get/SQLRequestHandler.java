package de.igslandstuhl.database.server.webserver.handlers.get;

import java.util.HashSet;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.igslandstuhl.database.Registry;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.User;

@FunctionalInterface
public interface SQLRequestHandler {
    public static final Logger LOGGER = LoggerFactory.getLogger(SQLRequestHandler.class);
    public String get(User user);

    public static String getResource(String resource, User user) {
        return Registry.sqlRequestHandlerRegistry().get(resource).get(user);
    }
    public static void register() {
        LOGGER.info("Registering SQL request handlers...");

        Registry.sqlRequestHandlerRegistry().register("mydata", (user) -> user.toJSON());

        Registry.sqlRequestHandlerRegistry().register("mysubjects", (user) -> {
            if (user instanceof Student student) {
                return student.getSchoolClass().getSubjects().toString();
            } else if (user instanceof Teacher teacher) {
                return teacher.getSubjects().toString();
            } else {
                return null;
            }
        });
        Registry.sqlRequestHandlerRegistry().register("myclasses", (user) -> {
            if (user instanceof Teacher teacher) {
                Set<Integer> classIDs = teacher.getClassIds();
                Set<String> classes = new HashSet<>();
                for (Integer classID : classIDs) {
                    classes.add("{\"classId\": " + classID + ", \"name\": \"" + SchoolClass.get(classID).getLabel() + "\"}");
                }
                return classes.toString();
            } else {
                return null;
            }
        });

        Registry.sqlRequestHandlerRegistry().register("teachers", (user) -> new HashSet<>(Teacher.getAll()).toString());
        Registry.sqlRequestHandlerRegistry().register("students", (user) -> new HashSet<>(Student.getAll()).toString());
        Registry.sqlRequestHandlerRegistry().register("subjects", (user) -> new HashSet<>(Subject.getAll()).toString());
        Registry.sqlRequestHandlerRegistry().register("classes", (user) -> new HashSet<>(SchoolClass.getAll()).toString());
        Registry.sqlRequestHandlerRegistry().register("all-student-resukts", (user) -> Student.getAllResultsCSV());
    }
}
