package org.example.disciplinaryrecordsmanagement.dto;

import java.util.List;

public record RoleGuideDto(int editDays, List<IdentityCount> identities) {
    public record IdentityCount(String code, long accountCount, Integer maximum) {}
}
