package de.igslandstuhl.database.api.results;

import de.igslandstuhl.database.api.Student;

public class StudentGenerationResult extends GenerationResult<Student> {
    public StudentGenerationResult(Student student, String password) {
        super(student, password);
    }
    public Student getStudent() {
        return getEntity();
    }

    public int getId() {
        return getStudent().getId();
    }
    public String getFirstName() {
        return getStudent().getFirstName();
    }
    public String getLastName() {
        return getStudent().getLastName();
    }
    public String getEmail() {
        return getStudent().getEmail();
    }

    @Override
    public String toCSVRow() {
        return new StringBuilder().append(this.getStudent().getId()).append(",")
            .append(this.getStudent().getFirstName()).append(",")
            .append(this.getStudent().getLastName()).append(",")
            .append(this.getStudent().getEmail()).append(",")
            .append(this.getPassword()).toString();
    }
}