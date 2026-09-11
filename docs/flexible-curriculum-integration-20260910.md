# Flexible curriculum: integration record (10 September 2026)

No deployment, service change or production/demo database operation was performed.
Only student-database repository content was changed. No pull request was opened.
No runtime files, secrets or databases are included in the feature commits.

## Bases and branches

- Fresh Learn-Monitor/student-database main: `535cfe4242e029c8d8596b553a2c4e0638956e3b`.
- Upstream-ready fork branch: `feature/flexible-curriculum-upstream-20260910`.
- Integration base: `8bc998058c2a81f15011d5f487040e52707ef19f` (documented Canonical v2).
- Integration branch: `feature/flexible-curriculum-canonical-v2-20260910`.
- Existing release and consolidation references were not moved or overwritten.
- The existing local remote names are reversed from common convention:
  `origin` names Learn-Monitor, `upstream` names synchronierer. Pushes explicitly
  target the fork; the feature base was fetched directly from Learn-Monitor main.

| Feature | Upstream commit | Integration commit |
|---|---|---|
| Model, transactions, caches | `f3873d3` | `a4517a3` |
| HTTP endpoints and core access | `e2331d4` | `fdd775a` |
| Standard UI and DOM tests | `7a692f2` | `dd01582` |
| Java tests and feature documentation | `1165d1f` | `4e5ec0c` |

This integration record is an additional documentation-only commit.

## Compatibility resolutions

Two textual conflicts required resolution:

1. Topic loading retains Canonical-v2's NULL **and blank** semester handling while
   using the new canonical cached object. The upstream branch handles SQL NULL.
2. PostResponse retains Canonical-v2's cookie-aware redirect overload and adds the
   new JSON error-status overload alongside it.

All other patches applied directly. The new service, schema, handler, UI and test
source are identical between branches. The existing Canonical-v2 server arguments,
plugin-loader dependency/version, login/session compatibility and assignment
behavior remain on their original base; current upstream main was not merged into
this branch. `git range-diff` confirms the two context-specific resolutions.

## Verification

- Upstream: `./gradlew test jar shadowJar` passed; 86 existing + 24 curriculum tests,
  no failures/errors/skips. Standard and fat JARs built locally only.
- Integration: same command passed; 87 existing + 24 curriculum tests,
  no failures/errors/skips. Standard and fat JARs built locally only.
- Both branches: `npm test --prefix src/test/js` passed, 2 DOM interaction tests.
- `git diff --check` passed. Build logs are local, ignored build artifacts.
- No deployed browser acceptance test was performed: deployment was explicitly
  excluded. UI tests execute the actual standard editor script in jsdom with
  synthetic API responses; backend tests use synthetic local SQL fixtures.
- Gradle reports deprecated APIs, including its test-task registering delegate;
  the tested Gradle wrapper builds successfully. Gradle 10 migration is separate.

## Scope and follow-up

See [feature/API/model documentation](flexible-curriculum.md) for additive schema,
authorization, budget calculation, validation coverage and edge cases. In particular,
legacy unscoped totals do not silently aggregate independent teacher contexts;
consumers must use the scoped progress API. Existing per-student variable rewards
remain unchanged. Class/subject assignments are the intersection of the two
existing assignment tables, not an inferred timetable.

No changes were made in Permission Manager, Results, Attendance, Overlay or Control.
Sprint 2 should add explicit Permission Manager policies for the new routes and
plan scoped Results/Overlay consumption. Keep backend ownership checks; do not add
awarded-token snapshots. Any eventual staging/deployment and upstream PR require
separate work. No Canonical-v2 release artifact is replaced by these local builds.

## Changed files (feature relative to its base)

