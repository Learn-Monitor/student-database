#!/usr/bin/env python3
"""Deterministic, guarded DEMO curriculum builder.

The source manifest is a sanitized, positive-list export of PROD fachdata.  It
contains no person identifiers, credentials, completions, or secrets.  This
tool deliberately keeps account tables untouched and has separate inventory,
dry-run, and apply modes.
"""

from __future__ import annotations

import argparse
import json
import os
import sqlite3
import sys
from collections import Counter, defaultdict
from pathlib import Path

LIVE_DEMO = Path("/srv/arcanum/demo/shared/database.db")
WORKCOPY_PREFIX = Path("/srv/arcanum/dev/validation")
EXPECTED_TEACHERS = {
    "Leni.Lehrerin": {"Mathematik": {"6a", "6b"}},
    "Lothar.Lehrer": {"Mathematik": {"6c", "6d"}},
    "Tanja.Tafel": {"Englisch": {"6a", "6b", "6c", "6d"}},
    "Bernd.Bleistift": {"Deutsch": {"6a", "6b"}, "Reli02": set()},
    "Frieda.Füller": {"Deutsch": {"6c", "6d"}, "Ethik01": set()},
    "Anton.Atlas": {"Naturwissenschaften": {"6a", "6b", "6c", "6d"}, "Reli03": set()},
    "Klara.Kreide": {"Gesellschaftslehre": {"6a", "6b", "6c", "6d"}, "WPF Sport und Gesundheit": set()},
    "Gustav.Globus": {"WPF Ökologie": set()},
    "Paula.Papier": {"WPF Darstellendes Spiel": set()},
    "Martin.Mappe": {"WPF Soziales und Familie": set()},
    "Sonja.Schere": {"WPF Technologie und Wirtschaft": set()},
    "Rainer.Radiergummi": {"WPF Französisch": set()},
}
REGULAR_TEACHER = {
    ("Mathematik", "6a"): "Leni.Lehrerin", ("Mathematik", "6b"): "Leni.Lehrerin",
    ("Mathematik", "6c"): "Lothar.Lehrer", ("Mathematik", "6d"): "Lothar.Lehrer",
    **{("Englisch", c): "Tanja.Tafel" for c in ("6a", "6b", "6c", "6d")},
    ("Deutsch", "6a"): "Bernd.Bleistift", ("Deutsch", "6b"): "Bernd.Bleistift",
    ("Deutsch", "6c"): "Frieda.Füller", ("Deutsch", "6d"): "Frieda.Füller",
    **{("Naturwissenschaften", c): "Anton.Atlas" for c in ("6a", "6b", "6c", "6d")},
    **{("Gesellschaftslehre", c): "Klara.Kreide" for c in ("6a", "6b", "6c", "6d")},
}
INDIVIDUAL_TEACHER = {
    "WPF Ökologie": "Gustav.Globus", "WPF Darstellendes Spiel": "Paula.Papier",
    "WPF Soziales und Familie": "Martin.Mappe", "WPF Technologie und Wirtschaft": "Sonja.Schere",
    "WPF Französisch": "Rainer.Radiergummi", "WPF Sport und Gesundheit": "Klara.Kreide",
    "Reli01": "Leni.Lehrerin", "Reli02": "Bernd.Bleistift", "Reli03": "Anton.Atlas",
    "Ethik01": "Frieda.Füller", "Ethik02": "Tanja.Tafel",
}


def die(message: str) -> None:
    raise SystemExit("DEMO-DATA REFUSED: " + message)


def guard_database(path: Path, mode: str) -> Path:
    path = path.expanduser().absolute()
    real = Path(os.path.realpath(path))
    lower = str(real).lower()
    if "/prod/" in lower or "production" in lower:
        die("PROD database path refused")
    if mode == "apply":
        allowed = real == LIVE_DEMO or (real.parent == WORKCOPY_PREFIX and real.name.startswith("demo-data-workcopy"))
        if not allowed:
            die("apply requires the live DEMO database or an explicit DEV workcopy")
    if not real.is_file():
        die(f"database does not exist: {real}")
    return real


def connect(path: Path, write: bool) -> sqlite3.Connection:
    if write:
        db = sqlite3.connect(path)
    else:
        db = sqlite3.connect(f"file:{path}?mode=ro", uri=True)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA query_only=ON" if not write else "PRAGMA foreign_keys=ON")
    db.execute("PRAGMA busy_timeout=10000")
    return db


