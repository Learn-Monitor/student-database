package tools.demo_e2e;

import de.igslandstuhl.database.Application;
import de.igslandstuhl.database.api.Admin;
import de.igslandstuhl.database.api.GraduationLevel;
import de.igslandstuhl.database.api.SchoolClass;
import de.igslandstuhl.database.api.SchoolYear;
import de.igslandstuhl.database.api.Semester;
import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.api.Teacher;
import de.igslandstuhl.database.api.User;
import de.igslandstuhl.database.api.curriculum.Curriculum;
import de.igslandstuhl.database.api.curriculum.CurriculumEnrollment;
import de.igslandstuhl.database.server.Server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.security.SecureRandom;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.Set;

/** One-shot DEMO fixture lifecycle. Never part of the web runtime. */
public final class DemoFixtureRunner {
    private static final Path DEMO_DB = Path.of("/srv/arcanum/demo/shared/database.db");
    private static Server server;
    private static String run;
    private static Path output;

    private record Fixture(int classId, int studentId, int tutor1, int tutor2, int foreignTutor,
                           int subjectTeacher, String admin, int semester, String t1Email, String t1Password,
                           String t2Email, String t2Password, String foreignEmail, String foreignPassword,
                           String subjectEmail, String subjectPassword, String studentEmail, String studentPassword,
                           String adminPassword) {}

    public static void main(String[] args) {
        int exitCode = 0;
        try {
            String mode = value(args, "--mode", "");
            run = value(args, "--run-id", "");
            String db = value(args, "--database", "");
            output = Path.of(value(args, "--output", "/tmp/arcanum-demo-fixture-" + run + ".json"));
            guard(mode, db, args);
            if ("create".equals(mode)) create();
            else if ("cleanup".equals(mode)) cleanup();
            else usage();
        } catch (Throwable failure) {
            failure.printStackTrace(System.err);
            exitCode = 1;
        } finally {
            closeQuietly();
        }
        // Server creates a non-daemon session cleanup thread. Exit only after
        // the database and web resources have been closed, including errors.
        System.exit(exitCode);
    }

    private static void guard(String mode, String db, String[] args) throws IOException {
        if (!Set.of("create", "cleanup").contains(mode)) usage();
        if (!has(args, "--confirm-demo-fixture")) fail("missing --confirm-demo-fixture");
        if (!"1".equals(System.getenv("ARCANUM_DEMO_FIXTURE"))) fail("ARCANUM_DEMO_FIXTURE=1 required");
        if (run.isBlank() || !run.matches("E2E_TUTOR_GRAD_[A-Za-z0-9_-]{8,64}")) fail("invalid run id");
        Path supplied = Path.of(db).toAbsolutePath().normalize();
        if (!supplied.equals(DEMO_DB)) fail("database must be exactly " + DEMO_DB);
        String lower = supplied.toString().toLowerCase();
        if (lower.contains("/prod/") || lower.contains("production")) fail("production path refused");
        if (!Files.isRegularFile(supplied)) fail("DEMO database does not exist");
    }

