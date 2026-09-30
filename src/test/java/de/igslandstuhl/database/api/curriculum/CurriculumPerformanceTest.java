package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.Student;
import de.igslandstuhl.database.server.sql.SQLiteConnection;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Disposable benchmark/regression coverage for the student catalog release read model. */
class CurriculumPerformanceTest {
    private static final int STUDENT=1, SUBJECT=1, SEMESTER=1, SCHOOL_CLASS=1, TEACHER=1;
    private static final int TOPICS=10, TASKS_PER_TOPIC=2;

    private static final class Metrics {
        long statements;
        long nanos;
        final List<String> sql=new ArrayList<>();
        final List<Long> durations=new ArrayList<>();
        void reset() { statements=0; nanos=0; sql.clear(); durations.clear(); }
        void record(String statement,long elapsed) { statements++; nanos+=elapsed; sql.add(statement); durations.add(elapsed); }
        long releaseStatements() {
            return sql.stream().filter(statement -> statement.contains("curriculum_task_releases")
                    || statement.contains("curriculum_topic_releases")
                    || statement.contains("flexible_task_releases")
                    || statement.contains("flexible_topic_releases")).count();
        }
        long releaseNanos() {
            long total=0;
            for(int i=0;i<sql.size();i++) if(sql.get(i).contains("curriculum_task_releases")
                    || sql.get(i).contains("curriculum_topic_releases")
                    || sql.get(i).contains("flexible_task_releases")
                    || sql.get(i).contains("flexible_topic_releases")) total+=durations.get(i);
            return total;
        }
    }

    private static final class CountingConnection extends SQLiteConnection {
        private final Metrics metrics=new Metrics();
        private final Connection counted;

        CountingConnection(Path path) throws SQLException {
            super(path.toString());
            counted=connectionProxy(super.getSQLConnection());
        }

        Metrics metrics() { return metrics; }

        @Override public Connection getSQLConnection() { return counted; }

        private Connection connectionProxy(Connection delegate) {
            InvocationHandler handler=(proxy,method,args)->{
                Object result;
                try { result=method.invoke(delegate,args); }
                catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                if("prepareStatement".equals(method.getName()) && args!=null && args.length>0 && args[0] instanceof String sql)
                    return statementProxy((PreparedStatement)result,sql);
                return result;
            };
            return (Connection)Proxy.newProxyInstance(Connection.class.getClassLoader(),new Class[]{Connection.class},handler);
        }

        private PreparedStatement statementProxy(PreparedStatement delegate,String sql) {
            InvocationHandler handler=(proxy,method,args)->{
                long started=System.nanoTime();
                try { return method.invoke(delegate,args); }
                catch(java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                finally {
                    if(method.getName().startsWith("execute")) metrics.record(sql,System.nanoTime()-started);
                }
            };
            return (PreparedStatement)Proxy.newProxyInstance(PreparedStatement.class.getClassLoader(),new Class[]{PreparedStatement.class},handler);
        }
    }

    private static void exec(Connection c,String sql,Object... args) throws SQLException {
        try(PreparedStatement statement=c.prepareStatement(sql)) {
            for(int i=0;i<args.length;i++) statement.setObject(i+1,args[i]);
            statement.executeUpdate();
        }
    }

