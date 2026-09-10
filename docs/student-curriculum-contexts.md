# Explicit student curriculum contexts

Sprint 3½ extends the flexible curriculum service and standard admin editor.
A student has at most one explicit `(teacher, class, grade)` assignment per
`(student, subject, semester)`. The database primary key enforces uniqueness.
An absent assignment is an actionable `409 context_unassigned`, not a guessed
teacher and not a zero-progress result. The assignment pins the historical grade,
including contexts without flexible task definitions yet.

## Assignment and authority

Assignment, class roster access and transfer are administrator-only, repeated
inside the service regardless of route/PM grants. Teachers cannot assign students
or transfer their work in this sprint. Target teacher-class AND teacher-subject
memberships are checked fresh even for an administrator. The student must currently
belong to the target class. Existing teacher ownership checks remain enforced.

The admin editor uses its existing teacher/subject/class/semester selectors and
shows a minimal class roster: student ID, name and current context teacher/class.
No credentials are returned. The assign button creates or replaces the assignment
only when all active flexible completions already belong to the target context.
Central progress alone does not prohibit switching teachers within the same
historical grade; switching the central grade within a semester is not supported.

An explicit assignment is now required by `complete`, staff `progress` and
`completedTokens`. Teachers cannot award another teacher's student a flexible
completion, even when both teach the same class and subject. An admin also cannot
bypass the student's assignment when completing a task. Staff progress checks
both the actor's scope authorization and the student's assignment. Historical
student progress continues to use the stored grade/class after promotion; new
completions still require the student's current class to match.

## Explicit transfer of completed work

A teacher/context change after flexible completion is supported through a separate
preview and explicit confirmation. The administrator maps **every active completed
flexible task** to a distinct, previously uncompleted target-context task with the
same current token value. The previewed token value is submitted and rechecked
against both definitions. If suitable target tasks do not exist, an authorized
editor must first prepare them within the target's existing budget; no task is
invented or automatically awarded by the transfer preview.

The target must have the same subject, semester and historical central grade.
The source assignment from the preview must still match. An added completion,
changed value, duplicate/missing mapping, wrong context, already completed target,
invalid teacher membership, or target budget above 105 rejects the operation.
All target completions, transfer links and the assignment update share one
`BEGIN IMMEDIATE` transaction and the existing database write lock. A failure
rolls back the whole transfer, including earlier valid mappings in that request.
Concurrent transfer/assignment/completion requests cannot create a mixed result.

`curriculum_completion_transfers` records student, source task, target task and
time. Both original completion rows and transferred-to completion rows are
retained. Active completions are those without an outgoing transfer link. A
transfer chain therefore counts only its current terminal completion. Historical
source tasks cannot be re-completed or reused as transfer targets to create a
cycle. A return to an earlier context needs unused replacement tasks.

No awarded-token snapshot is introduced. At transfer time the explicitly mapped
values must match; subsequently the current **target** definition determines
progress. Editing a historical source definition does not change transferred
progress. Central task values remain retroactive as before.

## Per-student hard limit

The authoritative budget for each assigned student/subject/semester is the sum
of central definitions for the pinned grade plus all flexible definitions in
that one assigned context, never the sum of several teachers' budgets. Completion
counts are a subset of these definitions. Existing create/edit/central-increase
checks retain the 105 hard limit for every affected context. Assignment, transfer,
completion and progress also validate the selected context's budget; an old
oversubscribed context is not silently presented as a valid capped total.

Two teachers can each plan 70 central + 35 flexible tokens for the same class and
subject. Different assigned students can use each context. One student cannot
receive both sets: a context switch explicitly replaces active completion
identities and preserves at most the target context's 105-token plan.

This contract concerns central plus flexible curriculum. Legacy unscoped
`Student.getCurrentProgress`, per-student `UnscheduledTask`/`IndividualTask`, and
external Results/Overlay consumers are not silently redefined. Consumers must
adopt the assignment-aware API to claim this per-student curriculum guarantee.

## HTTP contract

All routes are JSON POST and are registered both in code and core path metadata.
Scope fields are `teacherId, subjectId, classId, semesterId`.

| Route | Core role | Request and result |
|---|---|---|
| `/curriculum-students` | admin | Scope → minimal class roster with current assignments |
| `/assign-curriculum-context` | admin | Scope + `studentId` → `{ok:true}` |
| `/curriculum-transfer-preview` | admin | Target scope + `studentId` → `source`, `completions`, `targets`; read-only |
| `/transfer-curriculum-context` | admin | Target scope + `studentId`, `sourceTeacherId`, `sourceClassId`, `transfers:[{sourceTaskId,targetTaskId,tokens}]` → `{ok:true}` |
| `/my-curriculum-progress` | student | Only `subjectId, semesterId` → `centralTokens, flexibleTokens, totalTokens` |

The student endpoint uses the session student ID and resolves teacher/class/grade
from the persisted assignment. Supplied `studentId`, `teacherId`, `classId` or
`grade` are rejected with 400. Anonymous access is 401; staff cannot use the
student-only handler as a way to select an arbitrary student. Staff continue to
use `/curriculum-progress` with its strengthened assignment checks.

## Optional Permission Manager follow-up

No PM repository or policy is changed here. Without PM, the core admin role
controls assignment/transfer. With PM, the standard editor additionally requires
`curriculum_view` and the separate capability `curriculum_assign_context`; central
management does not imply assignment. The capability is rechecked before preview
and mutation. Existing Sprint-2 PM definitions do **not** yet grant the new routes,
so the assignment UI intentionally remains hidden until PM integration follows.

A subsequent PM-only sprint must explicitly cover the four new admin routes and
provide student-safe access to `/my-curriculum-progress`, preferably through a
separate own-progress permission. Do not give students the existing teacher
`curriculum_view` permission merely to expose this endpoint. Core student-session
and admin-role checks remain mandatory even with accidental PM grants.

## Additive installation and legacy records

The two new tables are created by the existing `createTables()` resource scan.
No historical completions, IDs or rows are deleted, rewritten or automatically
assigned. Repeated creation preserves assignments and transfer links. Existing
single-context completions can be explicitly assigned to their actual context.
Mixed legacy completions cause `context_conflict` until explicitly reconciled.

For unassigned legacy data, transfer preview returns source teacher/class `0/0`
as a transient marker. An admin may explicitly map all active legacy completions
into one valid target context; commit checks that the student remains unassigned.
The marker is never stored as an assignment. A mixed legacy grade or a set of
completions that cannot fit into a valid target requires a separately designed
correction; no destructive cleanup or automatic loss of work is provided.

## Validation

`./gradlew test jar shadowJar` uses synthetic fixtures only. Curriculum coverage
includes the original 24 cases (adapted to require explicit assignment) and
24 added cases: two-teacher isolation, different students, absent assignment,
admin-only authorization, fresh memberships, session-only student access,
historical grade/semester isolation, legacy preservation, 105 rejection,
assignment/completion races, explicit transfers and chains, mapping validation,
atomic rollback, concurrent transfers, HTTP transfer validation and unassigned
legacy recovery. No deployment or live database is required.

`npm test --prefix src/test/js` passes 44 jsdom tests: all 36 Sprint-3 checks plus
core/PM assignment gating, teacher grant rejection, explicit preview/confirmation,
and blocked stale assignment/transfer forms after permission revocation.
