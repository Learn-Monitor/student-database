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
            boolean copied=previous>0 && position==2;
            if(copied) copyFrame(c,previous,created);
            return Map.of("id",created,"label",value,"copiedFromSemesterId",copied?previous:0);
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

    public void activateSemester(Actor actor,int semesterId) throws SQLException {
        admin(actor);
        curriculum.transaction(c->{
            var semester=require(c,"SELECT id,school_year FROM semesters WHERE id=?",semesterId);
            int schoolYear=integer(semester,"school_year");
            require(c,"SELECT id FROM school_years WHERE id=?",schoolYear);
            write(c,"UPDATE school_years SET current_semester=? WHERE id=?",semesterId,schoolYear);
            return null;
        });
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
        write(c,"INSERT INTO curriculum_individual_assignments(student,semester,assignment_group,subject) SELECT student,?,assignment_group,subject FROM curriculum_individual_assignments WHERE semester=? ON CONFLICT DO NOTHING",to,from);
        write(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) SELECT ?,class,subject,teacher FROM curriculum_class_teachers WHERE semester=? ON CONFLICT DO NOTHING",to,from);
        write(c,"INSERT INTO curriculum_grade_teachers(semester,grade,subject,teacher) SELECT ?,grade,subject,teacher FROM curriculum_grade_teachers WHERE semester=? ON CONFLICT DO NOTHING",to,from);
    }

    private static boolean wpf(Connection c,int subject) throws SQLException {
        require(c,"SELECT id FROM subjects WHERE id=?",subject);
        return number(c,"SELECT COUNT(*) FROM curriculum_subject_types WHERE subject=? AND mode='INDIVIDUAL' AND assignment_group='WPF'",subject)>0;
    }
    private static boolean individual(Connection c,int subject) throws SQLException {
        require(c,"SELECT id FROM subjects WHERE id=?",subject);
        return Curriculum.individualSubject(c,subject);
    }
    private static String group(Connection c,int subject) throws SQLException {
        return Curriculum.assignmentGroup(c,subject);
    }
    public Map<String,Object> catalog(Actor actor) throws SQLException {
        admin(actor);
        return curriculum.transaction(c->Map.of(
            "subjects",rows(c,"SELECT s.id,s.name,CASE WHEN COALESCE(t.mode,CASE WHEN lower(s.name) LIKE '%religion%' OR lower(s.name) LIKE '%ethik%' OR lower(s.name) LIKE 'wpf %' THEN 'INDIVIDUAL' ELSE 'REGULAR' END)='INDIVIDUAL' THEN 1 ELSE 0 END AS wpf,COALESCE(t.mode,CASE WHEN lower(s.name) LIKE '%religion%' OR lower(s.name) LIKE '%ethik%' OR lower(s.name) LIKE 'wpf %' THEN 'INDIVIDUAL' ELSE 'REGULAR' END) AS mode,COALESCE(t.assignment_group,CASE WHEN lower(s.name) LIKE 'wpf %' THEN 'WPF' WHEN lower(s.name) LIKE '%religion%' OR lower(s.name) LIKE '%ethik%' THEN 'RELIGION_ETHIK' ELSE NULL END) AS assignmentGroup FROM subjects s LEFT JOIN curriculum_subject_types t ON t.subject=s.id ORDER BY s.name"),
            "classes",rows(c,"SELECT id,label,grade FROM classes WHERE active=1 AND id<>0 ORDER BY grade,label"),
            "semesters",rows(c,"SELECT s.id,s.label,s.school_year AS schoolYearId,CASE WHEN y.current_semester=s.id THEN 1 ELSE 0 END AS active FROM semesters s JOIN school_years y ON y.id=s.school_year ORDER BY s.school_year,s.position"),
            "teachers",rows(c,"SELECT id,first_name,last_name FROM teachers ORDER BY last_name,first_name"),
            "teaching",rows(c,"SELECT teacher AS teacherId,class AS classId,subject AS subjectId,semester AS semesterId FROM curriculum_class_teachers UNION SELECT teacher AS teacherId,NULL AS classId,subject AS subjectId,semester AS semesterId FROM curriculum_grade_teachers"),
            "gradeSubjects",rows(c,"SELECT grade,semester AS semesterId,subject AS subjectId FROM curriculum_grade_subjects")));
    }
    public void subjectType(Actor actor,int subject,boolean isWpf) throws SQLException {
        subjectType(actor,subject,isWpf?"INDIVIDUAL":"REGULAR",isWpf?"WPF":null);
    }
    public void subjectType(Actor actor,int subject,String mode,String assignmentGroup) throws SQLException {
        admin(actor);
        String normalizedMode=mode==null?"REGULAR":mode.strip().toUpperCase(Locale.ROOT);
        String normalizedGroup=assignmentGroup==null?null:assignmentGroup.strip().toUpperCase(Locale.ROOT);
        if(!normalizedMode.equals("REGULAR") && !normalizedMode.equals("INDIVIDUAL"))
            throw error(400,"invalid_input","Subject mode must be REGULAR or INDIVIDUAL.");
        if(normalizedMode.equals("INDIVIDUAL") && (normalizedGroup==null || normalizedGroup.isBlank()))
            throw error(400,"invalid_input","Individual subjects require an assignment group.");
        if(normalizedMode.equals("REGULAR")) normalizedGroup=null;
        final String finalMode=normalizedMode, finalGroup=normalizedGroup;
        curriculum.transaction(c->{
            require(c,"SELECT id FROM subjects WHERE id=?",subject);
            var current=rows(c,"SELECT mode,assignment_group FROM curriculum_subject_types WHERE subject=?",subject);
            if(!current.isEmpty() && Objects.equals(String.valueOf(current.get(0).get("mode")),finalMode)
                    && Objects.equals(current.get(0).get("assignment_group"),finalGroup)) return null;
            if(finalMode.equals("INDIVIDUAL") && number(c,"SELECT COUNT(*) FROM curriculum_grade_subjects WHERE subject=?",subject)>0)
                throw error(409,"context_conflict","A regular grade subject cannot become individual while grade assignments exist.");
            if(finalMode.equals("REGULAR") && number(c,"SELECT COUNT(*) FROM curriculum_individual_assignments WHERE subject=?",subject)>0)
                throw error(409,"context_conflict","Assigned individual subjects cannot become regular.");
            if(finalMode.equals("INDIVIDUAL") && number(c,"SELECT COUNT(*) FROM curriculum_individual_assignments WHERE subject=? AND assignment_group<>?",subject,finalGroup)>0)
                throw error(409,"context_conflict","Assigned individual subject cannot change assignment group.");
            write(c,"INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,?,?,?) ON CONFLICT(subject) DO UPDATE SET wpf=excluded.wpf,mode=excluded.mode,assignment_group=excluded.assignment_group",subject,finalGroup!=null&&finalGroup.equals("WPF")?1:0,finalMode,finalGroup);
            return null;
        });
    }
    public void removeGradeSubject(Actor actor,int grade,int semester,int subject) throws SQLException {
        admin(actor);
        curriculum.transaction(c->{
            long completed=number(c,"SELECT COUNT(*) FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.status<>0 AND p.subject=? AND p.grade=? AND p.semester=?",subject,grade,semester)
                    +number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE t.subject=? AND t.grade=? AND t.semester=?",subject,grade,semester);
            if(completed>0) throw error(409,"context_conflict","Subject has existing work in this semester and cannot be removed silently.");
            write(c,"DELETE FROM curriculum_grade_subjects WHERE grade=? AND semester=? AND subject=?",grade,semester,subject);
            write(c,"DELETE FROM curriculum_class_teachers WHERE semester=? AND subject=? AND class IN(SELECT id FROM classes WHERE grade=?)",semester,subject,grade);
            write(c,"DELETE FROM curriculum_grade_teachers WHERE semester=? AND grade=? AND subject=?",semester,grade,subject);
            write(c,"DELETE FROM student_curriculum_contexts WHERE semester=? AND subject=? AND grade=?",semester,subject,grade);
            write(c,"DELETE FROM curriculum_individual_assignments WHERE semester=? AND subject=?",semester,subject);
            return null;
        });
    }
    /** All classes and pupils are resolved afresh; a single invalid mapping rolls back the batch. */
    public Map<String,Object> assignGrade(Actor actor,int grade,int semester,List<Integer> subjects,List<Teaching> teaching) throws SQLException {
        admin(actor);
        if(grade<1 || grade>13 || subjects.isEmpty() || new HashSet<>(subjects).size()!=subjects.size())
            throw error(400,"invalid_input","Select a grade and distinct regular subjects.");
        return curriculum.transaction(c->{
            require(c,"SELECT id FROM semesters WHERE id=?",semester);
            for(int subject:subjects) if(individual(c,subject)) throw error(400,"invalid_input","Individual subjects must be assigned individually.");
            var classes=rows(c,"SELECT id FROM classes WHERE grade=? AND active=1 AND id<>0 ORDER BY id",grade);
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
                    require(c,"SELECT id FROM teachers WHERE id=?",scope.teacherId());
                    write(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,class,subject) DO UPDATE SET teacher=excluded.teacher",semester,classId,subject,scope.teacherId());
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
        return individualRoster(actor,classId,semester,"WPF");
    }
    public List<Map<String,Object>> individualRoster(Actor actor,int classId,int semester,String assignmentGroup) throws SQLException {
        admin(actor);
        String finalGroup=assignmentGroup==null?"WPF":assignmentGroup.strip().toUpperCase(Locale.ROOT);
        return curriculum.transaction(c->{require(c,"SELECT id FROM classes WHERE id=? AND active=1 AND id<>0",classId);require(c,"SELECT id FROM semesters WHERE id=?",semester);
            return rows(c,"SELECT s.id,s.first_name,s.last_name,w.subject AS subjectId,a.teacher AS teacherId FROM students s LEFT JOIN curriculum_individual_assignments w ON w.student=s.id AND w.semester=? AND w.assignment_group=? LEFT JOIN student_curriculum_contexts a ON a.student=s.id AND a.semester=w.semester AND a.subject=w.subject WHERE s.class=? ORDER BY s.last_name,s.first_name,s.id",semester,finalGroup,classId);
        });
    }
    public void assignWpf(Actor actor,int student,Scope scope,Integer expectedSubject) throws SQLException {
        assignIndividual(actor,student,scope,"WPF",expectedSubject);
    }
    public void assignIndividual(Actor actor,int student,Scope scope,String assignmentGroup,Integer expectedSubject) throws SQLException {
        admin(actor);
        String finalGroup=assignmentGroup==null?"WPF":assignmentGroup.strip().toUpperCase(Locale.ROOT);
        curriculum.transaction(c->{
            if(!individual(c,scope.subjectId()) || !group(c,scope.subjectId()).equals(finalGroup))
                throw error(400,"invalid_input","Select an individual subject from the requested assignment group.");
            var previous=rows(c,"SELECT subject FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group=?",student,scope.semesterId(),finalGroup);
            Integer old=previous.isEmpty()?null:integer(previous.get(0),"subject");
            if(!Objects.equals(old,expectedSubject))throw error(409,"context_conflict","Individual assignment changed; refresh the list.");
            if(old!=null && old!=scope.subjectId()) {
                long completed=number(c,"SELECT COUNT(*) FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.student=? AND x.status<>0 AND p.subject=? AND p.semester=?",student,old,scope.semesterId())
                    +number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE x.student=? AND t.subject=? AND t.semester=?",student,old,scope.semesterId());
                if(completed>0)throw error(409,"context_conflict","Individual subject with existing work requires an explicit transfer.");
            }
            int grade=integer(require(c,"SELECT grade FROM classes WHERE id=?",scope.classId()),"grade");
            require(c,"SELECT id FROM teachers WHERE id=?",scope.teacherId());
            write(c,"INSERT INTO curriculum_grade_teachers(semester,grade,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,grade,subject) DO UPDATE SET teacher=excluded.teacher",scope.semesterId(),grade,scope.subjectId(),scope.teacherId());
            Curriculum.assign(c,student,scope);
            var previousEnrollment=rows(c,"SELECT grade FROM curriculum_enrolled_students WHERE student=? AND semester=?",student,scope.semesterId());
            if(!previousEnrollment.isEmpty() && integer(previousEnrollment.get(0),"grade")!=grade)
                throw error(409,"context_conflict","Historical grade cannot be changed.");
            write(c,"INSERT INTO curriculum_enrolled_students(student,semester,grade) VALUES(?,?,?) ON CONFLICT(student,semester) DO NOTHING",student,scope.semesterId(),grade);
            write(c,"INSERT INTO curriculum_individual_assignments(student,semester,assignment_group,subject) VALUES(?,?,?,?) ON CONFLICT(student,semester,assignment_group) DO UPDATE SET subject=excluded.subject",student,scope.semesterId(),finalGroup,scope.subjectId());
            return null;
        });
    }
    static void requireSelectedWpf(Connection c,int student,int subject,int semester) throws SQLException {
        if(Curriculum.individualSubject(c,subject)) {
            String assignmentGroup=Curriculum.assignmentGroup(c,subject);
            if(number(c,"SELECT COUNT(*) FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group=? AND subject=?",student,semester,assignmentGroup,subject)==0)
                throw error(403,"forbidden","This individual subject is not assigned to the student.");
        }
    }
    /** null means an unmanaged legacy semester; its existing subject list remains available. */
    public List<Integer> studentSubjects(int student,int semester) throws SQLException {
        return curriculum.transaction(c->{
            boolean managed=number(c,"SELECT COUNT(*) FROM curriculum_enrolled_students WHERE student=? AND semester=?",student,semester)>0;
            if(!managed)return null;
            return rows(c,"SELECT a.subject FROM student_curriculum_contexts a LEFT JOIN curriculum_subject_types t ON t.subject=a.subject WHERE a.student=? AND a.semester=? AND (COALESCE(t.mode,'REGULAR')='REGULAR' OR EXISTS(SELECT 1 FROM curriculum_individual_assignments w WHERE w.student=a.student AND w.semester=a.semester AND w.assignment_group=t.assignment_group AND w.subject=a.subject)) ORDER BY a.subject",student,semester)
                .stream().map(r->integer(r,"subject")).toList();
        });
    }
    public List<Integer> unselectedWpf(int student,int semester) throws SQLException {
        return curriculum.transaction(c->rows(c,"SELECT subject FROM curriculum_subject_types t WHERE mode='INDIVIDUAL' AND assignment_group='WPF' AND NOT EXISTS(SELECT 1 FROM curriculum_individual_assignments w WHERE w.student=? AND w.semester=? AND w.assignment_group=t.assignment_group AND w.subject=t.subject)",student,semester).stream().map(r->integer(r,"subject")).toList());
    }
    public List<Integer> selectedWpf(int student,int semester) throws SQLException {
        return curriculum.transaction(c->rows(c,"SELECT subject FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group='WPF'",student,semester).stream().map(r->integer(r,"subject")).toList());
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
