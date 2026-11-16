package org.example.disciplinaryrecordsmanagement.dto;

import java.util.List;

public final class RosterDto {
    private RosterDto() {}

    public record Issue(int row, String message) {}
    public record Check(int rows, boolean ready, List<Issue> issues) {}
    public record ClassImport(int classCount, int counselorCount) {}
    public record StudentImport(int studentCount) {}
}
