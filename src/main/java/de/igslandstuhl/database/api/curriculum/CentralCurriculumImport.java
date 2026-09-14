package de.igslandstuhl.database.api.curriculum;

import de.igslandstuhl.database.api.Task;
import de.igslandstuhl.database.api.TaskLevel;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;

/** Server-side preview and atomic upsert for the central curriculum CSV format. */
public final class CentralCurriculumImport {
    private final Curriculum curriculum;
    public CentralCurriculumImport(Curriculum curriculum) { this.curriculum = curriculum; }

    public enum Action { CREATE, UPDATE, NO_OP, ERROR }
    public record ImportRow(int sourceLine, Integer subjectId, String subjectName, int topicNumber, String topicName,
                            int stageNumber, String stageName, int tokens, Action action, String message) {}
    public record SubjectSummary(int subjectId, String subjectName, long centralTokens, long remainingRegular,
                                 long remainingHard, boolean warning) {}

    public Map<String,Object> preview(Curriculum.Actor actor, int grade, int semesterId, String csv) throws SQLException {
        Curriculum.admin(actor);
        return curriculum.transaction(c -> analyse(c, grade, semesterId, csv).toMap());
    }

    public Map<String,Object> importCsv(Curriculum.Actor actor, int grade, int semesterId, String csv) throws SQLException {
        Curriculum.admin(actor);
        return curriculum.transaction(c -> {
            Plan plan = analyse(c, grade, semesterId, csv);
            for (Curriculum.Budget budget : plan.exceeded) {
                throw new CurriculumException(409, "budget_exceeded", "The 105-token limit would be exceeded in the listed contexts.", plan.exceeded);
            }
            if (!plan.errors.isEmpty()) {
                boolean history = plan.errors.stream().anyMatch(e -> e.contains("completion_history_conflict"));
                throw Curriculum.error(history ? 409 : 400, history ? "completion_history_conflict" : "invalid_input", plan.errors.get(0));
            }
            int createdTopics = 0, updatedTopics = 0, createdStages = 0, updatedStages = 0, unchangedStages = 0;
            Map<TopicKey,Integer> topicIds = new HashMap<>();
            for (TopicPlan topic : plan.topics.values()) {
                Integer id = topic.existingId;
                if (id == null) {
                    Curriculum.write(c, "INSERT INTO topics(subject,grade,semester,number,name) VALUES(?,?,?,?,?)",
                            topic.key.subjectId, grade, semesterId, topic.key.topicNumber, topic.name);
                    id = (int) Curriculum.number(c, "SELECT last_insert_rowid()");
                    createdTopics++;
                } else if (!Objects.equals(topic.existingName, topic.name)) {
                    Curriculum.write(c, "UPDATE topics SET name=? WHERE id=?", topic.name, id);
                    updatedTopics++;
                }
                topicIds.put(topic.key, id);
            }
            Set<StageKey> written = new HashSet<>();
            for (StagePlan stage : plan.stages.values()) {
                if (!written.add(stage.key)) continue;
                int topicId = topicIds.get(stage.key.topicKey);
                if (stage.existingId == null) {
                    Curriculum.write(c, "INSERT INTO tasks(topic,name,niveau,stage_number,tokens) VALUES(?,?,?,?,?)",
                            topicId, stage.name, TaskLevel.LEVEL1.getNumber(), stage.key.stageNumber, stage.tokens);
                    createdStages++;
                } else if (stage.action == Action.UPDATE) {
                    Curriculum.write(c, "UPDATE tasks SET name=?,tokens=? WHERE id=?", stage.name, stage.tokens, stage.existingId);
                    updatedStages++;
                } else unchangedStages++;
            }
            return Map.of("createdTopics", createdTopics, "updatedTopics", updatedTopics, "createdStages", createdStages,
                    "updatedStages", updatedStages, "unchangedStages", unchangedStages, "subjects", plan.subjectSummaries);
        });
    }

