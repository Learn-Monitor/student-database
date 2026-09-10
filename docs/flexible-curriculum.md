# Editable curriculum and independent teacher budgets

Topics and central tasks can be renamed without changing IDs. Central token values
can be edited retroactively: `taskstats` continues to refer to the same task and
current `Task.getTokens()` supplies the value. Cached instances, including those
held in student completion sets, are updated after commit. Task/Topic equality and
hash codes use class and ID, not editable names. Names are JSON-escaped.

See [explicit student contexts and transfers](student-curriculum-contexts.md) for
the Sprint 3½ assignment requirement, student progress and per-student hard limit.

## Model and additive schema

`UnscheduledTask` was considered first. It represents variable per-student rewards,
has no owner or semester and enforces `UNIQUE(subject,name,class)`. Extending it
would require rebuilding that constraint and deciding ownership/semester for
historical rows. Inferring those fields would be unsafe and could silently change
legacy reward semantics. `IndividualTask` is also not a teacher/class/semester
budget. Both models and their existing completion tables remain unchanged.

New `flexible_tasks` holds ID, owner teacher, subject, class, semester, grade, name
and tokens. Names are unique only within owner/subject/class/semester. Tasks are
not subclasses of `Task`: this avoids colliding identifiers between independent
SQL tables in existing task sets. No topic binding is required.

`completed_flexible_tasks` holds student ID, flexible task ID and timestamp, never
an awarded-token snapshot. `Curriculum.progress` sums the current definitions for
a single teacher context, joining both completion tables. Editing 6 to 4 therefore
changes the same completed task's contribution from 6 to 4.

Both tables are installed by the existing `createTables()` resource mechanism
using `CREATE TABLE IF NOT EXISTS`. There is no destructive ALTER, row rewrite,
legacy ownership inference, historical-ID renumbering or data copy. Installation
and repetition are tested on synthetic databases, including legacy completions.
New schema is additive; no existing migration is replaced.

## Budget and authorization

A context is `(teacherId, subjectId, classId, semesterId)`. Its budget is:

```
SUM(tasks.tokens for subject + grade + semester)
+ SUM(flexible_tasks.tokens for this teacher + subject + class + semester)
```

0–100 is regular, 101–105 is tolerated, and totals above 105 reject the operation.
Budget calculations use SQL SUM and 64-bit arithmetic. Name/token validation,
assignment checks, budget validation and writes share a `BEGIN IMMEDIATE`
transaction and the existing SQLiteConnection write lock. Concurrent create/edit
operations cannot both spend the same remainder. Constraint conflicts and database
contention return 409. Failed writes roll back without publishing cache changes.

Central insertion (including the existing `Task.addTask` import path) and central
edits validate the central sum and every affected existing flexible context. A
central increase that would exceed any context's cap is rejected in full. Budget
errors include all exceeded contexts and projected amounts, not just the first.
There is no shared pot between teachers, classes or semesters.

Endpoints derive the actor from the existing authenticated session. Access levels
are registered both in code and path metadata. The service also checks roles and
ownership, even if an extension changes the outer access decision. Teachers must
be assigned in **both** `teacher_classes` and `teacher_subjects`, read fresh from
SQL on every operation. Owner is derived from the session; a different supplied
teacher ID is rejected. Administrators can inspect/correct any flexible context.
Central mutation is administrator-only. Flexible completion and staff progress now
also require the explicit student context introduced in Sprint 3½. Authenticated access denial is HTTP 403;
missing authentication is 401.

The existing assignment schema stores separate class and subject memberships,
not individual teacher/subject/class tuples. Their intersection is the authorized
context. If narrower timetable assignments are required, a separate assignment
model will be needed. No broader assignment rule is inferred in the UI.

The context grade is captured on creation. Promotion of a mutable class record
does not silently move old semester budgets to a new grade. Creating more tasks
in an existing context after its class grade changes returns 409; existing records
remain readable/correctable. A new semester establishes a new context.

## JSON POST API

Uses existing sessions, JSON POST bodies and core AccessLevels. All IDs are integers;
tokens must be nonnegative integers. Names contain 1–200 printable characters.
Edit operations send the complete editable definition (name and tokens).

