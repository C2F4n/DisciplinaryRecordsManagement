package org.example.disciplinaryrecordsmanagement.result;

import java.util.List;

public record PageResult<T>(List<T> items, long total) {}
