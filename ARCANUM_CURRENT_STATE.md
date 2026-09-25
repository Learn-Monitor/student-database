# Arcanum current state

## Canonical development line

- `student-database`: `arcanum-2026` at `0536be8` (tutor foundation, weekly-conversation view, and static tutor web-path registration).
- `permission-manager`: `feature/admin-dashboard-polish-20260919` at `9bd0267`.
- The production source line remains separate; no production deployment was performed by the tutor E2E work.

## DEMO

- Release: `/srv/arcanum/demo/releases/tutor-static-webpaths-20260925T084500Z-0536be8`
- The service starts `/srv/arcanum/demo/runtime/student-database.jar` from its fixed runtime working directory; promoting a release therefore requires an explicit release-to-runtime JAR promotion.
- Active runtime JAR: commit `0536be8`, SHA256 `9c6ee31e921ebf82303d661eca5d0ec482b8d3603fbbf879a883c16898959af4`.
- `/weekly-conversations.js` and `/tutor-assignments.js` were authenticated HTTP-smoke-tested with synthetic teacher, tutor, and admin accounts and returned HTTP 200.
- The non-tutor/tutor/admin role regression passed; the tutor saw its own class and received HTTP 403 for a foreign class.
- JavaScript regression suite: `219/219 PASS` using portable Node `v24.21.0`.
- DEMO database was restored after the synthetic E2E test; integrity check remained `ok` and the known FK count remained `756`.

## PROD tutor webpath promotion

- The PROD service starts `/srv/arcanum/prod/runtime/run.sh`; its JAR and plugin paths are connected through the existing `current` release wiring.
- Active PROD release: `/srv/arcanum/prod/releases/tutor-static-webpaths-20260925T101500Z-0536be8`.
- Active runtime JAR: student-database commit `0536be8`, SHA256 `9c6ee31e921ebf82303d661eca5d0ec482b8d3603fbbf879a883c16898959af4`.
- Active Permission Manager: commit `9bd0267`, SHA256 `8f9f54976dd1fcb4dd9f58bd4fa829cc0c662ae12b341314b1b180909da3ff52`.
- Migration 021 was applied successfully; `curriculum_class_tutors` has zero FK violations.
- PROD readiness took 206 seconds. The Permission Manager has a long initialization window; deployment smoke tests must use a repeated health-readiness loop rather than a short fixed timeout.
- After readiness, five additional health checks kept Root/Login at HTTP 200 with a stable MainPID and no restart.
- PROD was promoted without synthetic test data; integrity remained `ok` and FK count remained `102`.
- Tutor and weekly-conversation resources are now productive using the same tested binaries as DEMO.
- The DEMO runtime loads the permission manager from `plugins/permission-manager-v1.0.1.jar`; it is built from the Arcanum permission-manager line above.
- Migration `021_curriculum_class_tutors.sql` is applied through the normal application migration runner and stores semester/class/tutor-slot assignments.
- The admin assigns zero, one, or two distinct tutors per semester and class. Tutor classes are server-side scoped.
- “Übersicht für Wochengespräche” is available only to tutors for their assigned classes; ordinary teachers retain the existing student-progress view without this extra entry.
- Weekly conversation data reads the existing canonical progress, earned-coin, grade, and completion logic. It adds no competing coin or assessment calculation.
- Printing uses the browser print view and print CSS; no server-side PDF system is introduced.
- The synthetic DEMO E2E passed, and the database was restored to its pre-E2E snapshot afterward.

## Verification boundary

The tutor feature does not change coin calculation, stage completion, level handling, curriculum-stage semantics, or the existing student-progress rendering. DEMO and PROD were tested without synthetic production data.

## Admin tutor assignment visibility

- The admin dashboard now places `Tutor:innen je Klasse` before the large enrollment/subject-teacher assignment area at `Admin → Schuljahr & Zuordnungen`.
- The existing semester + class tutor endpoints and server-side duplicate-tutor validation are unchanged.
- The UI regression suite verifies DOM order, normal-class filtering, two tutor selectors, persisted selections, save payloads, and visible load errors.
- JavaScript suite: `222/222 PASS`; Java suite and ShadowJar are green.
- DEMO was accepted with synthetic accounts and restored afterward: tutor block visible before enrollment, 1/2 tutor assignment persisted, duplicate assignment rejected, empty assignment accepted, and tutor weekly-conversation access remained intact.
- PROD active release: `/srv/arcanum/prod/releases/admin-tutor-visibility-20260925T103442-2ebc7c8`.
- PROD runtime student-database commit `2ebc7c8`, SHA256 `581bcc9812a0c2451ba77715f648fc80950d3f1f7283a408a877328e359cde22`; existing PROD Permission Manager was unchanged.
- PROD readiness took `191` seconds; the Permission Manager's long initialization requires a repeated readiness loop of up to 300 seconds.
- PROD integrity remained `ok`, FK count remained `102`, and `curriculum_class_tutors` has zero FK violations. No synthetic PROD data was created.