| Endpoint | Access | Fields / result |
|---|---|---|
| `/curriculum-catalog` | teacher/admin | Own assigned subjects/classes, available semesters; admin also gets teachers |
| `/curriculum-structure` | teacher/admin | `subjectId, grade, semesterId`; central topics, tasks and total |
| `/add-curriculum-topic` | admin | `subjectId, grade, semesterId, number, name`; new ID |
| `/rename-topic` | admin | `topicId, name`; same topic ID |
| `/add-curriculum-task` | admin | `topicId, name, level, tokens`; new ID |
| `/edit-task` | admin | `taskId, name, tokens`; same task ID |
| `/curriculum-budget` | teacher/admin | Context fields; budget |
| `/flexible-tasks` | teacher/admin | Context fields; own task definitions |
| `/add-flexible-task` | teacher/admin | Context fields plus `name, tokens` |
| `/edit-flexible-task` | teacher/admin | `taskId, name, tokens`; scope/owner cannot be reassigned |
| `/complete-flexible-task` | teacher/admin | `taskId, studentId`; idempotent completion, same-class and assigned-context checks |
| `/curriculum-progress` | teacher/admin | Context fields plus `studentId`; current completed central/flexible/total tokens |

Context fields: `subjectId, classId, semesterId`, plus `teacherId` for an admin;
for a teacher the latter may be omitted or must equal their session ID.

Status codes: 400 invalid input, 401 missing authentication, 403 forbidden,
404 missing referenced object, 409 constraint/concurrency conflict or budget
exceeded, 500 unexpected database failure without exposing SQL details.

Budget success responses and `affectedContexts` entries in budget errors contain:
`teacherId, subjectId, classId, semesterId, grade, centralTokens, flexibleTokens,
totalTokens, regularLimit=100, hardLimit=105, remainingRegular, remainingHard`.
Negative remainingRegular explicitly expresses tolerance usage. In a projected
rejected change, remainingHard may be negative. A central-only violation uses
teacherId/classId 0 to identify the central budget rather than a teacher context.

## Standard interface

The admin dashboard and subject page provide semester/grade selection, central
totals, topic rename, task rename/token editing and creation. Teacher dashboards
provide assigned subject/class and semester selection, central structure, their
own flexible tasks and independent remaining budget. Administrators can select
an owner teacher for corrections. Values above 100 are visibly marked and the UI
blocks totals above 105; the server remains authoritative, including hidden
conflicts with other contexts. UI error messages show affected context IDs.
All labels use textContent/value rather than interpolated HTML.

## Compatibility and follow-up work

- Archived topics with NULL semester remain readable/renameable; their tokens
  cannot be increased until a semester is assigned by a future explicit workflow.
  They are not silently counted in every semester. Existing oversubscribed scoped
  budgets are preserved on installation, reported and cannot be increased by
  these APIs; rejected edits leave all existing records intact.
- Existing topic name/number uniqueness constraints across semesters are retained.
  Reusing a central name/number that conflicts with legacy constraints returns 409;
  flexible names are independently scoped. Changing these central constraints is
  a separate migration decision.
- Legacy `UnscheduledTask` and `IndividualTask` rewards remain outside the new
  planning budget. No ambiguous historical ownership is assigned automatically.
- Legacy `Student.getCurrentProgress(subject)` keeps its historical unscoped
  contract. Central edits are retroactive there. Flexible rewards must be read
  through the **scoped** progress API; blindly adding all teachers' tasks into
  that legacy total would mix independent contexts. Student-facing completion
  selection and flexible task deletion/reopening are not added here.
- Permission extensions need explicit rules for these new routes in a later
  sprint. No external permission repository is changed. Core ownership checks
  must remain enforced even when such extensions grant route access.
- Results/reporting consumers should use `/curriculum-progress` or
  `Curriculum.progress` with an explicit teacher/class/semester context and must
  invalidate any cached totals on definition edits. Do not introduce award snapshots.
- Alternative frontend/overlay clients need these selectors, edits and error
  handling in their own sprint. No branding, deployment configuration or runtime
  integration is part of this change.