- `.gitignore`
- `build.gradle.kts`
- `docs/flexible-curriculum.md`
- `src/main/java/de/igslandstuhl/database/api/Task.java`
- `src/main/java/de/igslandstuhl/database/api/Topic.java`
- `src/main/java/de/igslandstuhl/database/api/curriculum/Curriculum.java`
- `src/main/java/de/igslandstuhl/database/api/curriculum/CurriculumException.java`
- `src/main/java/de/igslandstuhl/database/server/sql/SQLiteConnection.java`
- `src/main/java/de/igslandstuhl/database/server/webserver/Status.java`
- `src/main/java/de/igslandstuhl/database/server/webserver/handlers/CurriculumRequestHandler.java`
- `src/main/java/de/igslandstuhl/database/server/webserver/handlers/HttpHandler.java`
- `src/main/java/de/igslandstuhl/database/server/webserver/handlers/PostRequestHandler.java`
- `src/main/java/de/igslandstuhl/database/server/webserver/responses/PostResponse.java`
- `src/main/resources/html/admin/dashboard.html`
- `src/main/resources/html/admin/subject.html`
- `src/main/resources/html/teacher/dashboard.html`
- `src/main/resources/js/site/curriculum.js`
- `src/main/resources/meta/paths/get_paths.json`
- `src/main/resources/meta/paths/post_paths.json`
- `src/main/resources/sql/tables/completed_flexible_tasks.sql`
- `src/main/resources/sql/tables/flexible_tasks.sql`
- `src/test/java/de/igslandstuhl/database/api/curriculum/CurriculumTest.java`
- `src/test/js/curriculum.test.cjs`
- `src/test/js/package-lock.json`
- `src/test/js/package.json`
- `docs/flexible-curriculum-integration-20260910.md` (integration branch only)

## Sprint 3: permission-aware standard UI

Read-only source-of-truth review: Control main
`3c7895d223e14636e0a94425ca09f13189060ff3`, verified against remote main.
Read `docs/ARCHITECTURE.md`, `docs/arcanum/ARBEITSSTAND.md` (5 September)
and its updated `SIGNAGE-PARALLELHOST.md` reference including the 10 September
SOL sender contract update. The one-target-repository rule applies.

Permission Manager API and curriculum contract were read at Sprint-2 upstream
`12fc737db4f39801e34c787e06898a3e851fe119` and canonical
`bde94e87e7d3bd4bf1ca42125322c4a276a65f77`; their public JavaScript API is identical.
It starts `permissionsLoaded`, returns an empty snapshot on loading failure and
checks cached effective permissions via `hasPermission`. No PM changes are needed.

| Track | Exact Sprint-1 base | Sprint-3 UI commit |
|---|---|---|
| Upstream | `1165d1f423950dd4bfd06bdc581f399d8ac1c72c` | `8dbe2b35f9d60c0309eb48b127a51c23b4109b39` |
| Canonical v2 | `819df09231ab86378f889c44ef6f01541a69c8ac` | `78c0356dd8aa1bb4ad904ffce0f8dea3366eec58` |

Successor branches:
- `feature/flexible-curriculum-ui-permissions-upstream-20260910`
- `feature/flexible-curriculum-ui-permissions-canonical-v2-20260910`

The UI commit was cherry-picked without conflicts. All three changed feature
files are byte-identical between tracks: `src/main/resources/js/site/curriculum.js`,
`src/test/js/curriculum.test.cjs`, `docs/flexible-curriculum.md`. This integration
record is the only additional canonical documentation change. No Java, route,
core permission, plugin-loader or runtime compatibility classes were changed.
Both original Sprint-1 branch tips remain at the exact bases above.

Validation on each branch:
- `./gradlew test jar shadowJar`: passed. Upstream 86 existing + 24 curriculum
  tests; canonical 87 existing + 24 curriculum tests; zero failures/errors/skips.
- `npm test --prefix src/test/js`: 36/36 passing on each branch, Node 22.21.1.
  The existing local tool under `build/ui-tools` was added to PATH for these runs.
- `git diff --check`: passed. Existing Gradle deprecation warnings remain.
- Tests use synthetic local fixtures and mocked HTTP responses. There was no
  deployed browser test, service action or live database access.

The full capability table, fail-closed bootstrap, client snapshot limitations,
safe HTTP errors and DOM coverage are documented in `flexible-curriculum.md`.
Completion controls are absent; future controls must gate completion separately.
A later integration test should exercise the real optional PM and standard UI
together. Scoped progress consumers still need explicit teacher/class/semester
selection and cache invalidation after edits; independent budgets must not be
aggregated or replaced by token snapshots. Those are separate repository sprints.

