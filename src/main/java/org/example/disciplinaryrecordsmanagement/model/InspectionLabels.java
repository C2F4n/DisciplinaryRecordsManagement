package org.example.disciplinaryrecordsmanagement.model;

public final class InspectionLabels {
    private InspectionLabels() {}

    public static String display(String code, String snapshot) {
        if (snapshot != null) return snapshot;
        return switch (code) {
            case "COLLEGE" -> "学院检查";
            case "SCHOOL" -> "学校检查";
            default -> code;
        };
    }
}
