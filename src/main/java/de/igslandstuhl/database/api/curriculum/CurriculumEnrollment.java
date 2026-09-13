package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import static de.igslandstuhl.database.api.curriculum.Curriculum.*;
import java.util.regex.*;

/** Administrative enrollment and teacher publication, scoped to a semester. */
public final class CurriculumEnrollment {
    private final Curriculum curriculum;
    public CurriculumEnrollment(Curriculum curriculum) { this.curriculum=curriculum; }
    public record Teaching(int classId,int subjectId,int teacherId) {}

    /** Creates the next semester in chronological school-year order. */
    public Map<String,Object> createNextSemester(Actor actor) throws SQLException {
        admin(actor);
        return curriculum.transaction(c->{
            int previous=previousSemesterId(c);
            String value=nextLabel(c);
            Matcher m=Pattern.compile("(20\\d{2})_(\\d{2})_HJ([12])").matcher(value);
            if(!m.matches()) throw error(500,"invalid_state","Nächstes Halbjahr konnte nicht bestimmt werden.");
            int start=Integer.parseInt(m.group(1)), position=Integer.parseInt(m.group(3));
            String yearLabel=m.group(1)+"/"+m.group(2);
            var years=rows(c,"SELECT id FROM school_years WHERE label=?",yearLabel); int year;
            if(years.isEmpty()){write(c,"INSERT INTO school_years(label,week_count,current_week,start_date,end_date) VALUES(?,39,1,?,?)",yearLabel,start+"-08-01",(start+1)+"-07-31");year=(int)number(c,"SELECT last_insert_rowid()");} else year=integer(years.get(0),"id");
            write(c,"INSERT INTO semesters(label,position,school_year) VALUES(?,?,?)",value,position,year);
            int created=(int)number(c,"SELECT last_insert_rowid()");
            if(previous>0) copyFrame(c,previous,created);
            return Map.of("id",created,"label",value,"copiedFromSemesterId",previous);
        });
    }

    /** Kept for internal/legacy callers that explicitly provide a validated label. */
    public int createSemester(Actor actor,String label) throws SQLException {
        admin(actor);
        Matcher m=Pattern.compile("(20\\d{2})_(\\d{2})_HJ([12])").matcher(label==null?"":label.strip());
        if(!m.matches()) throw error(400,"invalid_input","Halbjahr muss das Format JJJJ_YY_HJ1 oder JJJJ_YY_HJ2 haben.");
        int start=Integer.parseInt(m.group(1)), position=Integer.parseInt(m.group(3)); String value=label.strip(), yearLabel=m.group(1)+"/"+m.group(2);
        return curriculum.transaction(c->{var years=rows(c,"SELECT id FROM school_years WHERE label=?",yearLabel); int year;
            if(years.isEmpty()){write(c,"INSERT INTO school_years(label,week_count,current_week,start_date,end_date) VALUES(?,39,1,?,?)",yearLabel,start+"-08-01",(start+1)+"-07-31");year=(int)number(c,"SELECT last_insert_rowid()");} else year=integer(years.get(0),"id");
            if(number(c,"SELECT COUNT(*) FROM semesters WHERE label=?",value)>0)throw error(409,"conflict","Dieses Halbjahr existiert bereits.");
            write(c,"INSERT INTO semesters(label,position,school_year) VALUES(?,?,?)",value,position,year);return (int)number(c,"SELECT last_insert_rowid()");});
    }

    private static String nextLabel(Connection c) throws SQLException {
        Pattern p=Pattern.compile("(20\\d{2})_(\\d{2})_HJ([12])");
        int bestStart=-1,bestPosition=0;
        for(var row:rows(c,"SELECT label FROM semesters")) {
            Matcher m=p.matcher(String.valueOf(row.get("label")));
            if(m.matches()) { int s=Integer.parseInt(m.group(1)), pos=Integer.parseInt(m.group(3)); if(s>bestStart || s==bestStart && pos>bestPosition){bestStart=s;bestPosition=pos;} }
        }
        if(bestStart<0) {
            LocalDate now=LocalDate.now();
            int start=now.getMonthValue()>=8?now.getYear():now.getYear()-1;
            return String.format("%04d_%02d_HJ1",start,(start+1)%100);
        }
        int start=bestPosition==1?bestStart:bestStart+1, pos=bestPosition==1?2:1;
        return String.format("%04d_%02d_HJ%d",start,(start+1)%100,pos);
    }

    private static int previousSemesterId(Connection c) throws SQLException {
        Pattern p=Pattern.compile("(20\\d{2})_(\\d{2})_HJ([12])"); int bestId=-1,bestStart=-1,bestPosition=-1;
        for(var row:rows(c,"SELECT id,label FROM semesters")) { Matcher m=p.matcher(String.valueOf(row.get("label"))); if(m.matches()) { int s=Integer.parseInt(m.group(1)),pos=Integer.parseInt(m.group(3)); if(s>bestStart || s==bestStart&&pos>bestPosition){bestStart=s;bestPosition=pos;bestId=integer(row,"id");} } }
        return bestId;
    }