def rows(db: sqlite3.Connection, sql: str, args=()):
    return db.execute(sql, args).fetchall()


def manifest(path: Path) -> dict:
    if not path.is_file():
        die(f"sanitized PROD manifest missing: {path}")
    out = {"subjects": {}, "groups": [], "central": [], "flex_topics": [], "flex_tasks": [], "central_releases": [], "flex_releases": []}
    for line_no, line in enumerate(path.read_text(encoding="utf-8").splitlines(), 1):
        if not line.strip():
            continue
        p = line.split("|")
        kind = p[0]
        if kind == "META":
            if p[2] != "2026/27" or p[5] != "HJ1": die(f"manifest line {line_no}: wrong school year/semester")
        elif kind == "SUBJECT_TYPE":
            out["subjects"][p[1]] = {"wpf": int(p[2]), "mode": p[3], "group": p[4] or None}
        elif kind == "COURSE_GROUP":
            out["groups"].append({"subject": p[1], "grade": int(p[2]), "semester": p[3], "group": p[4], "members": int(p[5])})
        elif kind == "CENTRAL":
            out["central"].append({"subject": p[1], "topic_no": int(p[2]), "topic": p[3], "stage_no": int(p[4]), "stage": p[5], "niveau": int(p[6]), "tokens": int(p[7])})
        elif kind == "FLEX_TOPIC":
            out["flex_topics"].append({"subject": p[1], "class": p[2], "grade": int(p[3]), "name": p[4]})
        elif kind == "FLEX_TASK":
            out["flex_tasks"].append({"subject": p[1], "class": p[2], "grade": int(p[3]), "name": p[4], "tokens": int(p[5]), "topic": p[6] or None})
        elif kind in ("CENTRAL_RELEASE", "CENTRAL_TOPIC_RELEASE"):
            out["central_releases"].append({"kind": "task" if kind == "CENTRAL_RELEASE" else "topic", "subject": p[1], "class": p[2], "topic_no": int(p[3]), "stage_no": int(p[4]), "active": int(p[5])})
        elif kind == "FLEX_RELEASE":
            out["flex_releases"].append({"subject": p[1], "class": p[2], "kind": p[3].lower(), "name": p[4], "active": int(p[5])})
        else:
            die(f"manifest line {line_no}: unsupported row {kind}")
    if not out["central"] or not out["subjects"]:
        die("manifest has no curriculum")
    return out


def inventory(db: sqlite3.Connection) -> dict:
    names = [r[0] for r in rows(db, "SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")]
    counts = {name: int(rows(db, f'SELECT COUNT(*) FROM "{name}"')[0][0]) for name in names if name != "sqlite_sequence"}
    fk = rows(db, "SELECT \"table\",parent,COUNT(*) AS n FROM pragma_foreign_key_check GROUP BY \"table\",parent ORDER BY \"table\",parent")
    return {
        "integrity": rows(db, "PRAGMA integrity_check")[0][0],
        "fk_total": sum(int(r[2]) for r in fk),
        "fk_by_table": [{"table": r[0], "parent": r[1], "count": int(r[2])} for r in fk],
        "counts": counts,
        "school_years": [dict(r) for r in rows(db, "SELECT id,label,start_date,end_date,current_semester FROM school_years ORDER BY id")],
        "accounts": {"students": int(rows(db, "SELECT COUNT(*) FROM students")[0][0]), "teachers": int(rows(db, "SELECT COUNT(*) FROM teachers")[0][0]), "admins": int(rows(db, "SELECT COUNT(*) FROM admins")[0][0])},
    }


def ids(db: sqlite3.Connection):
    classes = {r["label"]: r["id"] for r in rows(db, "SELECT id,label FROM classes")}
    subjects = {r["name"]: r["id"] for r in rows(db, "SELECT id,name FROM subjects")}
    teachers = {r["email"]: r["id"] for r in rows(db, "SELECT id,email FROM teachers")}
    students = [dict(r) for r in rows(db, "SELECT id,email,class FROM students WHERE class IN (1,2,3,4) AND email NOT LIKE 'demo.%' ORDER BY lower(email),id")]
    return classes, subjects, teachers, students