- Bulk legacy topic imports now encounter the same task budget guard. Their
  existing multi-task import workflow is not made globally transactional: tasks
  accepted before a later rejection remain. Use the explicit APIs for controlled
  edits; an atomic import redesign is separate work.

## Validation

Run `./gradlew test jar shadowJar`. `test` includes the separate `curriculumTest`
worker to keep new synthetic fixtures out of legacy singleton caches and fixed-ID
fixtures. No server listener is started. On the upstream feature basis, 86 existing
and 24 curriculum tests pass, including retroactive completions, stable IDs/caches,
negative/fractional input, permission checks, independent scopes, boundaries
100–106, administrative overflow rollback, legacy preservation and concurrent
spending. Build and fat JAR generation pass.

UI interaction tests: Node.js 22+, `npm ci --prefix src/test/js`,
`npm test --prefix src/test/js`. The original two jsdom interactions exercise the real editor script,
admin rename, server conflict display, teacher edit/context selection and client
budget blocking. These are DOM tests with synthetic API responses, not deployed
browser acceptance tests. No production/demo service or database is needed.

## Optional Permission Manager: standard UI capabilities (Sprint 3)

The editor detects the public capability `typeof hasPermission === 'function'`;
no plugin filename, branding or installation layout is assumed. With that API,
it awaits `permissionsLoaded` once, or calls `loadCurrentPermissions()` once if
only the loader is available. With only `hasPermission`, it awaits that API.
The shipped PM already starts its initial load and returns false after a failed
load. Rejected bootstrap/check promises also fail closed; the editor never
switches to core fallback after detecting PM. No polling or bootstrap loop is used.

| Capability | Standard UI with PM | Without PM |
|---|---|---|
| `curriculum_view` | Required before catalog and every subsequent request | Core catalog access authorizes teacher/admin |
| `curriculum_manage_flexible` | Flexible edit/create forms; otherwise name/tokens as text and “Nur lesbar.” | Existing teacher/admin flexible editing |
| `curriculum_manage_central` | Central forms only when this grant AND `catalog.admin === true` | `catalog.admin === true` |
| `curriculum_complete_flexible` | Reserved for separate completion controls; never implies management | No completion controls in this editor |

Central gating covers topic rename/create and central task name/token edit/create.
An accidental central PM grant cannot elevate a teacher. Read-only users retain
context selectors, structure and budgets. Every save checks view and its separate
management permission again, so a stale form cannot send a mutation after a
revocation is reflected in the PM client snapshot. The explicit display/refresh
button also reevaluates capabilities. These checks call the cached PM API, not
`/get-permissions` per DOM operation. Server-side revocations not yet present in
the client snapshot remain enforced by PM/backend; refresh the PM snapshot or
reload the page to update the UI. There is no new background permission refresh.

Missing view prevents curriculum data/mutation requests even if the editor script
was already loaded. PM can independently block `/curriculum.js` itself. The UI is
only a usability layer: session, core role, ownership, fresh teacher class/subject
assignment, semester and budget checks remain unchanged and authoritative.

HTTP 401 and 403 show fixed German sign-in/permission messages for JSON, plain
text, HTML and empty responses without parsing those bodies. Other responses are
parsed defensively. Known error codes and known conflict messages map to safe
local text; unknown messages, SQL details and server traces are never displayed.
409 budget errors retain validated numeric affected-context IDs and totals;
known name, concurrency, historical-grade and archived-semester conflicts retain
actionable information. Network/malformed-success errors use generic messages.

`/complete-flexible-task` currently has no standard frontend control. A future
completion UI must check `curriculum_view` and `curriculum_complete_flexible`
independently of `curriculum_manage_flexible`; this sprint adds no completion UI.

Validation: `npm test --prefix src/test/js` runs 36 synthetic jsdom tests, including
the two Sprint-1 interactions, core teacher/admin fallback, PM edit/read modes,
central role intersection, separate completion grant, denied view, failed/awaited
bootstrap, one-time loader, rejected checks, all eight 401/403 body combinations,
structured 409 budget/conflict handling, unsafe server details, and stale forms
for all six mutation endpoints after permission revocation. These do not replace
a later authorized browser/PM integration test. No plugin dependency was added.
