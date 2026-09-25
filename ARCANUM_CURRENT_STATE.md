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