def plan(db: sqlite3.Connection, m: dict) -> dict:
    classes, subjects, teachers, students = ids(db)
    required_subjects = set(m["subjects"]) | {x["subject"] for x in m["central"]} | {x["subject"] for x in m["flex_topics"]}
    missing_teachers = sorted(set(EXPECTED_TEACHERS) - set(teachers))
    missing_classes = sorted(set(("Nicht zugeordnet", "6a", "6b", "6c", "6d")) - set(classes))
    return {
        "students": len(students), "required_subjects": sorted(required_subjects),
        "missing_subjects": sorted(required_subjects - set(subjects)), "missing_classes": missing_classes,
        "missing_teachers": missing_teachers, "central_rows": len(m["central"]),
        "flex_topics": len(m["flex_topics"]), "flex_tasks": len(m["flex_tasks"]),
        "source_groups": len(m["groups"]), "source_releases": len(m["central_releases"]) + len(m["flex_releases"]),
        "synthetic_fixtures": ["DEMO Test zentral Anton.Apfel", "DEMO Test flexibel Egon.Eimer", "DEMO Test kombiniert Fina.Fuchs"],
    }


def ensure_subject(db, name: str) -> int:
    row = db.execute("SELECT id FROM subjects WHERE name=?", (name,)).fetchone()
    if row: return row[0]
    cur = db.execute("INSERT INTO subjects(name) VALUES(?)", (name,))
    return cur.lastrowid


def ensure_class(db, label: str, grade: int) -> int:
    row = db.execute("SELECT id FROM classes WHERE label=? AND grade=?", (label, grade)).fetchone()
    if row: return row[0]
    return db.execute("INSERT INTO classes(label,grade,active) VALUES(?,?,1)", (label, grade)).lastrowid


def upsert(db: sqlite3.Connection, sql: str, args):
    db.execute(sql, args)