    private static void open() throws Exception {
        String configuredBase = DEMO_DB.toString().substring(0, DEMO_DB.toString().length() - 3);
        Application app = new Application(new String[]{"--test-environment", "true", "--database", configuredBase});
        Field instance = Application.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, app);
        server = Server.getInstance();
        server.getConnection().createTables();
        server.getConnection().migrateTables();
    }

    private static void create() throws Exception {
        open();
        // DEMO may use a configured current-semester pointer without date metadata.
        Semester semester = null;
        try (var statement = server.getConnection().getSQLConnection().prepareStatement(
                "SELECT current_semester FROM school_years WHERE current_semester IS NOT NULL ORDER BY id DESC LIMIT 1");
             var rows = statement.executeQuery()) {
            if (rows.next()) semester = Semester.get(rows.getInt(1));
        }
        if (semester == null) fail("DEMO has no active semester");
        String suffix = run.substring("E2E_TUTOR_GRAD_".length());
        String classLabel = ("12e2e" + suffix).toLowerCase();
        SchoolClass schoolClass = SchoolClass.getOrCreate(classLabel);
        schoolClass = schoolClass.setGrade(12);
        String p = randomPassword();
        String t1e = "e2e-tutor1-" + suffix.toLowerCase() + "@example.invalid";
        String t2e = "e2e-tutor2-" + suffix.toLowerCase() + "@example.invalid";
        String fe = "e2e-foreign-" + suffix.toLowerCase() + "@example.invalid";
        String se = "e2e-subject-" + suffix.toLowerCase() + "@example.invalid";
        String ue = "e2e-student-" + suffix.toLowerCase() + "@example.invalid";
        Teacher t1 = Teacher.registerTeacher("E2E", "Tutor One " + suffix, t1e, p);
        String p2 = randomPassword(); Teacher t2 = Teacher.registerTeacher("E2E", "Tutor Two " + suffix, t2e, p2);
        String pf = randomPassword(); Teacher foreign = Teacher.registerTeacher("E2E", "Foreign Tutor " + suffix, fe, pf);
        String ps = randomPassword(); Teacher subject = Teacher.registerTeacher("E2E", "Subject Teacher " + suffix, se, ps);
        t1.addClass(schoolClass); t2.addClass(schoolClass);
        SchoolClass foreignClass = SchoolClass.getOrCreate(("12other" + suffix).toLowerCase()).setGrade(12);
        foreign.addClass(foreignClass);
        String pa = randomPassword();
        Admin admin = Admin.create("e2e-admin-" + suffix.toLowerCase(), pa);
        int studentId = 900000000 + new SecureRandom().nextInt(80000000);
        String pu = randomPassword();
        Student student = Student.registerStudentWithPassword(studentId, "E2E", "Student " + suffix, ue, pu, schoolClass, GraduationLevel.LEVEL0);
        new CurriculumEnrollment(Curriculum.current()).assignClassTutors(
                Curriculum.Actor.from(admin), semester.getId(), schoolClass.getId(), t1.getId(), t2.getId());
        Fixture f = new Fixture(schoolClass.getId(), student.getId(), t1.getId(), t2.getId(), foreign.getId(), subject.getId(),
                admin.getUsername(), semester.getId(), t1e,p,t2e,p2,fe,pf,se,ps,ue,pu, pa);
        writeJson(f, admin.getUsername());
        System.out.println("created run=" + run + " output=" + output);
    }

    private static void cleanup() throws Exception {
        open();
        String suffix = run.substring("E2E_TUTOR_GRAD_".length()).toLowerCase();
        server.getConnection().writeTransaction(c -> {
            String like = "%" + suffix + "%";
            String[] statements = {
                "DELETE FROM student_graduation_history WHERE student IN (SELECT id FROM students WHERE email LIKE ?)",
                "DELETE FROM curriculum_class_tutors WHERE class IN (SELECT id FROM classes WHERE label LIKE ?)",
                "DELETE FROM teacher_classes WHERE class_id IN (SELECT id FROM classes WHERE label LIKE ?)",
                "DELETE FROM students WHERE email LIKE ?",
                "DELETE FROM teachers WHERE email LIKE ?",
                "DELETE FROM admins WHERE username LIKE ?",
                "DELETE FROM classes WHERE label LIKE ?"
            };
            for (String sql : statements) try (PreparedStatement s=c.prepareStatement(sql)) { s.setString(1, like); s.executeUpdate(); }
            return null;
        });
        System.out.println("cleaned run=" + run);
    }

    private static void writeJson(Fixture f, String adminUser) throws IOException {
        String json = "{\n" +
                "  \"runId\":\""+run+"\",\"database\":\""+DEMO_DB+"\",\n"+
                "  \"classId\":"+f.classId+",\"studentId\":"+f.studentId+",\"semesterId\":"+f.semester+",\n"+
                "  \"tutor1\":{\"id\":"+f.tutor1+",\"email\":\""+f.t1Email+"\",\"password\":\""+f.t1Password+"\"},\n"+
                "  \"tutor2\":{\"id\":"+f.tutor2+",\"email\":\""+f.t2Email+"\",\"password\":\""+f.t2Password+"\"},\n"+
                "  \"foreignTutor\":{\"id\":"+f.foreignTutor+",\"email\":\""+f.foreignEmail+"\",\"password\":\""+f.foreignPassword+"\"},\n"+
                "  \"subjectTeacher\":{\"id\":"+f.subjectTeacher+",\"email\":\""+f.subjectEmail+"\",\"password\":\""+f.subjectPassword+"\"},\n"+
                "  \"student\":{\"email\":\""+f.studentEmail+"\",\"password\":\""+f.studentPassword+"\"},\n"+
                "  \"admin\":{\"username\":\""+adminUser+"\",\"password\":\""+f.adminPassword+"\"}\n}";
        Files.writeString(output, json, StandardCharsets.UTF_8);
        try { Files.setPosixFilePermissions(output, EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE)); } catch (UnsupportedOperationException ignored) {}
    }
    private static String randomPassword() { return "E2E!" + Long.toUnsignedString(new SecureRandom().nextLong(), 36) + "A9"; }
    private static String value(String[] a,String k,String d){for(int i=0;i<a.length-1;i++)if(a[i].equals(k))return a[i+1];return d;}
    private static boolean has(String[] a,String k){for(String x:a)if(x.equals(k))return true;return false;}
    private static void closeQuietly() {
        if (server == null) return;
        // These one-shot tools never start the web listener. Closing the
        // database connection avoids the listener's unstarted-socket path.
        try { server.getConnection().close(); }
        catch (SQLException failure) { failure.printStackTrace(System.err); }
        finally { server = null; }
    }
    private static void fail(String s){throw new IllegalArgumentException("DEMO fixture refused: "+s);}
    private static void usage(){throw new IllegalArgumentException("usage: --mode create|cleanup --database /srv/arcanum/demo/shared/database.db --run-id E2E_TUTOR_GRAD_<id> --confirm-demo-fixture");}
}