## Sprint 3½: student contexts and explicit transfer

Control remains at `3c7895d223e14636e0a94425ca09f13189060ff3`, checked against
remote main before work. Only student-database was modified.

| Track | Exact Sprint-3 base | Context feature commit |
|---|---|---|
| Upstream | `8dbe2b35f9d60c0309eb48b127a51c23b4109b39` | `205f8d853c7f4011f722c621f8c306243a3bf23b` |
| Canonical v2 | `f4f22abcdeff32c839cb21d61bbae7cd1f55940b` | `2b9be5f29e6cce1196e3c1e32c68ae0ff5851951` |

Local successor branches:
- `feature/flexible-curriculum-student-context-upstream-20260910`
- `feature/flexible-curriculum-student-context-canonical-v2-20260910`

The feature cherry-picked without conflicts; all ten feature files are identical
between tracks. This canonical-only integration record is additional. Prior
Sprint-1 and Sprint-3 branch tips were not moved.

Validation passed on both tracks:
- `./gradlew test jar shadowJar`: upstream 86 existing + 48 curriculum tests;
  canonical 87 existing + 48 curriculum tests. No failures/errors/skips.
- `npm test --prefix src/test/js`: 44 passing DOM tests on each track.
- `git diff --check`: passed. Existing Gradle deprecation warnings remain.

The initial design prohibited ordinary reassignment after flexible completion.
The requested final behavior additionally provides an explicit, atomic transfer
with one-to-one equally valued target tasks and retained historical completions.
See [student context contract](student-curriculum-contexts.md) for the final API,
admin workflow, additive tables, migration boundaries and tests.

No PM rules were changed. Assignment/transfer has a separate UI capability
`curriculum_assign_context`, and all four new admin routes need a subsequent
PM-only integration. Student own-progress also needs a separate PM route policy;
students must not receive teacher curriculum access. With the existing PM rules,
these new routes are intentionally not yet available. Core-only operation and
role/session checks are covered by the synthetic tests.

No services, live databases or other repositories were changed; no PR was opened.
The feature branches are local at this stage. No release artifact was replaced.

## Sprint 4½: additive progress detail contract

Control main `3c7895d223e14636e0a94425ca09f13189060ff3` was checked against its
remote before work; architecture, working state and referenced Signage/SOL update
were read. This sprint changes only student-database.

| Track | Exact Sprint-3½ base | Detail feature commit |
|---|---|---|
| Upstream | `205f8d853c7f4011f722c621f8c306243a3bf23b` | `1dbb52717d146798cc00c133ce4d2d0c6d316cca` |
| Canonical v2 | `739bbccadebabf2acc69911a273cfcda4f9bb665` | `5cc79a52ba308bd2e626a11ffb6c89ad10dc4273` |

Successor branches:
- `feature/curriculum-progress-details-upstream-20260910`
- `feature/curriculum-progress-details-canonical-v2-20260910`

Cherry-pick was conflict-free. Service, tests and general contract documentation
are identical between tracks; this integration record is canonical-only. Existing
branches and core/runtime compatibility classes were not moved or modified.

`/my-curriculum-progress` and `/curriculum-progress` add typed central/flexible
completion arrays. Existing totals now sum those same materialized lists inside
the existing transaction. Current definitions supply names, token values and
central topic/level data. Assignment, role/session, historical-grade, budget and
ACTIVE_COMPLETION checks remain unchanged. No schema or timestamp field is added.

Validation on both branches:
- `./gradlew test jar shadowJar`: passed. Upstream 86 existing + 58 curriculum
  tests; canonical 87 existing + 58 curriculum tests. No failures/errors/skips.
- `npm test --prefix src/test/js`: all 44 existing DOM tests passed per track.
- `git diff --check`: passed. Existing Gradle deprecation warnings remain.
- `get_paths.json`, `post_paths.json` and `CurriculumRequestHandler.java` are
  byte-identical to each track's Sprint-3½ base: no new route or weakened handler.