def apply_data(db: sqlite3.Connection, m: dict) -> dict:
    db.execute("PRAGMA foreign_keys=ON")
    if not any(row[1] == "archived" for row in db.execute("PRAGMA table_info(semesters)")):
        db.execute("ALTER TABLE semesters ADD COLUMN archived INTEGER NOT NULL DEFAULT 0")
    baseline_fk = {tuple(r) for r in rows(db, 'SELECT "table",rowid,parent,fkid FROM pragma_foreign_key_check')}
    db.execute("BEGIN IMMEDIATE")
    try:
        sy = db.execute("SELECT id FROM school_years WHERE label='2026/27'").fetchone()
        if sy is None:
            sy = (db.execute("INSERT INTO school_years(label,week_count,current_week,start_date,end_date) VALUES('2026/27',39,1,'2026-08-01','2027-07-31')").lastrowid,)
        year = sy[0]
        sem = db.execute("SELECT id FROM semesters WHERE label='2026_27_HJ1'").fetchone()
        if sem is None:
            sem = (db.execute("INSERT INTO semesters(label,position,school_year) VALUES('2026_27_HJ1',1,?)", (year,)).lastrowid,)
        semester = sem[0]
        db.execute("UPDATE school_years SET current_semester=? WHERE id=?", (semester, year))

        class_ids = {"0": ensure_class(db, "Nicht zugeordnet", 0),
                     **{c: ensure_class(db, c, 5) for c in ("5a", "5b", "5c", "5d")},
                     **{c: ensure_class(db, c, 6) for c in ("6a", "6b", "6c", "6d")}}
        demo_umbau = db.execute("SELECT id FROM classes WHERE label='DEMO-Umbau 900010'").fetchone()
        if demo_umbau:
            class_ids["DEMO-Umbau 900010"] = demo_umbau[0]
        subject_names = set(m["subjects"]) | {x["subject"] for x in m["central"]} | {x["subject"] for x in m["flex_topics"]}
        subject_ids = {name: ensure_subject(db, name) for name in sorted(subject_names)}
        teacher_ids = {r["email"]: r["id"] for r in rows(db, "SELECT id,email FROM teachers")}
        missing = sorted(set(EXPECTED_TEACHERS) - set(teacher_ids))
        if missing: die("known DEMO teacher missing: " + ",".join(missing))
        teacher_ids = {k: teacher_ids[k] for k in EXPECTED_TEACHERS}

        # One deterministic tutor per active DEMO class.  This is the
        # authoritative semester-scoped tutor relation; legacy teacher_classes
        # is not used to decide the tutor.
        tutor_by_class = {
            "5a": "Leni.Lehrerin", "5b": "Tanja.Tafel", "5c": "Klara.Kreide", "5d": "Anton.Atlas",
            "6a": "Paula.Papier", "6b": "Martin.Mappe", "6c": "Sonja.Schere", "6d": "Rainer.Radiergummi",
            "DEMO-Umbau 900010": "Leni.Lehrerin",
        }
        for class_label, teacher_name in tutor_by_class.items():
            db.execute("INSERT INTO curriculum_class_tutors(semester,class,teacher,tutor_slot) VALUES(?,?,?,1) ON CONFLICT(semester,class,tutor_slot) DO UPDATE SET teacher=excluded.teacher", (semester, class_ids[class_label], teacher_ids[teacher_name]))

        individual = {name: v for name, v in m["subjects"].items() if v["mode"] == "INDIVIDUAL"}
        all_subjects = sorted(subject_ids)
        for name in all_subjects:
            typ = m["subjects"].get(name, {"wpf": 0, "mode": "REGULAR", "group": None})
            db.execute("INSERT INTO curriculum_subject_types(subject,wpf,mode,assignment_group) VALUES(?,?,?,?) ON CONFLICT(subject) DO UPDATE SET wpf=excluded.wpf,mode=excluded.mode,assignment_group=excluded.assignment_group", (subject_ids[name], typ["wpf"], typ["mode"], typ["group"]))
            db.execute("INSERT OR IGNORE INTO curriculum_grade_subjects(grade,semester,subject) VALUES(6,?,?)", (semester, subject_ids[name]))
            db.execute("INSERT OR IGNORE INTO gradesubjects(grade,subject) VALUES(6,?)", (subject_ids[name],))

        # The DEMO currently has no grade-5 student accounts.  Keep the
        # fachliche frame and religion/ethics groups available without
        # fabricating people or memberships.
        grade5_individual = [name for name in sorted(individual) if m["subjects"].get(name, {}).get("group") == "RELIGION_ETHIK"]
        for name in all_subjects:
            db.execute("INSERT OR IGNORE INTO curriculum_grade_subjects(grade,semester,subject) VALUES(5,?,?)", (semester, subject_ids[name]))
            db.execute("INSERT OR IGNORE INTO gradesubjects(grade,subject) VALUES(5,?)", (subject_ids[name],))
            if name in grade5_individual:
                teacher = teacher_ids[INDIVIDUAL_TEACHER.get(name, "Leni.Lehrerin")]
                db.execute("INSERT INTO curriculum_grade_teachers(semester,grade,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,grade,subject) DO UPDATE SET teacher=excluded.teacher", (semester,5,subject_ids[name],teacher))

        group_ids = {}
        for name, typ in individual.items():
            teacher = teacher_ids[INDIVIDUAL_TEACHER.get(name, "Leni.Lehrerin")]
            row = db.execute("SELECT id FROM course_groups WHERE subject=? AND grade=6 AND semester=?", (subject_ids[name], semester)).fetchone()
            label = f"DEMO HJ1 {name} Jahrgang 6"
            if row:
                group_ids[name] = row[0]
                db.execute("UPDATE course_groups SET teacher=?,assignment_group=?,name=?,active=1 WHERE id=?", (teacher, typ["group"] or "INDIVIDUAL", label, row[0]))
            else:
                gid = 600000 + len(group_ids) + 1
                db.execute("INSERT INTO course_groups(id,subject,grade,semester,teacher,assignment_group,name,active) VALUES(?,?,?,?,?,?,?,1)", (gid, subject_ids[name], 6, semester, teacher, typ["group"] or "INDIVIDUAL", label))
                group_ids[name] = gid

        for name in grade5_individual:
            teacher = teacher_ids[INDIVIDUAL_TEACHER.get(name, "Leni.Lehrerin")]
            row = db.execute("SELECT id FROM course_groups WHERE subject=? AND grade=5 AND semester=?", (subject_ids[name], semester)).fetchone()
            label = f"DEMO HJ1 {name} Jahrgang 5"
            if row:
                db.execute("UPDATE course_groups SET teacher=?,assignment_group=?,name=?,active=1 WHERE id=?", (teacher, m["subjects"][name].get("group") or "RELIGION_ETHIK", label, row[0]))
            else:
                gid = 650000 + len(group_ids) + 1
                db.execute("INSERT INTO course_groups(id,subject,grade,semester,teacher,assignment_group,name,active) VALUES(?,?,?,?,?,?,?,1)", (gid, subject_ids[name], 5, semester, teacher, m["subjects"][name].get("group") or "RELIGION_ETHIK", label))
                group_ids[f"5:{name}"] = gid

        central_ids = {}
        for x in m["central"]:
            key = (x["subject"], x["topic_no"], x["stage_no"])
            sid = subject_ids[x["subject"]]
            topic = db.execute("SELECT id FROM topics WHERE subject=? AND grade=6 AND semester=? AND number=?", (sid, semester, x["topic_no"])).fetchone()
            if topic is None:
                tid = db.execute("INSERT INTO topics(name,subject,grade,number,semester) VALUES(?,?,?,?,?)", (x["topic"], sid, 6, x["topic_no"], semester)).lastrowid
            else:
                tid = topic[0]; db.execute("UPDATE topics SET name=? WHERE id=?", (x["topic"], tid))
            task = db.execute("SELECT id FROM tasks WHERE topic=? AND stage_number=?", (tid, x["stage_no"])).fetchone()
            if task is None:
                task_id = db.execute("INSERT INTO tasks(topic,name,niveau,stage_number,tokens) VALUES(?,?,?,?,?)", (tid, x["stage"], x["niveau"], x["stage_no"], x["tokens"])).lastrowid
            else:
                task_id = task[0]; db.execute("UPDATE tasks SET name=?,niveau=?,tokens=? WHERE id=?", (x["stage"], x["niveau"], x["tokens"], task_id))
            central_ids[key] = (tid, task_id)

        def regular_teacher(subject, class_label): return teacher_ids[REGULAR_TEACHER[(subject, class_label)]]
        regular_subjects = sorted({x["subject"] for x in m["central"] if x["subject"] not in individual})
        for subject in regular_subjects:
            db.execute("INSERT INTO curriculum_grade_teachers(semester,grade,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,grade,subject) DO UPDATE SET teacher=excluded.teacher", (semester,6,subject_ids[subject],regular_teacher(subject,"6a")))
            for c in ("6a","6b","6c","6d"):
                teacher = regular_teacher(subject,c)
                db.execute("INSERT INTO curriculum_class_teachers(semester,class,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,class,subject) DO UPDATE SET teacher=excluded.teacher", (semester,class_ids[c],subject_ids[subject],teacher))
                db.execute("INSERT OR IGNORE INTO teacher_classes(teacher_id,class_id) VALUES(?,?)", (teacher,class_ids[c]))
                db.execute("INSERT OR IGNORE INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)", (teacher,subject_ids[subject]))

        # The current runtime backfill validates every INDIVIDUAL subject
        # context through curriculum_grade_teachers, even when the additive
        # course_group columns are already populated.  Keep that legacy
        # source-of-truth row deterministic and aligned with the CourseGroup
        # owner used below.
        for subject in sorted(individual):
            teacher = teacher_ids[INDIVIDUAL_TEACHER[subject]]
            db.execute("INSERT INTO curriculum_grade_teachers(semester,grade,subject,teacher) VALUES(?,?,?,?) ON CONFLICT(semester,grade,subject) DO UPDATE SET teacher=excluded.teacher", (semester,6,subject_ids[subject],teacher))
        for subject, teacher_name in INDIVIDUAL_TEACHER.items():
            if subject in subject_ids:
                db.execute("INSERT OR IGNORE INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)", (teacher_ids[teacher_name], subject_ids[subject]))

        # Archive only the explicitly marked old DEMO test semester.  Its
        # rows remain in place, and an active pointer is cleared only for its
        # own synthetic school year.
        old = db.execute("SELECT s.id,s.school_year FROM semesters s JOIN school_years y ON y.id=s.school_year WHERE s.label='DEMO Testhalbjahr 900010' AND y.label='DEMO Umbau 900010'").fetchone()
        if old:
            db.execute("UPDATE semesters SET archived=1 WHERE id=?", (old[0],))
            db.execute("UPDATE school_years SET current_semester=NULL WHERE id=? AND current_semester=?", (old[1], old[0]))

        students = [dict(r) for r in rows(db, "SELECT id,email,class FROM students WHERE class IN (1,2,3,4) AND email NOT LIKE 'demo.%' ORDER BY lower(email),id")]
        if len(students) != 103: die(f"expected 103 known students, found {len(students)}")
        wpf = sorted([s for s,v in individual.items() if v["group"] == "WPF"])
        religion = sorted([s for s,v in individual.items() if v["group"] == "RELIGION_ETHIK"])
        for index, student in enumerate(students):
            c = next(label for label, cid in class_ids.items() if cid == student["class"])
            db.execute("INSERT INTO curriculum_enrolled_students(student,semester,grade) VALUES(?,?,6) ON CONFLICT(student,semester) DO UPDATE SET grade=6", (student["id"],semester))
            chosen = regular_subjects + [wpf[index % len(wpf)], religion[index % len(religion)]]
            for subject in chosen:
                teacher = teacher_ids[INDIVIDUAL_TEACHER[subject]] if subject in individual else regular_teacher(subject,c)
                group = group_ids.get(subject)
                db.execute("INSERT INTO student_curriculum_contexts(student,subject,semester,teacher,class,grade,course_group) VALUES(?,?,?,?,?,?,?) ON CONFLICT(student,subject,semester) DO UPDATE SET teacher=excluded.teacher,class=excluded.class,grade=excluded.grade,course_group=excluded.course_group", (student["id"],subject_ids[subject],semester,teacher,class_ids[c],6,group))
                db.execute("INSERT OR IGNORE INTO teacher_subjects(teacher_id,subject_id) VALUES(?,?)", (teacher,subject_ids[subject]))
                if group:
                    db.execute("INSERT OR IGNORE INTO course_group_members(course_group,student) VALUES(?,?)", (group,student["id"]))
            db.execute("INSERT INTO curriculum_individual_assignments(student,semester,assignment_group,subject) VALUES(?,?,?,?) ON CONFLICT(student,semester,assignment_group) DO UPDATE SET subject=excluded.subject", (student["id"],semester,"WPF",subject_ids[wpf[index % len(wpf)]]))
            db.execute("INSERT INTO curriculum_individual_assignments(student,semester,assignment_group,subject) VALUES(?,?,?,?) ON CONFLICT(student,semester,assignment_group) DO UPDATE SET subject=excluded.subject", (student["id"],semester,"RELIGION_ETHIK",subject_ids[religion[index % len(religion)]]))
            db.execute("INSERT INTO curriculum_wpf_assignments(student,semester,subject) VALUES(?,?,?) ON CONFLICT(student,semester) DO UPDATE SET subject=excluded.subject", (student["id"],semester,subject_ids[wpf[index % len(wpf)]]))

        # Source releases are mapped by subject/class and never by PROD teacher ID.
        for rel in m["central_releases"]:
            if rel["subject"] not in subject_ids or rel["class"] not in class_ids: continue
            task_key = (rel["subject"], rel["topic_no"], rel["stage_no"])
            topic_key = next((k for k in central_ids if k[0] == rel["subject"] and k[1] == rel["topic_no"]), None)
            if topic_key is None: continue
            key = task_key if rel["kind"] == "task" else topic_key
            if key not in central_ids: continue
            sid, cid, tid = subject_ids[rel["subject"]], class_ids[rel["class"]], central_ids[key][1]
            if rel["subject"] in individual:
                gid = group_ids[rel["subject"]]
                table = "course_group_task_releases" if rel["kind"] == "task" else "course_group_topic_releases"
                col = "task" if rel["kind"] == "task" else "topic"
                value = central_ids[key][1] if rel["kind"] == "task" else central_ids[key][0]
                db.execute(f"INSERT INTO {table}(course_group,{col},active) VALUES(?,?,?) ON CONFLICT(course_group,{col}) DO UPDATE SET active=excluded.active", (gid,value,rel["active"]))
            else:
                teacher = regular_teacher(rel["subject"],rel["class"])
                table = "curriculum_task_releases" if rel["kind"] == "task" else "curriculum_topic_releases"
                col = "task" if rel["kind"] == "task" else "topic"
                value = tid if rel["kind"] == "task" else central_ids[key][0]
                db.execute(f"INSERT INTO {table}(teacher,class,subject,semester,{col},active) VALUES(?,?,?,?,?,?) ON CONFLICT(teacher,class,subject,semester,{col}) DO UPDATE SET active=excluded.active", (teacher,cid,sid,semester,value,rel["active"]))

        # Ensure one visible central topic per regular context and one WPF group.
        for subject in regular_subjects:
            first = next(x for x in m["central"] if x["subject"] == subject)
            for c in ("6a","6b","6c","6d"):
                teacher = regular_teacher(subject,c); cid=class_ids[c]; sid=subject_ids[subject]
                db.execute("INSERT INTO curriculum_topic_releases(teacher,class,subject,semester,topic,active) VALUES(?,?,?,?,?,1) ON CONFLICT DO UPDATE SET active=1", (teacher,cid,sid,semester,central_ids[(subject,first["topic_no"],first["stage_no"])][0]))
        for subject in ("WPF Darstellendes Spiel", "WPF Ökologie"):
            if subject in group_ids:
                first = next(x for x in m["central"] if x["subject"] == subject)
                db.execute("INSERT INTO course_group_topic_releases(course_group,topic,active) VALUES(?,?,1) ON CONFLICT DO UPDATE SET active=1", (group_ids[subject],central_ids[(subject,first["topic_no"],first["stage_no"])][0]))

        flex_topic_ids = {}
        flex_teacher = {("Mathematik","6a"): "Leni.Lehrerin", ("Mathematik","6d"): "Lothar.Lehrer", ("Naturwissenschaften","6c"): "Anton.Atlas"}
        for x in m["flex_topics"]:
            owner=teacher_ids[flex_teacher[(x["subject"],x["class"])]]; sid=subject_ids[x["subject"]]; cid=class_ids[x["class"]]
            row=db.execute("SELECT id FROM flexible_topics WHERE owner_teacher=? AND subject=? AND class=? AND semester=? AND name=?",(owner,sid,cid,semester,x["name"])).fetchone()
            if row: fid=row[0]; db.execute("UPDATE flexible_topics SET grade=? WHERE id=?",(x["grade"],fid))
            else: fid=db.execute("INSERT INTO flexible_topics(owner_teacher,subject,class,semester,grade,name) VALUES(?,?,?,?,?,?)",(owner,sid,cid,semester,x["grade"],x["name"])).lastrowid
            flex_topic_ids[(x["subject"],x["class"],x["name"])] = fid
        flex_task_ids={}
        for x in m["flex_tasks"]:
            owner=teacher_ids[flex_teacher[(x["subject"],x["class"])]]; sid=subject_ids[x["subject"]]; cid=class_ids[x["class"]]
            row=db.execute("SELECT id FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=? AND name=?",(owner,sid,cid,semester,x["name"])).fetchone()
            if row: fid=row[0]; db.execute("UPDATE flexible_tasks SET grade=?,tokens=? WHERE id=?",(x["grade"],x["tokens"],fid))
            else: fid=db.execute("INSERT INTO flexible_tasks(owner_teacher,subject,class,semester,grade,name,tokens) VALUES(?,?,?,?,?,?,?)",(owner,sid,cid,semester,x["grade"],x["name"],x["tokens"])).lastrowid
            flex_task_ids[(x["subject"],x["class"],x["name"])] = fid
            if x["topic"]:
                topic_id=flex_topic_ids.get((x["subject"],x["class"],x["topic"]))
                if topic_id: db.execute("INSERT INTO flexible_task_topics(flexible_task,flexible_topic) VALUES(?,?) ON CONFLICT(flexible_task) DO UPDATE SET flexible_topic=excluded.flexible_topic",(fid,topic_id))
        for rel in m["flex_releases"]:
            key=(rel["subject"],rel["class"],rel["name"]); val=(flex_topic_ids if rel["kind"]=="topic" else flex_task_ids).get(key)
            if val is None: continue
            owner=teacher_ids[flex_teacher[(rel["subject"],rel["class"])] ] ; sid=subject_ids[rel["subject"]]; cid=class_ids[rel["class"]]
            table="flexible_topic_releases" if rel["kind"]=="topic" else "flexible_task_releases"; col="flexible_topic" if rel["kind"]=="topic" else "flexible_task"
            db.execute(f"INSERT INTO {table}(teacher,class,subject,semester,{col},active) VALUES(?,?,?,?,?,?) ON CONFLICT(teacher,class,subject,semester,{col}) DO UPDATE SET active=excluded.active",(owner,cid,sid,semester,val,rel["active"]))

        # Explicitly marked synthetic DEMO test area for Results and flexible completion.
        owner=teacher_ids["Leni.Lehrerin"]; sid=subject_ids["Mathematik"]; cid=class_ids["6a"]
        tname="DEMO Test flexibel"; taskname="DEMO Test flexibel – bestätigte Leistung"
        row=db.execute("SELECT id FROM flexible_topics WHERE owner_teacher=? AND subject=? AND class=? AND semester=? AND name=?",(owner,sid,cid,semester,tname)).fetchone()
        ftid=row[0] if row else db.execute("INSERT INTO flexible_topics(owner_teacher,subject,class,semester,grade,name) VALUES(?,?,?,?,?,?)",(owner,sid,cid,semester,6,tname)).lastrowid
        row=db.execute("SELECT id FROM flexible_tasks WHERE owner_teacher=? AND subject=? AND class=? AND semester=? AND name=?",(owner,sid,cid,semester,taskname)).fetchone()
        ftask=row[0] if row else db.execute("INSERT INTO flexible_tasks(owner_teacher,subject,class,semester,grade,name,tokens) VALUES(?,?,?,?,?,?,?)",(owner,sid,cid,semester,6,taskname,6)).lastrowid
        db.execute("INSERT INTO flexible_task_topics(flexible_task,flexible_topic) VALUES(?,?) ON CONFLICT(flexible_task) DO UPDATE SET flexible_topic=excluded.flexible_topic",(ftask,ftid))
        db.execute("INSERT INTO flexible_topic_releases(teacher,class,subject,semester,flexible_topic,active) VALUES(?,?,?,?,?,1) ON CONFLICT DO UPDATE SET active=1",(owner,cid,sid,semester,ftid))
        db.execute("INSERT INTO flexible_task_releases(teacher,class,subject,semester,flexible_task,active) VALUES(?,?,?,?,?,1) ON CONFLICT DO UPDATE SET active=1",(owner,cid,sid,semester,ftask))
        by_email={s["email"]:s["id"] for s in students}
        central_fixture=central_ids[("Mathematik",1,1)][1]
        for email in ("Anton.Apfel","Fina.Fuchs"):
            db.execute("INSERT INTO taskstats(student,task,status) VALUES(?,?,2) ON CONFLICT(student,task) DO UPDATE SET status=2,last_updated=CURRENT_TIMESTAMP",(by_email[email],central_fixture))
        for email in ("Egon.Eimer","Fina.Fuchs"):
            db.execute("INSERT INTO completed_flexible_tasks(student,flexible_task) VALUES(?,?) ON CONFLICT DO NOTHING",(by_email[email],ftask))

        # Keep the pre-existing demo.umbau accounts untouched and validate before commit.
        after_fk = {tuple(r) for r in rows(db, 'SELECT "table",rowid,parent,fkid FROM pragma_foreign_key_check')}
        new_fk = after_fk - baseline_fk
        if new_fk: die(f"new transaction would leave {len(new_fk)} new FK violations")
        db.commit()
        return {"semester": semester, "year": year, "students": len(students), "central_tasks": len(central_ids), "flex_tasks": len(flex_task_ids)+1, "course_groups": len(group_ids), "synthetic_completions": 4}
    except Exception:
        db.rollback()
        raise


def main() -> None:
    ap=argparse.ArgumentParser()
    ap.add_argument("--mode", choices=("inventory","dry-run","apply"), required=True)
    ap.add_argument("--database", required=True)
    ap.add_argument("--manifest", required=True)
    ap.add_argument("--confirm-demo-apply", action="store_true")
    args=ap.parse_args()
    db_path=guard_database(Path(args.database),args.mode)
    m=manifest(Path(args.manifest))
    if args.mode == "inventory":
        with connect(db_path,False) as db: print(json.dumps(inventory(db),ensure_ascii=False,indent=2))
        return
    with connect(db_path,args.mode=="apply") as db:
        p=plan(db,m)
        print(json.dumps({"mode":args.mode,"database":str(db_path),"plan":p},ensure_ascii=False,indent=2))
        if args.mode == "dry-run": return
        if not args.confirm_demo_apply: die("missing --confirm-demo-apply")
        print(json.dumps({"applied":apply_data(db,m)},ensure_ascii=False,indent=2))


if __name__ == "__main__": main()