    public Map<String,Object> overview(Curriculum.Actor actor, int grade, int semesterId) throws SQLException {
        Curriculum.admin(actor);
        return curriculum.transaction(c -> overview(c, grade, semesterId));
    }

    private Map<String,Object> overview(Connection c, int grade, int semesterId) throws SQLException {
        Curriculum.require(c, "SELECT id FROM semesters WHERE id=?", semesterId);
        List<Map<String,Object>> rows = Curriculum.rows(c, "SELECT s.id AS subjectId,s.name AS subjectName,p.number AS topicNumber,p.name AS topicName,"
                + "t.stage_number AS stageNumber,t.name AS stageName,t.tokens AS tokens,t.id AS taskId,p.id AS topicId "
                + "FROM tasks t JOIN topics p ON p.id=t.topic JOIN subjects s ON s.id=p.subject "
                + "WHERE p.grade=? AND p.semester=? ORDER BY lower(s.name),p.number,t.stage_number,t.id", grade, semesterId);
        return Map.of("rows", rows, "subjects", subjectSummaries(c, grade, semesterId, Collections.emptyMap()));
    }

    private Plan analyse(Connection c, int grade, int semesterId, String csv) throws SQLException {
        if (grade < 1 || grade > 13) throw Curriculum.error(400, "invalid_input", "grade must be between 1 and 13.");
        Curriculum.require(c, "SELECT id FROM semesters WHERE id=?", semesterId);
        Parsed parsed = parse(csv);
        Map<String,Map<String,Object>> subjects = new HashMap<>();
        for (Map<String,Object> subject : Curriculum.rows(c, "SELECT id,name FROM subjects")) {
            subjects.put(String.valueOf(subject.get("name")).strip().toLowerCase(Locale.ROOT), subject);
        }
        Plan plan = new Plan();
        Map<String,String> topicNamesInFile = new HashMap<>();
        Map<String,StagePlan> stagesInFile = new HashMap<>();
        for (ParsedRow input : parsed.rows) {
            List<String> rowErrors = new ArrayList<>();
            Map<String,Object> subject = subjects.get(input.subject.strip().toLowerCase(Locale.ROOT));
            int subjectId = subject == null ? 0 : ((Number) subject.get("id")).intValue();
            String subjectName = subject == null ? input.subject.strip() : String.valueOf(subject.get("name"));
            if (subject == null) rowErrors.add("Zeile " + input.line + ": unbekanntes Fach.");
            int topicNumber = positive(input.topicNumber, "Themennummer", input.line, rowErrors);
            int stageNumber = positive(input.stageNumber, "Etappennummer", input.line, rowErrors);
            String topicName = name(input.topicName, "Themenname", input.line, rowErrors);
            String stageName = name(input.stageName, "Etappenname", input.line, rowErrors);
            int tokens = token(input.tokens, input.line, rowErrors);
            String topicFileKey = subjectName.toLowerCase(Locale.ROOT) + "#" + topicNumber;
            String previousTopicName = topicNamesInFile.putIfAbsent(topicFileKey, topicName);
            if (previousTopicName != null && !previousTopicName.equals(topicName))
                rowErrors.add("Zeile " + input.line + ": gleiche Fach-/Themennummer mit unterschiedlichem Themennamen.");
            TopicKey topicKey = new TopicKey(subjectId, topicNumber);
            StageKey stageKey = new StageKey(topicKey, stageNumber);
            String stageFileKey = topicFileKey + "#" + stageNumber;
            StagePlan duplicate = stagesInFile.get(stageFileKey);
            if (duplicate != null && (!duplicate.name.equals(stageName) || duplicate.tokens != tokens))
                rowErrors.add("Zeile " + input.line + ": gleiche Etappennummer mit widersprüchlichen Daten.");
            if (!rowErrors.isEmpty()) {
                plan.addRow(new ImportRow(input.line, subjectId == 0 ? null : subjectId, subjectName, topicNumber, topicName, stageNumber, stageName, tokens, Action.ERROR, String.join(" ", rowErrors)));
                plan.errors.addAll(rowErrors);
                continue;
            }
            TopicPlan topic = plan.topics.get(topicKey);
            if (topic == null) {
                topic = loadTopic(c, topicKey, grade, semesterId, topicName);
                plan.topics.put(topicKey, topic);
            }
            if (!topic.name.equals(topicName)) topic.name = topicName;
            StagePlan stage = stagesInFile.get(stageFileKey);
            if (stage == null) {
                stage = loadStage(c, stageKey, topic, stageName, tokens);
                stagesInFile.put(stageFileKey, stage);
            }
            plan.stages.putIfAbsent(stageKey, stage);
            plan.addRow(new ImportRow(input.line, subjectId, subjectName, topicNumber, topicName, stageNumber, stageName, tokens, stage.action, stage.message));
            if (stage.action == Action.ERROR) plan.errors.add("Zeile " + input.line + ": " + stage.message);
        }
        plan.subjectSummaries = subjectSummaries(c, grade, semesterId, plan.stages);
        plan.exceeded = exceededBudgets(c, grade, semesterId, plan.subjectSummaries);
        if (!plan.exceeded.isEmpty()) plan.errors.add("budget_exceeded: Die Grenze von 105 Münzen würde überschritten.");
        return plan;
    }

