package org.example.disciplinaryrecordsmanagement.service;

import org.example.disciplinaryrecordsmanagement.dto.AuditDto.Entry;
import org.example.disciplinaryrecordsmanagement.dto.AuditDto.Filter;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.mapper.AuditMapper;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent;
import org.example.disciplinaryrecordsmanagement.model.AuditEvent.Target;
import org.example.disciplinaryrecordsmanagement.model.Viewer;
import org.example.disciplinaryrecordsmanagement.result.PageResult;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

import static org.example.disciplinaryrecordsmanagement.constant.MessageConstants.INVALID_DATE_RANGE;
import static org.example.disciplinaryrecordsmanagement.constant.RequestConstants.REQUEST_ID_KEY;

@Service
public class AuditService {
    private static final LocalDate MIN_AUDIT_DATE = LocalDate.of(1000, 1, 1);
    private static final LocalDate MAX_AUDIT_DATE = LocalDate.of(9999, 12, 31);
    private final AuditMapper mapper;
    private final Clock clock;

    AuditService(AuditMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResult<Entry> list(Viewer admin, Filter filter, int page, int size) {
        admin.requireAdmin();
        LocalDate from = filter.from();
        LocalDate to = filter.to();
        if ((from != null && from.isBefore(MIN_AUDIT_DATE)) || (to != null && to.isAfter(MAX_AUDIT_DATE))
                || (from != null && to != null && from.isAfter(to))
                || (from != null && from.isAfter(MAX_AUDIT_DATE)) || (to != null && to.isBefore(MIN_AUDIT_DATE))) {
            throw BusinessException.bad(INVALID_DATE_RANGE);
        }
        Long start = from == null ? null : millis(from);
        Long until = to == null ? null : millis(to.plusDays(1));
        return new PageResult<>(mapper.list(filter, start, until, size, (page - 1L) * size),
                mapper.count(filter, start, until));
    }

    private long millis(LocalDate day) {
        return day.atStartOfDay(clock.getZone()).toInstant().toEpochMilli();
    }

    void record(Long actor, AuditEvent event, Target target, long id, String previous, String next) {
        record(actor, event, target, id, previous, next, null);
    }

    void record(Long actor, AuditEvent event, Target target, long id, String previous, String next, String details) {
        mapper.append(actor, event, target, id, previous, next, MDC.get(REQUEST_ID_KEY), clock.millis(), details);
    }
}
