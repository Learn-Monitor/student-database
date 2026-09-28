# DEMO Tutor-Graduation Fixture Runner

This is a manually invoked, non-runtime tool. It is compiled against the already built application JAR and is never packaged into the web application.

The runner requires all of:

- `ARCANUM_DEMO_FIXTURE=1`
- `--confirm-demo-fixture`
- `--database /srv/arcanum/demo/shared/database.db`
- a run id matching `E2E_TUTOR_GRAD_<id>`

It refuses production paths and does not start a web server. Creation uses the regular `Teacher.registerTeacher`, `Student.registerStudentWithPassword`, `Admin.create`, `SchoolClass.getOrCreate`, and `CurriculumEnrollment.assignClassTutors` service paths. The output JSON contains disposable credentials and must remain owner-readable.

Example (from the repository root, using the existing tested Fat-JAR):

```sh
mkdir -p /tmp/demo-runner-classes
javac -cp build/libs/student-database-v2.0.0-fat.jar \
  -d /tmp/demo-runner-classes tools/demo-e2e/DemoFixtureRunner.java
ARCANUM_DEMO_FIXTURE=1 java \
  -cp /tmp/demo-runner-classes:build/libs/student-database-v2.0.0-fat.jar \
  tools.demo_e2e.DemoFixtureRunner \
  --mode create --database /srv/arcanum/demo/shared/database.db \
  --run-id E2E_TUTOR_GRAD_<unique-id> --confirm-demo-fixture \
  --output /tmp/demo-fixture.json
```

Use the same run id with `--mode cleanup`. Cleanup is restricted to that run marker and is idempotent after the first successful run.

The runner's safety checks were exercised: missing environment confirmation and a production database path are rejected before database initialization. A real DEMO run was created and fully removed; DEMO integrity remained `ok` and the FK baseline remained `756`.