## Permission Manager correction

- The PROD browser symptom was traced to a Permission Manager rollback, not to an HTML/plugin dashboard override.
- PROD had been running PM SHA `4970707aed9c80daba40d3bf453c189b4a27dabc3fe4d95619b4f980cd48ebd3`, whose permission metadata lacked the tutor assignment POST endpoints and `curriculum_tutor_context`.
- The tested PM artifact SHA `8f9f54976dd1fcb4dd9f58bd4fa829cc0c662ae12b341314b1b180909da3ff52` was restored without changing student-database source or database data.
- Active PROD release: `/srv/arcanum/prod/releases/admin-tutor-pm9bd0267-20260925T105036`.
- Runtime student-database SHA remains `581bcc9812a0c2451ba77715f648fc80950d3f1f7283a408a877328e359cde22`.
- PROD readiness after the PM correction took `190` seconds; five subsequent health checks were stable with no restart.
- An unauthenticated direct request to `/tutor-assignments.js` correctly returns `401`; no real PROD credentials were used. The active PM now contains the tutor assignment/context permissions required for the authenticated admin flow.

## Visible tutor-assignment fallback

- Root cause: the dashboard supplied an empty `#admin-tutors` container; if the protected JavaScript was delayed or failed, no visible tutor UI existed. The previous Permission Manager metadata also did not explicitly authorize `/tutor-assignments.js` under `curriculum_manage_enrollment`.
- `html/admin/dashboard.html` now renders a visible heading, explanation, and loading status before JavaScript runs. `tutor-assignments.js` preserves this markup, renders into its content area, and shows a visible error instead of silently leaving an empty block.
- Permission Manager explicitly authorizes `/tutor-assignments.js` with GET/POST under the existing `curriculum_manage_enrollment` admin permission. No new role or tutor logic was added.
- Student commit `6bf822e`; JavaScript suite `225/225 PASS`, Java suite and ShadowJar green. Permission Manager commit `c92387a`; its full test suite and JAR build are green.
- DEMO release `/srv/arcanum/demo/releases/admin-tutor-fallback-20260925T090726Z-6bf822e-c92387a`; runtime student SHA `9563b40df4b4d0836dd86b0972e09b567cf70b3d6c8fb3ca4138fd577da6c335`, PM SHA `1abb5031002bc0a518aca61da885d9f5a44be390e2715c4efa03cd5a61e2a6e9`. Synthetic admin raw-HTML and interactive save/reload/duplicate checks passed; DEMO was restored to integrity `ok`, FK `756`.
- PROD release `/srv/arcanum/prod/releases/admin-tutor-fallback-20260925T091133Z-6bf822e-c92387a`; the runtime uses those same SHAs. Readiness succeeded within the controlled 300-second window, followed by five stable health checks. Root/Login remained HTTP 200, integrity `ok`, FK `102`, and tutor-table FK violations `0`. No synthetic PROD data was created.
- The known Permission Manager startup delay remains operationally relevant: deployment smoke tests must use repeated health checks for up to 300 seconds.

## Weekly conversation script permission

- The authenticated DEMO check initially returned HTTP `403` for `/weekly-conversations.js` while `/my-tutor-classes` remained available. The teacher dashboard and script reference were already correct; the missing explicit Permission Manager path was the cause.
- Permission Manager commit `4d4d6b3` adds `/weekly-conversations.js` to `curriculum_tutor_context` and allows GET alongside the existing POST tutor APIs. Backend tutor ownership and foreign-class `403` checks are unchanged.
- DEMO authenticated checks passed: Teacher script HTTP `200` with JavaScript content type, own tutor class returned, overview loaded, foreign class returned `403`, and the rendered DOM panel contained the heading, class, and print/PDF control. A non-tutor received an empty tutor-class list and no panel. DEMO was restored to integrity `ok`, FK `756`, tutor FK `0`.
- PROD active release: `/srv/arcanum/prod/releases/weekly-permission-20260925T123354Z-c92387a`; active runtime student SHA remains `9563b40df4b4d0836dd86b0972e09b567cf70b3d6c8fb3ca4138fd577da6c335`; active PM SHA is `0dcb4370d666982399bc0b1b8342452d35793eec63d99adb685210186772dfbb`. The runtime plugin was explicitly promoted because the release symlink alone did not update the loaded plugin.
- PROD restarted successfully with stable MainPID `19115`, restart count `0`, five stable Root/Login checks at `200/200`, integrity `ok`, FK `102`, and tutor FK `0`. No synthetic PROD data was created.
