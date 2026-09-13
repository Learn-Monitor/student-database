# Dashboard planning: flexible topics and student catalog

Base: `c227310dac51d603458cff4a28c0126338055c40`. Development branch:
`feature/curriculum-dashboard-catalog-20260911`.

## Model and authority

Central topics/tasks remain administrative. Flexible topics are independent per
teacher, subject, class and semester, with a pinned historical grade. They do not
rename or otherwise alter central topics. Topic IDs in the two kinds are separate
namespaces, just like task IDs. A flexible topic name is unique in its context.

Additive `flexible_topics` and `flexible_task_topics` tables preserve all existing
tasks and completions. An absent association means no topic; do not infer a
historical assignment. Every association checks owner, subject, class, semester
and grade inside the task write transaction. Invalid associations roll back the
name, token edit or creation as well. Existing edits omitting topicId retain the
association; explicit null removes it. Topic-only contexts also pin their grade.

Current token values still apply retroactively. Planning and topic changes never
award a completion. The 100 regular / 105 hard planning limit and explicit student
context/transfer semantics are unchanged.

## JSON POST contracts

Existing context fields are `teacherId, subjectId, classId, semesterId`; teacher
identity follows existing session rules. All new routes have core path metadata.

| Route | Role | Input/output |
|---|---|---|
| `/flexible-curriculum-structure` | teacher/admin | Scope → `topics:[{id,name}], tasks:[{id,name,tokens,topicId?,topicName?}]` |
| `/add-flexible-topic` | teacher/admin | Scope + name → `{id}` |
| `/rename-flexible-topic` | teacher/admin | topicId + name → `{ok:true}` |
| `/my-curriculum-catalog` | student | subjectId, optional semesterId → catalog below |

`/add-flexible-task` and `/edit-flexible-task` now accept optional nullable
`topicId`, always referencing a flexible topic. Their old responses and
`/flexible-tasks` remain compatible; the new structure route supplies grouping.

The student route rejects studentId/teacherId/classId/grade overrides. It uses
the session student and saved assignment, shares the current-semester resolver,
and returns `context_unassigned` / `current_semester_unavailable` as 409, never 0.
Teachers and administrators cannot use the student route. Historical requests
retain the assigned grade after promotion. No personal or credential fields are
returned.

Success contains `semesterId`, `centralTopics`, `centralTasks`, `flexibleTopics`,
`flexibleTasks`, `planned`, and `progress`. Central task entries contain
`id,name,tokens,niveau,topicId,topicName,completed`. Flexible entries contain
`id,name,tokens,topicId?,topicName?,completed`; no level is invented. Missing topic
fields (or null) mean unassigned. Empty topics and zero-token tasks remain visible.
`planned` contains central/flexible/total tokens, regularLimit and hardLimit.
`progress` is the existing progress contract, unchanged, computed in the same
transaction. Completed flags use task identities in separate namespaces, and
only terminal flexible transfer completions count. No cache or award snapshot.

## Standard dashboards

The existing teacher/admin dashboard curriculum sections now group flexible
tasks by topic and offer topic creation/rename and a topic selector for task
create/edit. Unassigned tasks remain in a visible separate group. Existing central
topic/task controls remain administrative. Existing PM view/manage capabilities
are rechecked before mutations, including new topic operations. Stale forms after
a context change cannot write. Editing warns that token changes are retroactive.

## Integration and checks

Required PM follow-up: add structure to `curriculum_view`, both topic mutations
to `curriculum_manage_flexible`, and student catalog to the student own-progress
permission. No teacher/admin grant for the student catalog. The overlay must
consume the catalog to display open plans alongside earned progress.

Validation uses only synthetic fixtures: `./gradlew test jar shadowJar`,
`npm test --prefix src/test/js` (Node 22), and `git diff --check`.
Additional tests cover context/role isolation, atomic failed association,
legacy preservation, current and historical semesters, transfer terminal state,
budget overflow, UI grouping, permission revocation and stale forms.
No deployment or runtime/database operation is part of this implementation.