    private static Student syntheticStudent() throws Exception {
        Constructor<Student> constructor=Student.class.getDeclaredConstructor(int.class,String.class,String.class,String.class,
                String.class,de.igslandstuhl.database.api.SchoolClass.class,de.igslandstuhl.database.api.GraduationLevel.class,boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(STUDENT,"Synthetic","Student","synthetic@example.invalid","unused",null,null,true);
    }

    private static void fixture(CountingConnection db) throws Exception {
        db.migrateTables();
        db.createTables();
        db.writeTransaction(c->{
            exec(c,"INSERT INTO subjects(id,name) VALUES(?,?)",SUBJECT,"Performance Subject");
            exec(c,"INSERT INTO school_years(id,label,week_count,current_week) VALUES(?, ?,39,1)",SEMESTER,"Performance Year");
            exec(c,"INSERT INTO semesters(id,label,position,school_year) VALUES(?,?,1,?)",SEMESTER,"Performance Semester",SEMESTER);
            exec(c,"INSERT INTO classes(id,label,grade) VALUES(?,?,5)",SCHOOL_CLASS,"5a");
            exec(c,"INSERT INTO teachers(id,first_name,last_name,email,password) VALUES(?,?,?,?,'unused')",TEACHER,"Synthetic","Teacher","teacher@example.invalid");
            exec(c,"INSERT INTO students(id,first_name,last_name,email,password,class,graduation_level) VALUES(?,?,?,?,?,?,1)",STUDENT,"Synthetic","Student","student@example.invalid","unused",SCHOOL_CLASS);
            exec(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?)",SEMESTER,SCHOOL_CLASS,SUBJECT,TEACHER);
            exec(c,"INSERT INTO curriculum_enrolled_students(student,semester,grade) VALUES(?,?,5)",STUDENT,SEMESTER);
            exec(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) VALUES(?,?,?,?,?,5)",STUDENT,SUBJECT,SEMESTER,TEACHER,SCHOOL_CLASS);
            for(int topic=1;topic<=TOPICS;topic++) {
                int topicId=100+topic;
                exec(c,"INSERT INTO topics(id,name,subject,grade,number,semester) VALUES(?,?,?,?,?,?)",topicId,"Topic "+topic,SUBJECT,5,topic,SEMESTER);
                exec(c,"INSERT INTO curriculum_topic_releases(teacher,class,subject,semester,topic,active) VALUES(?,?,?,?,?,?)",TEACHER,SCHOOL_CLASS,SUBJECT,SEMESTER,topicId,topic<=3?1:0);
                for(int stage=1;stage<=TASKS_PER_TOPIC;stage++) {
                    int taskId=1000+((topic-1)*TASKS_PER_TOPIC)+stage;
                    exec(c,"INSERT INTO tasks(id,topic,name,niveau,stage_number,tokens) VALUES(?,?,?,?,?,?)",taskId,topicId,"Stage "+taskId,stage,stage,5);
                }
            }
            return null;
        });
    }

    /** Executes the exact release-query pattern used by the pre-snapshot implementation. */
    private static void legacyReleaseChecks(CountingConnection db) throws Exception {
        db.writeTransaction(c->{
            for(int topic=1;topic<=TOPICS;topic++) {
                int topicId=100+topic;
                for(int stage=1;stage<=TASKS_PER_TOPIC;stage++) {
                    int taskId=1000+((topic-1)*TASKS_PER_TOPIC)+stage;
                    Curriculum.rows(c,"SELECT active FROM curriculum_task_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND task=?",
                            TEACHER,SCHOOL_CLASS,SUBJECT,SEMESTER,taskId);
                    Curriculum.number(c,"SELECT COUNT(*) FROM curriculum_topic_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND topic=? AND active=1",
                            TEACHER,SCHOOL_CLASS,SUBJECT,SEMESTER,topicId);
                }
                Curriculum.number(c,"SELECT COUNT(*) FROM curriculum_topic_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND topic=? AND active=1",
                        TEACHER,SCHOOL_CLASS,SUBJECT,SEMESTER,topicId);
            }
            return null;
        });
    }

    @Test void catalogUsesARequestScopedReleaseSnapshotInsteadOfPerStageQueries() throws Exception {
        Path directory=Files.createTempDirectory("arcanum-catalog-performance-");
        try(CountingConnection db=new CountingConnection(directory.resolve("catalog"))) {
            fixture(db);
            Curriculum service=new Curriculum(db);
            Student student=syntheticStudent();
            db.metrics().reset();
            long legacyStarted=System.nanoTime();
            legacyReleaseChecks(db);
            long legacyElapsed=System.nanoTime()-legacyStarted;
            long legacyReleaseQueries=db.metrics().releaseStatements();
            long legacyTotalQueries=db.metrics().statements;
            assertEquals(TASKS_PER_TOPIC*TOPICS*2+TOPICS,legacyReleaseQueries);
            db.metrics().reset();
            long started=System.nanoTime();
            Map<String,Object> catalog=service.studentCatalog(student,SUBJECT,SEMESTER);
            long elapsed=System.nanoTime()-started;
            long centralTasks=((List<?>)catalog.get("centralTasks")).size();
            assertEquals(3*TASKS_PER_TOPIC,centralTasks);
            assertEquals(4,db.metrics().releaseStatements(),"catalog release state should be loaded once per release table");
            assertTrue(db.metrics().statements < 45,"release snapshot must remove linear release-query growth");
            long fullCatalogQueriesBefore=legacyTotalQueries+(db.metrics().statements-db.metrics().releaseStatements());
            System.out.printf("PERF catalog tasks=%d topics=%d releaseChecksBefore=%d releaseQueriesBefore=%d releaseQueriesAfter=%d totalQueriesBefore=%d totalQueriesAfter=%d legacyReleaseMs=%.3f releaseDbMsAfter=%.3f dbMsAfter=%.3f elapsedMsAfter=%.3f%n",
                    TOPICS*TASKS_PER_TOPIC,TOPICS,TOPICS*TASKS_PER_TOPIC+TOPICS,legacyReleaseQueries,db.metrics().releaseStatements(),fullCatalogQueriesBefore,db.metrics().statements,
                    legacyElapsed/1_000_000d,db.metrics().releaseNanos()/1_000_000d,db.metrics().nanos/1_000_000d,elapsed/1_000_000d);
        } finally {
            try(var files=Files.walk(directory)) { files.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}}); }
        }
    }
}
