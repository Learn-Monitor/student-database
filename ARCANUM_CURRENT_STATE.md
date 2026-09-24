# Arcanum current state

## Canonical development line

- `student-database`: `arcanum-2026` at `53e7e42` (tutor foundation, weekly-conversation view, web-path registration, and existing migration-runner fix).
- `permission-manager`: `feature/admin-dashboard-polish-20260919` at `9bd0267`.
- The production source line remains separate; no production deployment was performed by the tutor E2E work.

## DEMO

- Release: `/srv/arcanum/demo/releases/tutor-weekly-20260924T230000Z-53e7e42`
- The DEMO runtime loads the permission manager from `plugins/permission-manager-v1.0.1.jar`; it is built from the Arcanum permission-manager line above.
- Migration `021_curriculum_class_tutors.sql` is applied through the normal application migration runner and stores semester/class/tutor-slot assignments.
- The admin assigns zero, one, or two distinct tutors per semester and class. Tutor classes are server-side scoped.
- “Übersicht für Wochengespräche” is available only to tutors for their assigned classes; ordinary teachers retain the existing student-progress view without this extra entry.
- Weekly conversation data reads the existing canonical progress, earned-coin, grade, and completion logic. It adds no competing coin or assessment calculation.
- Printing uses the browser print view and print CSS; no server-side PDF system is introduced.
- The synthetic DEMO E2E passed, and the database was restored to its pre-E2E snapshot afterward.

## Verification boundary

The tutor feature does not change coin calculation, stage completion, level handling, curriculum-stage semantics, or the existing student-progress rendering. DEMO was changed and tested; PROD remained unchanged.
