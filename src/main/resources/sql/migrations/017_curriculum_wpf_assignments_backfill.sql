INSERT OR IGNORE INTO curriculum_individual_assignments(student,semester,assignment_group,subject) SELECT student,semester,'WPF',subject FROM curriculum_wpf_assignments;