    private TopicPlan loadTopic(Connection c, TopicKey key, int grade, int semesterId, String name) throws SQLException {
        var rows = Curriculum.rows(c, "SELECT id,name FROM topics WHERE subject=? AND grade=? AND semester=? AND number=?", key.subjectId, grade, semesterId, key.topicNumber);
        if (rows.isEmpty()) return new TopicPlan(key, name, null, null);
        return new TopicPlan(key, name, Curriculum.integer(rows.get(0), "id"), String.valueOf(rows.get(0).get("name")));
    }

    private StagePlan loadStage(Connection c, StageKey key, TopicPlan topic, String name, int tokens) throws SQLException {
        if (topic.existingId == null) return new StagePlan(key, null, null, name, tokens, Action.CREATE, "Neue Etappe.");
        var rows = Curriculum.rows(c, "SELECT id,name,tokens FROM tasks WHERE topic=? AND stage_number=?", topic.existingId, key.stageNumber);
        if (rows.isEmpty()) return new StagePlan(key, null, null, name, tokens, Action.CREATE, "Neue Etappe.");
        var row = rows.get(0);
        int id = Curriculum.integer(row, "id"), oldTokens = Curriculum.integer(row, "tokens");
        boolean nameChanged = !Objects.equals(String.valueOf(row.get("name")), name);
        boolean tokenChanged = oldTokens != tokens;
        if (tokenChanged && Curriculum.number(c, "SELECT COUNT(*) FROM taskstats WHERE task=? AND status=?", id, Task.STATUS_COMPLETED) > 0)
            return new StagePlan(key, id, oldTokens, name, tokens, Action.ERROR, "completion_history_conflict: Der Münzwert kann nicht geändert werden, weil bereits Leistungen bestätigt wurden.");
        return new StagePlan(key, id, oldTokens, name, tokens, nameChanged || tokenChanged ? Action.UPDATE : Action.NO_OP, nameChanged || tokenChanged ? "Etappe wird aktualisiert." : "Unverändert.");
    }