    private static void copyFrame(Connection c,int from,int to) throws SQLException {
        write(c,"INSERT INTO curriculum_grade_subjects(grade,semester,subject) SELECT grade,?,subject FROM curriculum_grade_subjects WHERE semester=? ON CONFLICT DO NOTHING",to,from);
        write(c,"INSERT INTO curriculum_enrolled_students(student,semester,grade) SELECT student,?,grade FROM curriculum_enrolled_students WHERE semester=? ON CONFLICT DO NOTHING",to,from);
        write(c,"INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade) SELECT student,subject,?,teacher,class,grade FROM student_curriculum_contexts WHERE semester=? ON CONFLICT DO NOTHING",to,from);
        write(c,"INSERT INTO curriculum_wpf_assignments(student,semester,subject) SELECT student,?,subject FROM curriculum_wpf_assignments WHERE semester=? ON CONFLICT DO NOTHING",to,from);
    }

    private static boolean wpf(Connection c,int subject) throws SQLException {
        require(c,"SELECT id FROM subjects WHERE id=?",subject);
        return number(c,"SELECT COUNT(*) FROM curriculum_subject_types WHERE subject=? AND wpf=1",subject)>0;
    }
    public Map<String,Object> catalog(Actor actor) throws SQLException {
        admin(actor);
        return curriculum.transaction(c->Map.of(
            "subjects",rows(c,"SELECT s.id,s.name,COALESCE(t.wpf,CASE WHEN lower(s.name) LIKE '%religion%' OR lower(s.name) LIKE '%ethik%' OR lower(s.name) LIKE 'wpf %' THEN 1 ELSE 0 END) AS wpf FROM subjects s LEFT JOIN curriculum_subject_types t ON t.subject=s.id ORDER BY s.name"),
            "classes",rows(c,"SELECT id,label,grade FROM classes ORDER BY grade,label"),
            "semesters",rows(c,"SELECT id,label FROM semesters ORDER BY school_year,position"),
            "teachers",rows(c,"SELECT id,first_name,last_name FROM teachers ORDER BY last_name,first_name"),
            "teaching",rows(c,"SELECT tc.teacher_id AS teacherId,tc.class_id AS classId,ts.subject_id AS subjectId FROM teacher_classes tc JOIN teacher_subjects ts ON ts.teacher_id=tc.teacher_id"),
            "gradeSubjects",rows(c,"SELECT grade,semester AS semesterId,subject AS subjectId FROM curriculum_grade_subjects")));
    }
    public void subjectType(Actor actor,int subject,boolean isWpf) throws SQLException {
        admin(actor);
        curriculum.transaction(c->{
            if(wpf(c,subject)==isWpf) return null;
            if(isWpf && number(c,"SELECT COUNT(*) FROM curriculum_grade_subjects WHERE subject=?",subject)>0)
                throw error(409,"context_conflict","A regular grade subject cannot become WPF while grade assignments exist.");
            if(!isWpf && number(c,"SELECT COUNT(*) FROM curriculum_wpf_assignments WHERE subject=?",subject)>0)
                throw error(409,"context_conflict","Assigned WPF cannot become a regular subject.");
            write(c,"INSERT INTO curriculum_subject_types(subject,wpf) VALUES(?,?) ON CONFLICT(subject) DO UPDATE SET wpf=excluded.wpf",subject,isWpf?1:0);
            return null;
        });
    }
    public void removeGradeSubject(Actor actor,int grade,int semester,int subject) throws SQLException { admin(actor); curriculum.transaction(c->{write(c,"DELETE FROM curriculum_grade_subjects WHERE grade=? AND semester=? AND subject=?",grade,semester,subject);return null;}); }
    /** All classes and pupils are resolved afresh; a single invalid mapping rolls back the batch. */
    public Map<String,Object> assignGrade(Actor actor,int grade,int semester,List<Integer> subjects,List<Teaching> teaching) throws SQLException {
        admin(actor);
        if(grade<1 || grade>13 || subjects.isEmpty() || new HashSet<>(subjects).size()!=subjects.size())
            throw error(400,"invalid_input","Select a grade and distinct regular subjects.");
        return curriculum.transaction(c->{
            require(c,"SELECT id FROM semesters WHERE id=?",semester);
            for(int subject:subjects) if(wpf(c,subject)) throw error(400,"invalid_input","WPF must be assigned individually.");
            var classes=rows(c,"SELECT id FROM classes WHERE grade=? ORDER BY id",grade);
            if(classes.isEmpty()) throw error(409,"context_conflict","No classes exist in this grade.");
            Set<String> expected=new HashSet<>();for(var cl:classes) for(int subject:subjects)expected.add(integer(cl,"id")+":"+subject);
            Map<String,Teaching> mappings=new HashMap<>();
            for(var item:teaching) if(mappings.put(item.classId()+":"+item.subjectId(),item)!=null)
                throw error(400,"invalid_input","Duplicate teaching assignment.");
            if(!mappings.keySet().equals(expected)) throw error(409,"context_conflict","Every class and subject needs an explicit teacher; refresh the class list.");
            int assignments=0,students=0;
            for(var cl:classes) {
                int classId=integer(cl,"id");var pupils=rows(c,"SELECT id FROM students WHERE class=? ORDER BY id",classId);students+=pupils.size();
                for(int subject:subjects) {
                    var scope=new Scope(mappings.get(classId+":"+subject).teacherId(),subject,classId,semester);
                    authorize(c,new Actor(false,scope.teacherId()),scope,true);
                    for(var pupil:pupils) { Curriculum.assign(c,integer(pupil,"id"),scope);assignments++; }
                }
                for(var pupil:pupils) {
                    int student=integer(pupil,"id");
                    var previous=rows(c,"SELECT grade FROM curriculum_enrolled_students WHERE student=? AND semester=?",student,semester);
                    if(!previous.isEmpty() && integer(previous.get(0),"grade")!=grade) throw error(409,"context_conflict","Historical grade cannot be changed.");
                    write(c,"INSERT INTO curriculum_enrolled_students(student,semester,grade) VALUES(?,?,?) ON CONFLICT(student,semester) DO NOTHING",student,semester,grade);
                }
            }
            for(int subject:subjects)write(c,"INSERT INTO curriculum_grade_subjects(grade,semester,subject) VALUES(?,?,?) ON CONFLICT DO NOTHING",grade,semester,subject);
            return Map.of("students",students,"assignments",assignments,"classes",classes.size());
        });
    }
    public List<Map<String,Object>> wpfRoster(Actor actor,int classId,int semester) throws SQLException {
        admin(actor);
        return curriculum.transaction(c->{require(c,"SELECT id FROM classes WHERE id=?",classId);require(c,"SELECT id FROM semesters WHERE id=?",semester);
            return rows(c,"SELECT s.id,s.first_name,s.last_name,w.subject AS subjectId,a.teacher AS teacherId FROM students s LEFT JOIN curriculum_wpf_assignments w ON w.student=s.id AND w.semester=? LEFT JOIN student_curriculum_contexts a ON a.student=s.id AND a.semester=w.semester AND a.subject=w.subject WHERE s.class=? ORDER BY s.last_name,s.first_name,s.id",semester,classId);
        });
    }
    public void assignWpf(Actor actor,int student,Scope scope,Integer expectedSubject) throws SQLException {
        admin(actor);
        curriculum.transaction(c->{
            if(!wpf(c,scope.subjectId()))throw error(400,"invalid_input","Select a WPF subject.");
            var previous=rows(c,"SELECT subject FROM curriculum_wpf_assignments WHERE student=? AND semester=?",student,scope.semesterId());
            Integer old=previous.isEmpty()?null:integer(previous.get(0),"subject");
            if(!Objects.equals(old,expectedSubject))throw error(409,"context_conflict","WPF assignment changed; refresh the list.");
            if(old!=null && old!=scope.subjectId()) {
                long completed=number(c,"SELECT COUNT(*) FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.student=? AND x.status<>0 AND p.subject=? AND p.semester=?",student,old,scope.semesterId())
                    +number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE x.student=? AND t.subject=? AND t.semester=?",student,old,scope.semesterId());
                if(completed>0)throw error(409,"context_conflict","WPF with existing work requires an explicit transfer.");
            }
            Curriculum.assign(c,student,scope);
            write(c,"INSERT INTO curriculum_wpf_assignments(student,semester,subject) VALUES(?,?,?) ON CONFLICT(student,semester) DO UPDATE SET subject=excluded.subject",student,scope.semesterId(),scope.subjectId());
            return null;
        });
    }
    static void requireSelectedWpf(Connection c,int student,int subject,int semester) throws SQLException {
        if(wpf(c,subject) && number(c,"SELECT COUNT(*) FROM curriculum_wpf_assignments WHERE student=? AND semester=? AND subject=?",student,semester,subject)==0)
            throw error(403,"forbidden","This WPF is not assigned to the student.");
    }
    /** null means an unmanaged legacy semester; its existing subject list remains available. */
    public List<Integer> studentSubjects(int student,int semester) throws SQLException {
        return curriculum.transaction(c->{
            boolean managed=number(c,"SELECT COUNT(*) FROM curriculum_enrolled_students WHERE student=? AND semester=?",student,semester)>0;
            if(!managed)return null;
            return rows(c,"SELECT a.subject FROM student_curriculum_contexts a LEFT JOIN curriculum_subject_types t ON t.subject=a.subject WHERE a.student=? AND a.semester=? AND (COALESCE(t.wpf,0)=0 OR EXISTS(SELECT 1 FROM curriculum_wpf_assignments w WHERE w.student=a.student AND w.semester=a.semester AND w.subject=a.subject)) ORDER BY a.subject",student,semester)
                .stream().map(r->integer(r,"subject")).toList();
        });
    }
    public List<Integer> unselectedWpf(int student,int semester) throws SQLException {
        return curriculum.transaction(c->rows(c,"SELECT subject FROM curriculum_subject_types t WHERE wpf=1 AND NOT EXISTS(SELECT 1 FROM curriculum_wpf_assignments w WHERE w.student=? AND w.semester=? AND w.subject=t.subject)",student,semester).stream().map(r->integer(r,"subject")).toList());
    }
    public List<Integer> selectedWpf(int student,int semester) throws SQLException {
        return curriculum.transaction(c->rows(c,"SELECT subject FROM curriculum_wpf_assignments WHERE student=? AND semester=?",student,semester).stream().map(r->integer(r,"subject")).toList());
    }
    static boolean released(Connection c,Scope scope,int task,int topic) throws SQLException {
        var override=rows(c,"SELECT active FROM curriculum_task_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND task=?",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),task);
        if(!override.isEmpty())return integer(override.get(0),"active")==1;
        return topicReleased(c,scope,topic);
    }
    static boolean topicReleased(Connection c,Scope scope,int topic) throws SQLException {
        return number(c,"SELECT COUNT(*) FROM curriculum_topic_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND topic=? AND active=1",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),topic)>0;
    }
    public Map<String,Object> releases(Actor actor,Scope scope) throws SQLException {
        return curriculum.transaction(c->{int grade=authorize(c,new Actor(false,scope.teacherId()),scope,false);authorize(c,actor,scope,false);
            var topics=rows(c,"SELECT id,name FROM topics WHERE subject=? AND grade=? AND semester=? ORDER BY number,id",scope.subjectId(),grade,scope.semesterId());
            var tasks=rows(c,"SELECT t.id,t.name,t.topic AS topicId FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=? ORDER BY p.number,t.id",scope.subjectId(),grade,scope.semesterId());
            for(var topic:topics)topic.put("active",topicReleased(c,scope,integer(topic,"id")));
            for(var task:tasks)task.put("active",released(c,scope,integer(task,"id"),integer(task,"topicId")));
            return Map.of("topics",topics,"tasks",tasks);
        });
    }
    public void release(Actor actor,Scope scope,Integer topic,Integer task,boolean active) throws SQLException {
        if((topic==null)==(task==null))throw error(400,"invalid_input","Select exactly one topic or task.");
        curriculum.transaction(c->{int grade=authorize(c,new Actor(false,scope.teacherId()),scope,false);authorize(c,actor,scope,false);
            int topicId=topic!=null?topic:integer(require(c,"SELECT topic FROM tasks WHERE id=?",task),"topic");
            require(c,"SELECT id FROM topics WHERE id=? AND subject=? AND grade=? AND semester=?",topicId,scope.subjectId(),grade,scope.semesterId());
            String table=topic!=null?"curriculum_topic_releases":"curriculum_task_releases",column=topic!=null?"topic":"task";
            write(c,"INSERT INTO "+table+"(teacher,class,subject,semester,"+column+",active) VALUES(?,?,?,?,?,?) ON CONFLICT(teacher,class,subject,semester,"+column+") DO UPDATE SET active=excluded.active",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),topic!=null?topic:task,active?1:0);
            if(topic!=null)write(c,"DELETE FROM curriculum_task_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND task IN(SELECT id FROM tasks WHERE topic=?)",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),topic);
            return null;
        });
    }
    public boolean canAccessTask(int student,int task,boolean includeCompleted) throws SQLException {
        return curriculum.transaction(c->{
            var t=require(c,"SELECT t.topic,p.subject,p.semester FROM tasks t JOIN topics p ON p.id=t.topic WHERE t.id=?",task);
            if(t.get("semester")==null)return true;
            int subject=integer(t,"subject"),semester=integer(t,"semester");
            requireSelectedWpf(c,student,subject,semester);
            var assignments=rows(c,"SELECT teacher,class,grade FROM student_curriculum_contexts WHERE student=? AND subject=? AND semester=?",student,subject,semester);
            if(assignments.isEmpty())return false;
            var scope=assignmentScope(assignments.get(0),subject,semester);
            return released(c,scope,task,integer(t,"topic")) || (includeCompleted && number(c,"SELECT COUNT(*) FROM taskstats WHERE student=? AND task=? AND status=2",student,task)>0);
        });
    }
}