- Ten new curriculum tests cover exact completed identities, current 6→4 token
  values and renamed task/topics, zero-token entries, same-name distinct IDs,
  empty lists, isolated students/teachers/subjects/semesters/grades, pinned grade,
  A→B and A→B→C with retained source rows, identical student/staff/admin JSON,
  all three sum invariants, missing-assignment 409, scope/budget checks and
  concurrent definition edits. Existing override/authorization tests still pass.

See [the extended response contract](student-curriculum-contexts.md) for field types,
ordering and examples. Existing Sprint-4 PM permissions cover the same endpoints;
Permission Manager, Results, Overlay, Attendance and Control were not changed.
No deployment, PR, service action or live database operation was performed.

## Sprint 4¾: configured current semester for progress

Remote Control main was rechecked before implementation and had advanced to
`e28135fedf582dc8c2b4d292b7820bcc40df3124`. Architecture, workflow, Arcanum working
state, referenced Signage/SOL state and canonical release evidence were read.
Only student-database is changed.

| Track | Exact base | Feature commit |
|---|---|---|
| Upstream | `1dbb52717d146798cc00c133ce4d2d0c6d316cca` | `f72c693ff9152e1ce34398f9903eb8296ae7b846` |
| Canonical v2 | `c7c806880e4630a59edea3541607416ed8a3bf0d` | `18f16293d277b51eecbe7045687473ec7439e35e` |

Branches:
- `feature/curriculum-current-semester-upstream-20260910`
- `feature/curriculum-current-semester-canonical-v2-20260910`

The feature cherry-pick was conflict-free. The same five feature files are
identical across tracks; only this integration report is canonical-only.
Existing branches remain at their original commits.

Both existing progress handlers share an effective-semester resolver. Present
`semesterId` uses exactly the requested integer, including historical contexts.
Missing `semesterId` resolves the configured current semester of the current
school year. Every successful response adds integer `semesterId`; all five
Sprint-4½ fields and their calculation remain intact. Other required fields,
student scope rejection, teacher/admin authorization, assignment, pinned grade,
105 limit and ACTIVE_COMPLETION/transfer semantics are unchanged.

The existing school-year API selects years using configured inclusive date
ranges, but previously fell back to the last label if no range matched. Progress
uses the additive strict `SchoolYear.getCurrentYear(false)` variant, which shares
that existing selection and disables the label fallback. No new calendar logic
or semester inference is introduced. Legacy no-argument callers keep their
behavior. An absent year, absent pointer or unresolved semester returns
`409 current_semester_unavailable` with `No current semester is configured.`
An absent student assignment in the resolved semester remains
`409 context_unassigned`; neither case manufactures zero progress.

SchoolYear stores the configured semester ID and lazily resolves the object,
preventing circular SchoolYear/Semester hydration with empty caches. Each request
reads school-year rows fresh; the real `setCurrentSemester` update from A to B
is reflected by the next request. Explicit A continues to return A, including
after class promotion. No new progress/Results cache or schema change exists.

Validation on both tracks:
- `./gradlew test jar shadowJar`: passed; upstream 86 existing + 78 curriculum
  cases, canonical 87 existing + 78 curriculum cases. No failures/errors/skips.
- `npm test --prefix src/test/js`: 44/44 DOM tests per track. Used the existing
  Node 22.21.1 installation under `build/ui-tools` via PATH; no JS changes.
- `git diff --check`: passed.
- Core metadata files and route registration are unchanged against each exact
  base. No new GET/POST routes, no Permission Manager changes.
- Twenty added curriculum cases cover both handlers' explicit/current/history
  behavior, A→B changes with different completed details and all three sum
  invariants, post-promotion grade pinning, absent year/current semester,
  unresolved pointers, invalid explicit inputs, assignment errors, unchanged
  session/scope/teacher/admin rules, legacy oversubscription, cold caches,
  required fields and unchanged non-progress requests, and transferred terminal
  completion values with preserved A→B→C history. All prior tests remain enabled.

Changed feature files: `SchoolYear.java`, `Curriculum.java`,
`CurriculumRequestHandler.java`, `CurriculumTest.java` and
`docs/student-curriculum-contexts.md`. This canonical report is the only extra file.
No deployment, DEMO/PROD access, service action, live database operation, other
repository modification or PR was performed. Push is limited to the two named
new branches in synchronierer/student-database, without force or merge.
