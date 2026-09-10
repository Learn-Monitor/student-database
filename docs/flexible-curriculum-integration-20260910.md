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
