package de.igslandstuhl.database.api.curriculum;

import java.sql.*;
import java.util.*;

/** Read-only analysis of legacy class releases before A2 moves them to group releases. */
public final class CourseGroupReleasePreflight {
    public record Finding(int courseGroupId, String kind, int stageId, Set<Integer> activeValues) {
        public boolean conflict() { return activeValues.size()>1; }
    }
    private CourseGroupReleasePreflight() {}
    public static List<Finding> inspect(Connection c) throws SQLException {
        Map<String,Set<Integer>> values=new LinkedHashMap<>(); Map<String,Integer> groupIds=new HashMap<>();
        inspectTable(c,values,groupIds,"curriculum_topic_releases","topic","topics");
        inspectTable(c,values,groupIds,"curriculum_task_releases","task","tasks");
        inspectTable(c,values,groupIds,"flexible_topic_releases","flexible_topic","flexible_topics");
        inspectTable(c,values,groupIds,"flexible_task_releases","flexible_task","flexible_tasks");
        List<Finding> result=new ArrayList<>();
        for(var e:values.entrySet()) { String[] p=e.getKey().split(":"); result.add(new Finding(Integer.parseInt(p[0]),p[1],Integer.parseInt(p[2]),Set.copyOf(e.getValue()))); }
        return result;
    }
    private static void inspectTable(Connection c,Map<String,Set<Integer>> values,Map<String,Integer> groups,String table,String stageColumn,String stageTable) throws SQLException {
        String sql="SELECT r."+stageColumn+" AS stageId,r.active,x.subject,x.semester,cl.grade FROM "+table+" r JOIN "+stageTable+" x ON x.id=r."+stageColumn+" JOIN classes cl ON cl.id=r.class JOIN curriculum_subject_types st ON st.subject=x.subject AND st.mode='INDIVIDUAL'";
        for(var row:Curriculum.rows(c,sql)) {
            CourseGroup g=CourseGroup.resolve(c,Curriculum.integer(row,"subject"),Curriculum.integer(row,"grade"),Curriculum.integer(row,"semester"));
            String key=g.id()+":"+table+":"+Curriculum.integer(row,"stageId"); values.computeIfAbsent(key,k->new LinkedHashSet<>()).add(Curriculum.integer(row,"active")); groups.put(key,g.id());
        }
    }
}
