# Realistic synthetic DEMO data

Public DEMO website: https://arcanum-demo.dynv6.net

Public synthetic DEMO accounts (fictional test identities) and the German and
English access/reset notice: https://github.com/synchronierer/arcanum-demo

`realistic_demo_data.py` is a guarded, repeatable DEMO curriculum builder. It
has three independent modes:

```text
python3 tools/demo-data/realistic_demo_data.py --mode inventory --database <db> --manifest <sanitized-manifest.tsv>
python3 tools/demo-data/realistic_demo_data.py --mode dry-run --database <db> --manifest <sanitized-manifest.tsv>
python3 tools/demo-data/realistic_demo_data.py --mode apply --database <db> --manifest <sanitized-manifest.tsv> --confirm-demo-apply
```

The only accepted apply targets are `/srv/arcanum/demo/shared/database.db` and
a named workcopy under `/srv/arcanum/dev/validation`. PROD paths, symlinked
PROD paths, and arbitrary databases are refused. The tool never writes account
tables and never deletes the existing `demo.umbau.*` accounts.

The manifest is a positive-list, anonymized export made read-only from PROD.
It contains only school year/semester, subject types, class labels, neutral
course-group counts, curriculum text, token values, and release patterns. It
contains no PROD person IDs, logins, passwords, hashes, sessions, attendance,
or completions. The deterministic person mapping is:

- known students are ordered by lowercase fachlicher login and enrolled in HJ1;
- each student receives all regular grade-6 subjects;
- one WPF and one RELIGION_ETHIK subject are selected by stable ordinal;
- known DEMO teachers are selected by the documented subject/class mapping in
  the script; course-group and context teacher references are then derived from
  those DEMO IDs;
- central/flexible releases are mapped by subject, class, topic/stage name and
  class label, never by PROD identity IDs;
- three explicit synthetic Results fixtures are marked in the script and are
  limited to DEMO.

Before live apply, create and verify a consistent SQLite backup, record the
inventory and FK set, and run inventory plus dry-run against the fresh current
database. The apply transaction rejects newly introduced FK violations and
rolls back on any invariant failure. A second dry-run on the same database must
produce identical planning output.
