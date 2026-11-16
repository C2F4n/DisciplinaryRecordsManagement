package org.example.disciplinaryrecordsmanagement.model;

import java.util.List;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import org.example.disciplinaryrecordsmanagement.dto.AccountDto.Grant;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;

import static org.example.disciplinaryrecordsmanagement.constant.DepartmentCodes.DISCIPLINE;
import static org.example.disciplinaryrecordsmanagement.model.Role.CLASS_COMMITTEE;
import static org.example.disciplinaryrecordsmanagement.model.Role.DEPARTMENT_HEAD;
import static org.example.disciplinaryrecordsmanagement.model.Role.DISCIPLINE_OFFICE;
import static org.example.disciplinaryrecordsmanagement.model.Role.OFFICER;
import static org.example.disciplinaryrecordsmanagement.model.Role.PRESIDENT;
import static org.example.disciplinaryrecordsmanagement.model.Role.QUALITY_HEAD;
import static org.example.disciplinaryrecordsmanagement.model.Role.TEACHER;

public record Viewer(long id, String username, String displayName, Long studentId, Long classId,
                     boolean coreAdmin, List<Grant> grants) {
    private boolean has(Role role) {
        return grants.stream().anyMatch(g -> g.roleCode() == role);
    }

    public SortedSet<String> scopes(Role... roles) {
        Set<Role> requested = Set.of(roles);
        SortedSet<String> result = new TreeSet<>();
        for (var grant : grants) if (requested.contains(grant.roleCode())) result.add(grant.scopeKey());
        return result;
    }

    public boolean allRecords() {
        return coreAdmin || has(PRESIDENT) || has(TEACHER) || scopes(DEPARTMENT_HEAD).contains(DISCIPLINE);
    }

    public boolean canReviewPermissionRequests() {
        return coreAdmin || has(PRESIDENT) || has(TEACHER) || has(DEPARTMENT_HEAD);
    }

    public boolean office() { return coreAdmin || has(DISCIPLINE_OFFICE); }

    public Long committeeClass() {
        return classId != null && scopes(CLASS_COMMITTEE).contains(classId.toString()) ? classId : null;
    }

    public boolean canExportReports() { return allRecords() || has(QUALITY_HEAD); }

    public boolean canViewStatistics() { return allRecords() || has(DEPARTMENT_HEAD); }

    public boolean canViewRecords() {
        return allRecords() || studentId != null || !scopes(OFFICER, DEPARTMENT_HEAD).isEmpty() || office();
    }

    public void requireAdmin() {
        if (!coreAdmin) throw BusinessException.forbidden();
    }
}
