package org.example.disciplinaryrecordsmanagement.service;

import java.util.ArrayList;
import java.util.HashMap;

import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.dto.RoleGuideDto;
import org.example.disciplinaryrecordsmanagement.dto.RoleGuideDto.IdentityCount;
import org.example.disciplinaryrecordsmanagement.mapper.RoleGuideMapper;
import org.example.disciplinaryrecordsmanagement.model.Role;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.DepartmentCodes.DISCIPLINE;

@Service
public class RoleGuideService {
    private static final int CORE_ADMIN_MAXIMUM = 1;
    private static final String DISCIPLINE_HEAD = "DISCIPLINE_HEAD";

    private final RoleGuideMapper mapper;
    private final BusinessPolicy policy;

    RoleGuideService(RoleGuideMapper mapper, BusinessPolicy policy) {
        this.mapper = mapper;
        this.policy = policy;
    }

    @Transactional(readOnly = true)
    public RoleGuideDto guide() {
        var base = mapper.baseCounts();
        var counts = new HashMap<String, Long>();
        for (var row : mapper.grantCounts(Role.DEPARTMENT_HEAD, DISCIPLINE)) {
            counts.put(row.disciplineHead() ? DISCIPLINE_HEAD : row.roleCode().name(), row.accountCount());
        }

        var identities = new ArrayList<IdentityCount>();
        identities.add(new IdentityCount("CORE_ADMIN", base.coreAdminCount(), CORE_ADMIN_MAXIMUM));
        identities.add(new IdentityCount("STUDENT", base.studentCount(), null));
        identities.add(new IdentityCount("UNASSIGNED", base.unassignedCount(), null));
        identities.add(new IdentityCount(DISCIPLINE_HEAD, counts.getOrDefault(DISCIPLINE_HEAD, 0L), null));
        for (Role role : Role.values()) {
            identities.add(new IdentityCount(role.name(), counts.getOrDefault(role.name(), 0L), null));
        }
        return new RoleGuideDto(policy.editDays(), identities);
    }
}
