package tools.admin_e2e;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.SchoolYear;
import de.igslandstuhl.database.api.Semester;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Subject;
import de.igslandstuhl.database.api.Task;
import de.igslandstuhl.database.api.TaskLevel;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.Topic;
import de.igslandstuhl.database.api.curriculum.Curriculum;
import de.igslandstuhl.database.api.curriculum.CurriculumEnrollment;
import de.igslandstuhl.database.server.Server;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.lang.reflect.Field;
import java.security.SecureRandom;
import java.sql.Connection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Disposable A4b synthetic fixture. Uses domain APIs and refuses non-runner paths. */
public final class FixtureRunner {
    private FixtureRunner() {}

    public static void main(String[] args) throws Exception {
        if (!"1".equals(System.getenv("ARCANUM_ADMIN_E2E")) || !has(args, "--confirm-admin-e2e")) {
            throw new IllegalArgumentException("A4b fixture guard confirmation missing");
        }
        String runnerTemp = System.getenv("RUNNER_TEMP");
        if (runnerTemp == null || runnerTemp.isBlank()) throw new IllegalArgumentException("RUNNER_TEMP is required");
        Path temp = Path.of(runnerTemp).toRealPath();
        Path isolatedRoot = Path.of(System.getenv("ARCANUM_ADMIN_E2E_ROOT")).toAbsolutePath().normalize();
        if (!isolatedRoot.startsWith(temp) || !Files.isDirectory(isolatedRoot))
            throw new IllegalArgumentException("A4b temp root must be an existing directory under RUNNER_TEMP");
        Path databaseBase = Path.of(value(args, "--database-base")).toAbsolutePath().normalize();
        Path credentials = Path.of(value(args, "--credentials")).toAbsolutePath().normalize();
        String normalized = databaseBase.toString().toLowerCase();
        if (!databaseBase.startsWith(isolatedRoot) || !databaseBase.startsWith(temp) || normalized.contains("/prod/") || normalized.contains("/demo/")
                || normalized.contains("production") || normalized.contains("demonstration")
                || normalized.endsWith(".db")) {
            throw new IllegalArgumentException("A4b fixture refuses the supplied database path");
        }
        if (!credentials.startsWith(isolatedRoot) || !credentials.startsWith(temp) || credentials.toString().toLowerCase().contains("/prod/")
                || credentials.toString().toLowerCase().contains("/demo/")) {
            throw new IllegalArgumentException("A4b fixture refuses the credential output path");
        }
        Files.createDirectories(databaseBase.getParent());
        Files.createDirectories(credentials.getParent());

        System.out.println("A4b fixture: initialize isolated schema");
        Application app = new Application(new String[] {"--test-environment", "true", "--database", databaseBase.toString()});
        Field instance = Application.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, app);
        Server server = Server.getInstance();
        server.getConnection().createTables();
        server.getConnection().migrateTables();
        System.out.println("A4b fixture: isolated schema ready");

        String suffix = Long.toUnsignedString(new SecureRandom().nextLong(), 36);
        String adminPassword = password();
        String teacherAPassword = password();
        String teacherBPassword = password();
        String unusedTeacherPassword = password();
        String studentAPassword = password();
        String studentBPassword = password();
        String adminName = "a4b-admin-" + suffix;
        String teacherAEmail = "a4b-teacher-a-" + suffix + "@example.invalid";
        String teacherBEmail = "a4b-teacher-b-" + suffix + "@example.invalid";
        String unusedTeacherEmail = "a4b-unused-" + suffix + "@example.invalid";
        String studentAEmail = "a4b-student-a-" + suffix + "@example.invalid";
        String studentBEmail = "a4b-student-b-" + suffix + "@example.invalid";

        Admin admin = Admin.create(adminName, adminPassword);
        System.out.println("A4b fixture: admin account ready");
        Teacher teacherA = Teacher.registerTeacher("A4b", "Lehrkraft A", teacherAEmail, teacherAPassword);
        Teacher teacherB = Teacher.registerTeacher("A4b", "Lehrkraft B", teacherBEmail, teacherBPassword);
        Teacher unusedTeacher = Teacher.registerTeacher("A4b", "Unbenutzt", unusedTeacherEmail, unusedTeacherPassword);
        System.out.println("A4b fixture: teacher accounts ready");
        SchoolClass classA = SchoolClass.addClass("6a", 6);
        SchoolClass classD = SchoolClass.addClass("6d", 6);
        teacherA.addClass(classA);
        teacherA.addClass(classD);
        teacherB.addClass(classA);
        teacherB.addClass(classD);
        int studentAId = 710_000_000 + new SecureRandom().nextInt(80_000_000);
        int studentBId = studentAId == 789_999_999 ? studentAId - 1 : studentAId + 1;
        Student studentA = Student.registerStudentWithPassword(studentAId, "A4b", "Schüler A", studentAEmail,
                studentAPassword, classA, GraduationLevel.initialValue());
        Student studentB = Student.registerStudentWithPassword(studentBId, "A4b", "Schüler B", studentBEmail,
                studentBPassword, classD, GraduationLevel.initialValue());
        System.out.println("A4b fixture: student accounts ready");

        String yearLabel = "2098/99";
        SchoolYear year = SchoolYear.addSchoolYear(yearLabel, 39, 1);
        Semester semester = Semester.addSemester("2098_99_HJ1", 1, year);
        year.setCurrentSemester(semester);