    private List<SubjectSummary> subjectSummaries(Connection c, int grade, int semesterId, Map<StageKey,StagePlan> planned) throws SQLException {
        Map<Integer,Long> totals = new HashMap<>();
        Map<Integer,String> names = new HashMap<>();
        for (Map<String,Object> row : Curriculum.rows(c, "SELECT s.id,s.name,COALESCE(SUM(t.tokens),0) AS tokens FROM subjects s LEFT JOIN topics p ON p.subject=s.id AND p.grade=? AND p.semester=? LEFT JOIN tasks t ON t.topic=p.id GROUP BY s.id,s.name", grade, semesterId)) {
            int subject = Curriculum.integer(row, "id");
            totals.put(subject, ((Number) row.get("tokens")).longValue());
            names.put(subject, String.valueOf(row.get("name")));
        }
        for (StagePlan stage : planned.values()) {
            int subject = stage.key.topicKey.subjectId;
            totals.put(subject, totals.getOrDefault(subject, 0L) - (stage.oldTokens == null ? 0 : stage.oldTokens) + stage.tokens);
        }
        List<SubjectSummary> out = new ArrayList<>();
        for (int subject : totals.keySet().stream().sorted(Comparator.comparing(s -> names.getOrDefault(s, ""))).toList()) {
            long total = totals.get(subject);
            if (total == 0 && planned.keySet().stream().noneMatch(k -> k.topicKey.subjectId == subject)) continue;
            out.add(new SubjectSummary(subject, names.getOrDefault(subject, String.valueOf(subject)), total, Curriculum.REGULAR_LIMIT - total, Curriculum.HARD_LIMIT - total, total > Curriculum.REGULAR_LIMIT));
        }
        return out;
    }

    private List<Curriculum.Budget> exceededBudgets(Connection c, int grade, int semesterId, List<SubjectSummary> summaries) throws SQLException {
        List<Curriculum.Budget> exceeded = new ArrayList<>();
        for (SubjectSummary summary : summaries) {
            var central = summary.centralTokens;
            if (central > Curriculum.HARD_LIMIT) exceeded.add(Curriculum.Budget.of(new Curriculum.Scope(0, summary.subjectId, 0, semesterId), grade, central, 0));
            for (Map<String,Object> row : Curriculum.rows(c, "SELECT owner_teacher,class,SUM(tokens) AS tokens FROM flexible_tasks WHERE subject=? AND grade=? AND semester=? GROUP BY owner_teacher,class", summary.subjectId, grade, semesterId)) {
                Curriculum.Budget budget = Curriculum.Budget.of(new Curriculum.Scope(Curriculum.integer(row, "owner_teacher"), summary.subjectId, Curriculum.integer(row, "class"), semesterId), grade, central, ((Number) row.get("tokens")).longValue());
                if (budget.totalTokens() > Curriculum.HARD_LIMIT) exceeded.add(budget);
            }
        }
        return exceeded;
    }

    private static int positive(String value, String label, int line, List<String> errors) {
        try { int parsed = Integer.parseInt(value.strip()); if (parsed > 0) return parsed; }
        catch (RuntimeException ignored) {}
        errors.add("Zeile " + line + ": " + label + " muss eine positive Ganzzahl sein."); return 0;
    }
    private static int token(String value, int line, List<String> errors) {
        try { int parsed = Integer.parseInt(value.strip()); if (parsed >= 0 && parsed <= Curriculum.HARD_LIMIT) return parsed; }
        catch (RuntimeException ignored) {}
        errors.add("Zeile " + line + ": Münzen müssen eine Ganzzahl zwischen 0 und 105 sein."); return 0;
    }
    private static String name(String value, String label, int line, List<String> errors) {
        String trimmed = value == null ? "" : value.strip();
        if (trimmed.isEmpty() || trimmed.length() > 200 || trimmed.chars().anyMatch(Character::isISOControl))
            errors.add("Zeile " + line + ": " + label + " muss 1-200 druckbare Zeichen enthalten.");
        return trimmed;
    }

