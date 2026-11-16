package org.example.disciplinaryrecordsmanagement.model;

public enum Role {
    PRESIDENT, TEACHER, QUALITY_HEAD, DISCIPLINE_OFFICE, OFFICER, DEPARTMENT_HEAD, CLASS_COMMITTEE;

    public boolean departmentScoped() {
        return this == OFFICER || this == DEPARTMENT_HEAD;
    }
}
