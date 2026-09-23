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
    private record CentralCacheUpdate(int studentId,int taskId) {}

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
            "teaching",rows(c,"SELECT teacher AS teacherId,class AS classId,NULL AS grade,subject AS subjectId,semester AS semesterId FROM curriculum_class_teachers UNION SELECT teacher AS teacherId,NULL AS classId,grade,subject AS subjectId,semester AS semesterId FROM curriculum_grade_teachers"),
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
    /** Only submitted class/subject mappings are written; a single invalid mapping rolls back the batch. */
    public Map<String,Object> assignGrade(Actor actor,int grade,int semester,List<Integer> subjects,List<Teaching> teaching) throws SQLException {
        admin(actor);
        if(grade<1 || grade>13 || subjects.isEmpty() || new HashSet<>(subjects).size()!=subjects.size())
            throw error(400,"invalid_input","Select a grade and distinct regular subjects.");
        return curriculum.transaction(c->{
            require(c,"SELECT id FROM semesters WHERE id=?",semester);
            var classes=rows(c,"SELECT id FROM classes WHERE grade=? AND active=1 AND id<>0 ORDER BY id",grade);
            if(classes.isEmpty()) throw error(409,"context_conflict","No classes exist in this grade.");
            Set<Integer> validClasses=new HashSet<>();for(var cl:classes)validClasses.add(integer(cl,"id"));
            Set<Integer> validSubjects=new HashSet<>(subjects);
            for(int subject:subjects) if(individual(c,subject)) throw error(400,"invalid_input","Individual subjects must be assigned individually.");
            Map<String,Teaching> mappings=new HashMap<>();
            for(var item:teaching) {
                if(!validClasses.contains(item.classId())) throw error(400,"invalid_input","Class is not part of the selected grade.");
                if(!validSubjects.contains(item.subjectId())) throw error(400,"invalid_input","Subject is not part of the selected regular subjects.");
                require(c,"SELECT id FROM teachers WHERE id=?",item.teacherId());
                if(mappings.put(item.classId()+":"+item.subjectId(),item)!=null)
                    throw error(400,"invalid_input","Duplicate teaching assignment.");
            }
            int assignments=0,students=0;
            Set<Integer> usedSubjects=new HashSet<>();
            for(var item:mappings.values()) {
                int classId=item.classId(),subject=item.subjectId();
                var pupils=rows(c,"SELECT id FROM students WHERE class=? ORDER BY id",classId);students+=pupils.size();
                var scope=new Scope(item.teacherId(),subject,classId,semester);usedSubjects.add(subject);
                    write(c,"INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,class,subject) DO UPDATE SET teacher=excluded.teacher",semester,classId,subject,scope.teacherId());
                    authorize(c,new Actor(false,scope.teacherId()),scope,true);
                    for(var pupil:pupils) { Curriculum.assign(c,integer(pupil,"id"),scope);assignments++; }
                for(var pupil:pupils) {
                    int student=integer(pupil,"id");
                    var previous=rows(c,"SELECT grade FROM curriculum_enrolled_students WHERE student=? AND semester=?",student,semester);
                    if(!previous.isEmpty() && integer(previous.get(0),"grade")!=grade) throw error(409,"context_conflict","Historical grade cannot be changed.");
                    write(c,"INSERT INTO curriculum_enrolled_students(student,semester,grade) VALUES(?,?,?) ON CONFLICT(student,semester) DO NOTHING",student,semester,grade);
                }
            }
            for(int subject:usedSubjects)write(c,"INSERT INTO curriculum_grade_subjects(grade,semester,subject) VALUES(?,?,?) ON CONFLICT DO NOTHING",grade,semester,subject);
            return Map.of("students",students,"assignments",assignments,"classes",mappings.size());
        });
    }
    public List<Map<String,Object>> wpfRoster(Actor actor,int classId,int semester) throws SQLException {
        return individualRoster(actor,classId,semester,"WPF");
    }
    public List<Map<String,Object>> individualRoster(Actor actor,int classId,int semester,String assignmentGroup) throws SQLException {
        admin(actor);
        String finalGroup=assignmentGroup==null?"WPF":assignmentGroup.strip().toUpperCase(Locale.ROOT);
        return curriculum.transaction(c->{require(c,"SELECT id FROM classes WHERE id=? AND active=1 AND id<>0",classId);require(c,"SELECT id FROM semesters WHERE id=?",semester);
            return rows(c,"SELECT s.id,s.first_name,s.last_name,w.subject AS subjectId FROM students s LEFT JOIN curriculum_individual_assignments w ON w.student=s.id AND w.semester=? AND w.assignment_group=? WHERE s.class=? ORDER BY s.last_name,s.first_name,s.id",semester,finalGroup,classId);
        });
    }
    public void assignWpf(Actor actor,int student,Scope scope,Integer expectedSubject) throws SQLException {
        assignIndividual(actor,student,scope.subjectId(),scope.classId(),scope.semesterId(),"WPF",expectedSubject);
    }
    public void assignIndividual(Actor actor,int student,int subject,int classId,int semester,String assignmentGroup,Integer expectedSubject) throws SQLException {
        admin(actor);
        String finalGroup=assignmentGroup==null?"WPF":assignmentGroup.strip().toUpperCase(Locale.ROOT);
        curriculum.transaction(c->{
            if(!individual(c,subject) || !group(c,subject).equals(finalGroup))
                throw error(400,"invalid_input","Select an individual subject from the requested assignment group.");
            int grade=integer(require(c,"SELECT grade FROM classes WHERE id=? AND active=1",classId),"grade");
            var canonical=rows(c,"SELECT teacher FROM curriculum_grade_teachers WHERE semester=? AND grade=? AND subject=?",semester,grade,subject);
            if(canonical.isEmpty()) throw error(409,"context_conflict","No grade-wide teacher is assigned for this individual subject.");
            int teacher=integer(canonical.get(0),"teacher");
            var scope=new Scope(teacher,subject,classId,semester);
            var previous=rows(c,"SELECT subject FROM curriculum_individual_assignments WHERE student=? AND semester=? AND assignment_group=?",student,semester,finalGroup);
            Integer old=previous.isEmpty()?null:integer(previous.get(0),"subject");
            if(!Objects.equals(old,expectedSubject))throw error(409,"context_conflict","Individual assignment changed; refresh the list.");
            if(old!=null && old!=scope.subjectId()) {
                long completed=number(c,"SELECT COUNT(*) FROM taskstats x JOIN tasks t ON t.id=x.task JOIN topics p ON p.id=t.topic WHERE x.student=? AND x.status<>0 AND p.subject=? AND p.semester=?",student,old,scope.semesterId())
                    +number(c,"SELECT COUNT(*) FROM completed_flexible_tasks x JOIN flexible_tasks t ON t.id=x.flexible_task WHERE x.student=? AND t.subject=? AND t.semester=?",student,old,semester);
                if(completed>0)throw error(409,"context_conflict","Individual subject with existing work requires an explicit transfer.");
            }
            Curriculum.assign(c,student,scope);
            var previousEnrollment=rows(c,"SELECT grade FROM curriculum_enrolled_students WHERE student=? AND semester=?",student,semester);
            if(!previousEnrollment.isEmpty() && integer(previousEnrollment.get(0),"grade")!=grade)
                throw error(409,"context_conflict","Historical grade cannot be changed.");
            write(c,"INSERT INTO curriculum_enrolled_students(student,semester,grade) VALUES(?,?,?) ON CONFLICT(student,semester) DO NOTHING",student,semester,grade);
            write(c,"INSERT INTO curriculum_individual_assignments(student,semester,assignment_group,subject) VALUES(?,?,?,?) ON CONFLICT(student,semester,assignment_group) DO UPDATE SET subject=excluded.subject",student,semester,finalGroup,subject);
            return null;
        });
    }
    /* Compatibility overload: the teacher in the old scope is ignored. */
    public void assignIndividual(Actor actor,int student,Scope scope,String assignmentGroup,Integer expectedSubject) throws SQLException {
        assignIndividual(actor,student,scope.subjectId(),scope.classId(),scope.semesterId(),assignmentGroup,expectedSubject);
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
    static boolean flexibleReleased(Connection c,Scope scope,int task,Integer topic) throws SQLException {
        var override=rows(c,"SELECT active FROM flexible_task_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND flexible_task=?",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),task);
        if(!override.isEmpty())return integer(override.get(0),"active")==1;
        return topic!=null && flexibleTopicReleased(c,scope,topic);
    }
    static boolean flexibleTopicReleased(Connection c,Scope scope,int topic) throws SQLException {
        return number(c,"SELECT COUNT(*) FROM flexible_topic_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND flexible_topic=? AND active=1",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),topic)>0;
    }
    public Map<String,Object> releases(Actor actor,Scope scope) throws SQLException {
        return curriculum.transaction(c->{int grade=authorize(c,new Actor(false,scope.teacherId()),scope,false);authorize(c,actor,scope,false);
            var topics=rows(c,"SELECT id,name FROM topics WHERE subject=? AND grade=? AND semester=? ORDER BY number,id",scope.subjectId(),grade,scope.semesterId());
            var tasks=rows(c,"SELECT t.id,t.name,t.topic AS topicId FROM tasks t JOIN topics p ON p.id=t.topic WHERE p.subject=? AND p.grade=? AND p.semester=? ORDER BY p.number,t.id",scope.subjectId(),grade,scope.semesterId());
            for(var topic:topics)topic.put("active",topicReleased(c,scope,integer(topic,"id")));
            for(var task:tasks)task.put("active",released(c,scope,integer(task,"id"),integer(task,"topicId")));
            var flexibleTopics=rows(c,"SELECT id,name FROM flexible_topics WHERE owner_teacher=? AND class=? AND subject=? AND semester=? ORDER BY id",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId());
            var flexibleTasks=rows(c,"SELECT t.id,t.name,p.id AS topicId FROM flexible_tasks t LEFT JOIN flexible_task_topics m ON m.flexible_task=t.id LEFT JOIN flexible_topics p ON p.id=m.flexible_topic AND p.owner_teacher=t.owner_teacher AND p.subject=t.subject AND p.class=t.class AND p.semester=t.semester AND p.grade=t.grade WHERE t.owner_teacher=? AND t.class=? AND t.subject=? AND t.semester=? ORDER BY t.id",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId());
            for(var topic:flexibleTopics)topic.put("active",flexibleTopicReleased(c,scope,integer(topic,"id")));
            for(var task:flexibleTasks)task.put("active",flexibleReleased(c,scope,integer(task,"id"),task.get("topicId")==null?null:integer(task,"topicId")));
            return Map.of("topics",topics,"tasks",tasks,"flexibleTopics",flexibleTopics,"flexibleTasks",flexibleTasks);
        });
    }
    public void release(Actor actor,Scope scope,Integer topic,Integer task,boolean active) throws SQLException {
        release(actor,scope,topic,task,null,null,active);
    }
    public void release(Actor actor,Scope scope,Integer topic,Integer task,Integer flexibleTopic,Integer flexibleTask,boolean active) throws SQLException {
        int selected=0;for(Integer value:new Integer[]{topic,task,flexibleTopic,flexibleTask})if(value!=null)selected++;
        if(selected!=1)throw error(400,"invalid_input","Select exactly one topic or task.");
        List<CentralCacheUpdate> cacheUpdates=curriculum.transaction(c->{int grade=authorize(c,new Actor(false,scope.teacherId()),scope,false);authorize(c,actor,scope,false);
            if(topic!=null || task!=null) {
                int topicId=topic!=null?topic:integer(require(c,"SELECT topic FROM tasks WHERE id=?",task),"topic");
                require(c,"SELECT id FROM topics WHERE id=? AND subject=? AND grade=? AND semester=?",topicId,scope.subjectId(),grade,scope.semesterId());
                String table=topic!=null?"curriculum_topic_releases":"curriculum_task_releases",column=topic!=null?"topic":"task";
                write(c,"INSERT INTO "+table+"(teacher,class,subject,semester,"+column+",active) VALUES(?,?,?,?,?,?) ON CONFLICT(teacher,class,subject,semester,"+column+") DO UPDATE SET active=excluded.active",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),topic!=null?topic:task,active?1:0);
                if(topic!=null)write(c,"DELETE FROM curriculum_task_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND task IN(SELECT id FROM tasks WHERE topic=?)",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),topic);
                return active?List.of():stopActiveCentralStages(c,scope,topic,task);
            }
            if(flexibleTopic!=null) {
                var row=require(c,"SELECT * FROM flexible_topics WHERE id=?",flexibleTopic);
                if(integer(row,"owner_teacher")!=scope.teacherId() || integer(row,"class")!=scope.classId() || integer(row,"subject")!=scope.subjectId() || integer(row,"semester")!=scope.semesterId() || integer(row,"grade")!=grade)
                    throw error(403,"forbidden","Flexible topic does not belong to this context.");
                write(c,"INSERT INTO flexible_topic_releases(teacher,class,subject,semester,flexible_topic,active) VALUES(?,?,?,?,?,?) ON CONFLICT(teacher,class,subject,semester,flexible_topic) DO UPDATE SET active=excluded.active",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),flexibleTopic,active?1:0);
                write(c,"DELETE FROM flexible_task_releases WHERE teacher=? AND class=? AND subject=? AND semester=? AND flexible_task IN(SELECT flexible_task FROM flexible_task_topics WHERE flexible_topic=?)",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),flexibleTopic);
                if(!active)stopActiveFlexibleStages(c,scope,flexibleTopic,null);
                return List.of();
            }
            var row=require(c,"SELECT t.*,p.flexible_topic AS topic FROM flexible_tasks t LEFT JOIN flexible_task_topics p ON p.flexible_task=t.id WHERE t.id=?",flexibleTask);
            if(integer(row,"owner_teacher")!=scope.teacherId() || integer(row,"class")!=scope.classId() || integer(row,"subject")!=scope.subjectId() || integer(row,"semester")!=scope.semesterId() || integer(row,"grade")!=grade)
                throw error(403,"forbidden","Flexible task does not belong to this context.");
            write(c,"INSERT INTO flexible_task_releases(teacher,class,subject,semester,flexible_task,active) VALUES(?,?,?,?,?,?) ON CONFLICT(teacher,class,subject,semester,flexible_task) DO UPDATE SET active=excluded.active",scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),integer(row,"id"),active?1:0);
            if(!active)stopActiveFlexibleStages(c,scope,null,flexibleTask);
            return List.of();
        });
        for(var update:cacheUpdates) {
            Student student=Student.get(update.studentId());
            Task cached=Task.get(update.taskId());
            if(student!=null && cached!=null) student.clearSelectedTask(cached);
        }
    }
    private static List<CentralCacheUpdate> stopActiveCentralStages(Connection c,Scope scope,Integer topic,Integer task) throws SQLException {
        String predicate=task!=null ? "a.central_task=?" : "t.topic=?";
        int id=task!=null ? task : topic;
        var active=rows(c,"SELECT a.student,a.central_task FROM student_active_curriculum_stages a "
                + "JOIN student_curriculum_contexts x ON x.student=a.student AND x.subject=a.subject AND x.semester=a.semester "
                + "JOIN tasks t ON t.id=a.central_task JOIN topics p ON p.id=t.topic "
                + "WHERE x.teacher=? AND x.class=? AND x.subject=? AND x.semester=? AND x.grade=p.grade "
                + "AND a.subject=? AND a.semester=? AND p.subject=? AND p.semester=? AND "+predicate,
                scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),scope.subjectId(),scope.semesterId(),scope.subjectId(),scope.semesterId(),id);
        List<CentralCacheUpdate> cache=new ArrayList<>();
        for(var row:active) {
            int student=integer(row,"student"), centralTask=integer(row,"central_task");
            write(c,"UPDATE taskstats SET status=0,last_updated=CURRENT_TIMESTAMP WHERE student=? AND task=? AND status=1",student,centralTask);
            write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=? AND semester=? AND central_task=?",student,scope.subjectId(),scope.semesterId(),centralTask);
            cache.add(new CentralCacheUpdate(student,centralTask));
        }
        return cache;
    }
    private static void stopActiveFlexibleStages(Connection c,Scope scope,Integer flexibleTopic,Integer flexibleTask) throws SQLException {
        String join=flexibleTopic!=null ? "JOIN flexible_task_topics m ON m.flexible_task=a.flexible_task " : "";
        String predicate=flexibleTopic!=null ? "m.flexible_topic=?" : "a.flexible_task=?";
        int id=flexibleTopic!=null ? flexibleTopic : flexibleTask;
        var active=rows(c,"SELECT a.student,a.flexible_task FROM student_active_curriculum_stages a "
                + "JOIN student_curriculum_contexts x ON x.student=a.student AND x.subject=a.subject AND x.semester=a.semester "
                + "JOIN flexible_tasks t ON t.id=a.flexible_task "+join
                + "WHERE x.teacher=? AND x.class=? AND x.subject=? AND x.semester=? AND x.grade=t.grade "
                + "AND a.subject=? AND a.semester=? AND t.owner_teacher=? AND t.class=? AND t.subject=? AND t.semester=? AND "+predicate,
                scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),scope.subjectId(),scope.semesterId(),scope.teacherId(),scope.classId(),scope.subjectId(),scope.semesterId(),id);
        for(var row:active)
            write(c,"DELETE FROM student_active_curriculum_stages WHERE student=? AND subject=? AND semester=? AND flexible_task=?",
                    integer(row,"student"),scope.subjectId(),scope.semesterId(),integer(row,"flexible_task"));
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