        Curriculum.Actor actor = Curriculum.Actor.from(admin);
        CurriculumEnrollment enrollment = new CurriculumEnrollment(Curriculum.current());
        int regularSubject = enrollment.addSubjectWithType(actor, "A4b REGULAR " + suffix, "REGULAR", null);
        int wpfSubject = enrollment.addSubjectWithType(actor, "A4b WPF " + suffix, "INDIVIDUAL", "WPF");
        int religionSubject = enrollment.addSubjectWithType(actor, "A4b Religion/Ethik " + suffix,
                "INDIVIDUAL", "RELIGION_ETHIK");
        int alternativeWpfSubject = enrollment.addSubjectWithType(actor, "A4b WPF Alternative " + suffix,
                "INDIVIDUAL", "WPF");
        enrollment.assignGrade(actor, 6, semester.getId(), List.of(regularSubject), List.of(
                new CurriculumEnrollment.Teaching(classA.getId(), regularSubject, teacherA.getId()),
                new CurriculumEnrollment.Teaching(classD.getId(), regularSubject, teacherA.getId())));
        enrollment.assignIndividualTeacher(actor, 6, semester.getId(), wpfSubject, teacherA.getId());
        enrollment.assignIndividualTeacher(actor, 6, semester.getId(), religionSubject, teacherA.getId());
        enrollment.assignIndividualTeacher(actor, 6, semester.getId(), alternativeWpfSubject, teacherA.getId());
        enrollment.assignIndividual(actor, studentA.getId(), wpfSubject, classA.getId(), semester.getId(), "WPF", null);
        enrollment.assignIndividual(actor, studentB.getId(), wpfSubject, classD.getId(), semester.getId(), "WPF", null);
        enrollment.assignIndividual(actor, studentA.getId(), religionSubject, classA.getId(), semester.getId(), "RELIGION_ETHIK", null);
        enrollment.assignIndividual(actor, studentB.getId(), religionSubject, classD.getId(), semester.getId(), "RELIGION_ETHIK", null);
        System.out.println("A4b fixture: curriculum and CourseGroups ready");
        enrollment.assignClassTutors(actor, semester.getId(), classA.getId(), teacherA.getId(), teacherB.getId());
        enrollment.assignClassTutors(actor, semester.getId(), classD.getId(), teacherA.getId(), teacherB.getId());
        Topic historyTopic = Topic.addTopic("A4b history " + suffix, Subject.get(wpfSubject), 6, 1, semester);
        Task historyTask = Task.addTask(historyTopic, "A4b completed history " + suffix, TaskLevel.LEVEL1, 1);
        studentA.changeTaskStatus(historyTask, Task.STATUS_COMPLETED);
        System.out.println("A4b fixture: synthetic learning history ready");

        JsonObject root = new JsonObject();
        root.addProperty("adminUsername", adminName);
        root.addProperty("adminPassword", adminPassword);
        root.addProperty("teacherAUsername", teacherAEmail);
        root.addProperty("teacherAPassword", teacherAPassword);
        root.addProperty("teacherBUsername", teacherBEmail);
        root.addProperty("teacherBPassword", teacherBPassword);
        root.addProperty("unusedTeacherUsername", unusedTeacherEmail);
        root.addProperty("unusedTeacherPassword", unusedTeacherPassword);
        root.addProperty("unusedTeacherId", unusedTeacher.getId());
        root.addProperty("studentAUsername", studentAEmail);
        root.addProperty("studentAPassword", studentAPassword);
        root.addProperty("studentBUsername", studentBEmail);
        root.addProperty("studentBPassword", studentBPassword);
        root.addProperty("studentAId", studentA.getId());
        root.addProperty("studentBId", studentB.getId());
        root.addProperty("teacherAId", teacherA.getId());
        root.addProperty("teacherBId", teacherB.getId());
        root.addProperty("classAId", classA.getId());
        root.addProperty("classDId", classD.getId());
        root.addProperty("semester1Id", semester.getId());
        root.addProperty("regularSubjectId", regularSubject);
        root.addProperty("wpfSubjectId", wpfSubject);
        root.addProperty("religionSubjectId", religionSubject);
        root.addProperty("alternativeWpfSubjectId", alternativeWpfSubject);
        root.addProperty("historyTaskId", historyTask.getId());
        root.addProperty("historyTopicId", historyTopic.getId());
        Files.writeString(credentials, new GsonBuilder().disableHtmlEscaping().create().toJson(root));
        try {
            Files.setPosixFilePermissions(credentials, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // GitHub's Linux runner supports POSIX permissions; keep this portable for local safety checks.
        }
        try (Connection connection = server.getConnection().getSQLConnection()) {
            // Touch the connection and make sure the fixture is persisted before the service starts.
            if (connection.isClosed()) throw new IllegalStateException("Fixture database did not persist");
        }
        server.getConnection().close();
        System.out.println("A4b synthetic fixture prepared; credentials are in a mode-600 temp file.");
    }

    private static String password() {
        return "A4b!" + Long.toUnsignedString(new SecureRandom().nextLong(), 36) + "Z9";
    }
    private static boolean has(String[] args, String expected) {
        for (String arg : args) if (expected.equals(arg)) return true;
        return false;
    }
    private static String value(String[] args, String key) {
        for (int i=0; i<args.length-1; i++) if (key.equals(args[i])) return args[i+1];
        throw new IllegalArgumentException("Missing required option " + key);
    }
}