    private static Parsed parse(String csv) {
        if (csv == null || csv.isBlank()) throw Curriculum.error(400, "invalid_input", "CSV darf nicht leer sein.");
        String text = csv.startsWith("\ufeff") ? csv.substring(1) : csv;
        char delimiter = detectDelimiter(text);
        List<List<String>> records = parseRecords(text, delimiter);
        if (records.isEmpty()) throw Curriculum.error(400, "invalid_input", "CSV-Header fehlt.");
        List<String> header = records.get(0).stream().map(s -> s.replace("\ufeff", "").strip()).toList();
        List<String> expected = List.of("Fach", "Themennummer", "Themenname", "Etappennummer", "Etappenname");
        if (header.size() != 6 || !header.subList(0, 5).equals(expected) || !(header.get(5).equals("Münzen") || header.get(5).equals("Muenzen")))
            throw Curriculum.error(400, "invalid_input", "CSV-Header muss Fach;Themennummer;Themenname;Etappennummer;Etappenname;Münzen lauten.");
        List<ParsedRow> rows = new ArrayList<>();
        for (int i = 1; i < records.size(); i++) {
            List<String> record = records.get(i);
            if (record.size() == 1 && record.get(0).isBlank()) continue;
            if (record.size() != 6) throw Curriculum.error(400, "invalid_input", "Zeile " + (i + 1) + ": falsche Spaltenzahl.");
            rows.add(new ParsedRow(i + 1, record.get(0), record.get(1), record.get(2), record.get(3), record.get(4), record.get(5)));
        }
        return new Parsed(rows);
    }

    private static char detectDelimiter(String text) {
        String first = text.lines().findFirst().orElse("");
        return parseRecords(first, ';').get(0).size() >= parseRecords(first, ',').get(0).size() ? ';' : ',';
    }
    private static List<List<String>> parseRecords(String text, char delimiter) {
        List<List<String>> records = new ArrayList<>(); List<String> record = new ArrayList<>(); StringBuilder field = new StringBuilder(); boolean quoted = false;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (quoted) {
                if (ch == '"' && i + 1 < text.length() && text.charAt(i + 1) == '"') { field.append('"'); i++; }
                else if (ch == '"') quoted = false;
                else field.append(ch);
            } else if (ch == '"') quoted = true;
            else if (ch == delimiter) { record.add(field.toString()); field.setLength(0); }
            else if (ch == '\n' || ch == '\r') {
                if (ch == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') i++;
                record.add(field.toString()); field.setLength(0); records.add(record); record = new ArrayList<>();
            } else field.append(ch);
        }
        record.add(field.toString()); records.add(record); return records;
    }

    private record Parsed(List<ParsedRow> rows) {}
    private record ParsedRow(int line, String subject, String topicNumber, String topicName, String stageNumber, String stageName, String tokens) {}
    private record TopicKey(int subjectId, int topicNumber) {}
    private record StageKey(TopicKey topicKey, int stageNumber) {}
    private static final class TopicPlan {
        final TopicKey key; String name; final Integer existingId; final String existingName;
        TopicPlan(TopicKey key, String name, Integer existingId, String existingName) { this.key = key; this.name = name; this.existingId = existingId; this.existingName = existingName; }
    }
    private static final class StagePlan {
        final StageKey key; final Integer existingId; final Integer oldTokens; final String name; final int tokens; final Action action; final String message;
        StagePlan(StageKey key, Integer existingId, Integer oldTokens, String name, int tokens, Action action, String message) { this.key = key; this.existingId = existingId; this.oldTokens = oldTokens; this.name = name; this.tokens = tokens; this.action = action; this.message = message; }
    }
    private static final class Plan {
        final List<ImportRow> rows = new ArrayList<>(); final List<String> errors = new ArrayList<>(); final Map<TopicKey,TopicPlan> topics = new LinkedHashMap<>(); final Map<StageKey,StagePlan> stages = new LinkedHashMap<>();
        List<SubjectSummary> subjectSummaries = List.of(); List<Curriculum.Budget> exceeded = List.of(); int creates, updates, unchanged;
        void addRow(ImportRow row) { rows.add(row); if (row.action == Action.CREATE) creates++; else if (row.action == Action.UPDATE) updates++; else if (row.action == Action.NO_OP) unchanged++; }
        Map<String,Object> toMap() {
            return Map.of("rows", rows, "creates", creates, "updates", updates, "unchanged", unchanged, "errors", errors,
                    "warnings", subjectSummaries.stream().filter(SubjectSummary::warning).toList(), "subjects", subjectSummaries, "canImport", errors.isEmpty());
        }
    }
}
