package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.*;
import de.igslandstuhl.database.server.Server;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import de.igslandstuhl.database.server.webserver.Status;
import org.junit.jupiter.api.*;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class CentralCurriculumImportTest {
    static final AtomicInteger sequence = new AtomicInteger(50000);
    SQLiteConnection db; Curriculum curriculum; CentralCurriculumImport importer;
    int id, math, german, semester1, semester2, teacherId, classId, studentId;
    Curriculum.Actor admin = new Curriculum.Actor(true, 0), teacher, studentActor = null;
    Curriculum.Scope scope;

    @BeforeEach void setup() throws Exception {
        db = Server.getInstance().getConnection(); db.createTables();
        curriculum = new Curriculum(db); importer = new CentralCurriculumImport(curriculum);
        id = sequence.getAndAdd(100); math = id; german = id + 1; semester1 = id; semester2 = id + 1; teacherId = id; classId = id; studentId = id;
        db.writeTransaction(c -> {
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", math, "Mathematik-" + id);
            exec(c, "INSERT INTO subjects(id,name) VALUES(?,?)", german, "Deutsch-" + id);
            exec(c, "INSERT INTO school_years(id,label,week_count,current_week) VALUES(?,?,39,1)", id, "Year-" + id);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,1,?)", semester1, "HJ1-" + id, id);
            exec(c, "INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,2,?)", semester2, "HJ2-" + id, id);
            exec(c, "INSERT INTO classes(id,label,grade) VALUES(?,?,5)", classId, "Class-" + id);
            exec(c, "INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,'Test','Teacher',?,'unused')", teacherId, "teacher-import-" + id + "@example.invalid");
            exec(c, "INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,'Test','Student',?,'unused',?,1)", studentId, "student-import-" + id + "@example.invalid", classId);
            exec(c, "INSERT INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)", teacherId, math);
            exec(c, "INSERT INTO teacher_classes(teacher_id,class_id) VALUES(?,?)", teacherId, classId);
            return null;
        });
        teacher = new Curriculum.Actor(false, teacherId); scope = new Curriculum.Scope(teacherId, math, classId, semester1);
        curriculum.assign(admin, studentId, scope);
    }

    static void exec(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement s = c.prepareStatement(sql)) { for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]); s.executeUpdate(); }
    }
    long scalar(String sql, Object... args) throws SQLException {
        try (PreparedStatement s = db.getSQLConnection().prepareStatement(sql)) { for (int i = 0; i < args.length; i++) s.setObject(i + 1, args[i]); try (ResultSet r = s.executeQuery()) { assertTrue(r.next()); return r.getLong(1); } }
    }
    String csv(String subject, int topic, String topicName, int stage, String stageName, int tokens) {
        return "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\n" + subject + ";" + topic + ";" + topicName + ";" + stage + ";" + topic + ".1." + stage + " " + stageName + ";" + tokens + "\n";
    }
    @SuppressWarnings("unchecked") List<Map<String,Object>> rows(Map<String,Object> result) { return (List<Map<String,Object>>) result.get("rows"); }

    @Test void gradeFiveSixLevelParserUsesMiddleNumberAndAllowsDifferentStageNumber() {
        assertEquals(TaskLevel.LEVEL1, CentralCurriculumImport.levelFromStageName(1, "5 M 1.1.3 Addition"));
        assertEquals(TaskLevel.LEVEL2, CentralCurriculumImport.levelFromStageName(1, "5 M 1.2.1 Rechengesetze"));
        assertEquals(TaskLevel.LEVEL3, CentralCurriculumImport.levelFromStageName(2, "6 DS 2.3.1 Familienfoto"));
        assertEquals(TaskLevel.LEVEL2, CentralCurriculumImport.levelFromStageName(2, "6 DS 2.2.1 Theorie"));
        assertThrows(CurriculumException.class, () -> CentralCurriculumImport.levelFromStageName(2, "6 DS 1.2.1 Falsch"));
        assertThrows(CurriculumException.class, () -> CentralCurriculumImport.levelFromStageName(2, "ohne Nummer"));
        assertThrows(CurriculumException.class, () -> CentralCurriculumImport.levelFromStageName(2, "2.4.1 Ungültig"));
        assertThrows(CurriculumException.class, () -> CentralCurriculumImport.levelFromStageName(2, "2.1.1 und 2.3.2 widersprüchlich"));
    }

    @Test void schemaAllowsSameTopicNumberInDifferentSemestersOnly() throws Exception {
        int first = curriculum.createTopic(admin, math, 5, semester1, 1, "Prozentrechnung");
        int second = curriculum.createTopic(admin, math, 5, semester2, 1, "Prozentrechnung");
        assertNotEquals(first, second);
        assertEquals(409, assertThrows(CurriculumException.class, () -> curriculum.createTopic(admin, math, 5, semester1, 1, "Zinsrechnung")).status);
    }

    @Test void stageNumberIsExplicitUniqueAndLegacyAutoNumbered() throws Exception {
        int topic = curriculum.createTopic(admin, math, 5, semester1, 2, "Terme");
        int first = curriculum.createCentralTask(topic, "Grundlagen", TaskLevel.LEVEL1, 1, 5);
        assertEquals(1, Task.get(first).getStageNumber());
        assertEquals(409, assertThrows(CurriculumException.class, () -> curriculum.createCentralTask(topic, "Doppelt", TaskLevel.LEVEL1, 1, 4)).status);
        int next = curriculum.createCentralTask(topic, "Legacy", TaskLevel.LEVEL3, 3);
        assertEquals(2, scalar("SELECT stage_number FROM tasks WHERE id=?", next));
    }

    @Test void upgradeDatabaseKeepsTopicsTasksCompletionsAndNumbersLegacyStages() throws Exception {
        var dir = Files.createTempDirectory("central-curriculum-upgrade");
        try (SQLiteConnection legacy = new SQLiteConnection(dir.resolve("legacy").toString())) {
            try (Statement s = legacy.getSQLConnection().createStatement()) {
                s.executeUpdate("CREATE TABLE subjects(id INTEGER PRIMARY KEY,name TEXT)");
                s.executeUpdate("CREATE TABLE semesters(id INTEGER PRIMARY KEY,label TEXT,position INTEGER,school_year INTEGER)");
                s.executeUpdate("CREATE TABLE topics(id INTEGER PRIMARY KEY,name TEXT NOT NULL,subject INTEGER NOT NULL,grade INTEGER NOT NULL,resource TEXT,number INTEGER NOT NULL,semester INTEGER,UNIQUE(name,subject,grade),UNIQUE(grade,subject,number))");
                s.executeUpdate("CREATE TABLE tasks(id INTEGER PRIMARY KEY,topic INTEGER NOT NULL,name TEXT NOT NULL,niveau INTEGER NOT NULL,tokens INTEGER NOT NULL,UNIQUE(topic,name))");
                s.executeUpdate("CREATE TABLE students(id INTEGER PRIMARY KEY,first_name TEXT,last_name TEXT,email TEXT,password TEXT,class INTEGER,graduation_level INTEGER)");
                s.executeUpdate("CREATE TABLE taskstats(student INTEGER NOT NULL,task INTEGER NOT NULL,status INTEGER NOT NULL,PRIMARY KEY(student,task))");
                s.executeUpdate("INSERT INTO subjects VALUES(1,'Alt')");
                s.executeUpdate("INSERT INTO semesters VALUES(1,'Alt HJ',1,1)");
                s.executeUpdate("INSERT INTO topics VALUES(1,'Alt Thema',1,5,NULL,1,1)");
                s.executeUpdate("INSERT INTO tasks VALUES(10,1,'B',2,6)");
                s.executeUpdate("INSERT INTO tasks VALUES(11,1,'A',1,5)");
                s.executeUpdate("INSERT INTO students VALUES(1,'A','B','s','p',0,1)");
                s.executeUpdate("INSERT INTO taskstats VALUES(1,10,2)");
            }
            legacy.migrateTables();
            try (Statement s = legacy.getSQLConnection().createStatement()) {
                try (ResultSet r = s.executeQuery("SELECT id,stage_number FROM tasks ORDER BY stage_number")) {
                    assertTrue(r.next()); assertEquals(11, r.getInt(1)); assertEquals(1, r.getInt(2));
                    assertTrue(r.next()); assertEquals(10, r.getInt(1)); assertEquals(2, r.getInt(2));
                }
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM topics")) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
                try (ResultSet r = s.executeQuery("SELECT COUNT(*) FROM taskstats WHERE student=1 AND task=10 AND status=2")) { assertTrue(r.next()); assertEquals(1, r.getInt(1)); }
            }
        }
    }

    @Test void csvPreviewParsesSemicolonCommaBomCrlfQuotesAndUmlauts() throws Exception {
        String subject = "Mathematik-" + id;
        for (String data : List.of(
                csv(subject, 1, "Prozentrechnung", 1, "Grundbegriffe", 5),
                "Fach,Themennummer,Themenname,Etappennummer,Etappenname,Münzen\n" + subject + ",1,Prozentrechnung,1,1.1.1 Grundbegriffe,5\n",
                "\ufeffFach;Themennummer;Themenname;Etappennummer;Etappenname;Münzen\r\n" + subject + ";1;Üben;1;\"1.1.1 Text; mit Trenner\";5\r\n",
                "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Münzen\n" + subject + ";1;Äpfel;1;\"1.1.1 Quote \"\" innen\";5\n")) {
            var preview = importer.preview(admin, 5, semester1, data);
            assertEquals(true, preview.get("canImport"));
            assertEquals(1, rows(preview).size());
            assertEquals(0, scalar("SELECT COUNT(*) FROM topics WHERE subject=? AND grade=5 AND semester=?", math, semester1));
        }
    }

    @Test void csvValidationReportsBadHeadersUnknownSubjectBadNumbersAndConflicts() throws Exception {
        assertEquals(400, assertThrows(CurriculumException.class, () -> importer.preview(admin, 5, semester1, "X;Y\n1;2\n")).status);
        String subject = "Mathematik-" + id;
        for (String data : List.of(
                csv("Unbekannt", 1, "T", 1, "E", 5),
                csv(subject, 0, "T", 1, "E", 5),
                csv(subject, 1, "T", 0, "E", 5),
                csv(subject, 1, "T", 1, "E", 106),
                "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\n" + subject + ";1;A;1;E;5\n" + subject + ";1;B;2;E;5\n",
                "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\n" + subject + ";1;A;1;E;5\n" + subject + ";1;A;1;Andere;5\n")) {
            var preview = importer.preview(admin, 5, semester1, data);
            assertEquals(false, preview.get("canImport"));
            assertFalse(((List<?>) preview.get("errors")).isEmpty());
        }
    }

    @Test void importIsAtomicIdempotentUpsertAndNeverDeletesMissingRows() throws Exception {
        String subject = "Mathematik-" + id;
        String initial = "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\n" + subject + ";1;Prozentrechnung;1;1.1.1 Grundbegriffe;5\nDeutsch-" + id + ";1;Argumentieren;1;1.1.1 Argumente;6\n";
        var result = importer.importCsv(admin, 5, semester1, initial);
        assertEquals(2, result.get("createdTopics")); assertEquals(2, result.get("createdStages"));
        assertEquals(2, scalar("SELECT COUNT(*) FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.semester=?", semester1));
        assertEquals(2, importer.importCsv(admin, 5, semester1, initial).get("unchangedStages"));
        String update = "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\n" + subject + ";1;Prozentrechnung neu;1;1.1.1 Grundbegriffe neu;7\n";
        importer.importCsv(admin, 5, semester1, update);
        assertEquals(2, scalar("SELECT COUNT(*) FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.semester=?", semester1));
        assertEquals(7, scalar("SELECT tokens FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND t.stage_number=1", math));
        String invalidLastLine = initial + subject + ";2;Fehler;1;Zu viel;106\n";
        assertEquals(400, assertThrows(CurriculumException.class, () -> importer.importCsv(admin, 5, semester1, invalidLastLine)).status);
        assertEquals(2, scalar("SELECT COUNT(*) FROM topics WHERE semester=?", semester1));
    }

    @Test void completedCentralAndFlexibleTokenChangesAreBlocked() throws Exception {
        String subject = "Mathematik-" + id;
        importer.importCsv(admin, 5, semester1, csv(subject, 1, "Prozentrechnung", 1, "Grundbegriffe", 5));
        int task = (int) scalar("SELECT t.id FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=?", math);
        Student.get(studentId).changeTaskStatus(Task.get(task), Task.STATUS_COMPLETED);
        var conflict = assertThrows(CurriculumException.class, () -> importer.importCsv(admin, 5, semester1, csv(subject, 1, "Prozentrechnung", 1, "Grundbegriffe", 10)));
        assertEquals(409, conflict.status); assertEquals(5, scalar("SELECT tokens FROM tasks WHERE id=?", task));
        var flexible = curriculum.create(teacher, scope, "Flex", 5); curriculum.complete(teacher, flexible.id(), studentId);
        assertEquals(409, assertThrows(CurriculumException.class, () -> curriculum.edit(teacher, flexible.id(), "Flex", 10)).status);
        assertEquals(5, scalar("SELECT tokens FROM flexible_tasks WHERE id=?", flexible.id()));
    }

    @Test void budgetChecksCentralAndExistingFlexibleContexts() throws Exception {
        String subject = "Mathematik-" + id;
        assertEquals(true, importer.preview(admin, 5, semester1, csv(subject, 1, "A", 1, "A", 100)).get("canImport"));
        var warning = importer.preview(admin, 5, semester1, csv(subject, 1, "A", 1, "A", 105));
        assertEquals(true, warning.get("canImport")); assertFalse(((List<?>) warning.get("warnings")).isEmpty());
        String tooMuch = "Fach;Themennummer;Themenname;Etappennummer;Etappenname;Muenzen\n" + subject + ";1;A;1;A;100\n" + subject + ";1;A;2;B;6\n";
        assertEquals(false, importer.preview(admin, 5, semester1, tooMuch).get("canImport"));
        var flexible = curriculum.create(teacher, scope, "Flex", 6);
        assertEquals(false, importer.preview(admin, 5, semester1, csv(subject, 1, "A", 1, "A", 100)).get("canImport"));
        assertEquals(409, assertThrows(CurriculumException.class, () -> importer.importCsv(admin, 5, semester1, csv(subject, 1, "A", 1, "A", 100))).status);
        assertEquals(0, scalar("SELECT COUNT(*) FROM topics WHERE semester=?", semester1));
        assertEquals(flexible.id(), scalar("SELECT id FROM flexible_tasks WHERE id=?", flexible.id()));
    }

    @Test void previewAndImportEndpointsAreAdminOnly() throws Exception {
        String body = "{\"grade\":5,\"semesterId\":" + semester1 + ",\"csv\":\"" + csv("Mathematik-" + id, 1, "A", 1, "A", 5).replace("\n", "\\n") + "\"}";
        Admin adminUser = Admin.create("central-import-admin-" + id, "unused");
        assertEquals(Status.OK, request(adminUser, "/preview-central-curriculum-import", body).getStatus());
        assertEquals(Status.OK, request(adminUser, "/import-central-curriculum", body).getStatus());
        assertEquals(Status.OK, request(adminUser, "/central-curriculum-overview", "{\"grade\":5,\"semesterId\":" + semester1 + "}").getStatus());
        assertEquals(Status.FORBIDDEN, request(Teacher.get(teacherId), "/preview-central-curriculum-import", body).getStatus());
        assertEquals(Status.FORBIDDEN, request(Student.get(studentId), "/import-central-curriculum", body).getStatus());
    }

    de.igslandstuhl.database.server.webserver.responses.PostResponse request(User user, String path, String body) {
        var rq = new de.igslandstuhl.database.server.webserver.requests.APIPostRequest(
                new de.igslandstuhl.database.server.webserver.requests.HttpHeader("POST " + path + " HTTP/1.1\r\nContent-Type: application/json\r\nContent-Length: " + body.length() + "\r\n"), body, "127.0.0.1", true) {
            @Override public User getUser() { return user; }
        };
        return de.igslandstuhl.database.server.webserver.handlers.CurriculumRequestHandler.handle(rq);
    }
    String responseBody(de.igslandstuhl.database.server.webserver.responses.PostResponse response) {
        var output = new ByteArrayOutputStream(); response.respond(new PrintStream(output)); return output.toString();
    }
}
