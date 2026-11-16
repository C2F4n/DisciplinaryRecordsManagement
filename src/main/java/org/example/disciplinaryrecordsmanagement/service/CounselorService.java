package org.example.disciplinaryrecordsmanagement.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Create;
import org.example.disciplinaryrecordsmanagement.dto.CounselorDto.Option;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.CounselorMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.CLASS_COUNSELOR_COUNT;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.CLASS_COUNSELOR_DUPLICATE;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.COUNSELOR_EXISTS;
import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.COUNSELOR_REQUIRED;
import static org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.MAX_CLASS_COUNSELORS;
import static org.springframework.util.StringUtils.hasText;

@Service
public class CounselorService {
    private final CounselorMapper mapper;
    private final AuditService audit;

    CounselorService(CounselorMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public List<Option> list(Viewer admin) {
        admin.requireAdmin();
        return mapper.list();
    }

    @Transactional
    public Option create(Viewer admin, Create input) {
        admin.requireAdmin();
        String name = input.name().strip();
        try {
            mapper.create(name);
        } catch (DuplicateKeyException duplicate) {
            throw BusinessException.conflict(COUNSELOR_EXISTS);
        }
        var saved = mapper.byName(name);
        audit.record(admin.id(), AuditEvent.CREATE_COUNSELOR, AuditEvent.Target.COUNSELOR, saved.id(), null, null);
        return saved;
    }

    boolean exists(String name) {
        return mapper.byName(name) != null;
    }

    List<Option> registeredCounselors(List<String> requested, String legacyName) {
        List<String> names = requested != null ? requested : hasText(legacyName) ? List.of(legacyName) : List.of();
        if (names.isEmpty() || names.size() > MAX_CLASS_COUNSELORS) throw BusinessException.bad(CLASS_COUNSELOR_COUNT);
        var selected = new LinkedHashMap<Long, Option>();
        for (String name : names) {
            var counselor = mapper.byName(name.strip());
            if (counselor == null) throw BusinessException.bad(COUNSELOR_REQUIRED);
            if (selected.putIfAbsent(counselor.id(), counselor) != null) throw BusinessException.bad(CLASS_COUNSELOR_DUPLICATE);
        }
        return List.copyOf(selected.values());
    }

    static String displayNames(List<Option> selected) {
        return selected.stream().map(Option::name).collect(Collectors.joining("、"));
    }

    void assignToClass(long id, List<Option> selected) {
        mapper.removeClassCounselors(id);
        for (int i = 0; i < selected.size(); i++) {
            mapper.addClassCounselor(id, selected.get(i).id(), i + 1);
        }
    }

    Map<Long, List<String>> classCounselors() {
        return mapper.classCounselors().stream().collect(Collectors.groupingBy(CounselorMapper.ClassCounselor::classId,
                Collectors.mapping(CounselorMapper.ClassCounselor::name, Collectors.toList())));
    }

}
