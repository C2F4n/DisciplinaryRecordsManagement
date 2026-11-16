package org.example.disciplinaryrecordsmanagement.model;

import java.time.LocalDate;
import java.util.List;

import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.APPEALED;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.HANDED_BACK;
import static org.example.disciplinaryrecordsmanagement.model.ViolationStatus.RECORDED;

public record RetentionWindow(LocalDate cutoff) {
    public static final List<ViolationStatus> LIMITED_STATUSES = List.of(HANDED_BACK, APPEALED, RECORDED);

    public List<ViolationStatus> statuses() {
        return LIMITED_STATUSES;
    }

    public boolean excludes(LocalDate date, ViolationStatus status) {
        return cutoff != null && !date.isAfter(cutoff) && LIMITED_STATUSES.contains(status);
    }
}
