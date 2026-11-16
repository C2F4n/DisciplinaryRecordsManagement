package org.example.disciplinaryrecordsmanagement;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.disciplinaryrecordsmanagement.service.MonthlyReportService;
import org.example.disciplinaryrecordsmanagement.service.RetentionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:retention;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DB_USER:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "app.bootstrap.username=test_admin",
        "app.bootstrap.password=TestUser_2026@a",
        "app.report.enabled=false",
        "app.notice.pending-cron=-",
        "app.retention.enabled=true",
        "app.retention.cron=-",
        "logging.file.name=target/test-application.log"
})
@AutoConfigureMockMvc
class RetentionIntegrationTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final LocalDate WEEK_START = LocalDate.of(2008, 2, 25);
    private static final LocalDate WEEK_END = LocalDate.of(2008, 2, 29);
    private static final YearMonth FIRST_REPORT = YearMonth.of(2008, 3);
    private static final String SECRET = "TestUser_2026@a";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary TestClock retentionClock() { return new TestClock(); }
    }

    static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2009-02-27T10:00:00Z");
        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
        void set(String localTime) { now = LocalDateTime.parse(localTime).atZone(ZONE).toInstant(); }
    }

    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired RetentionService retention;
    @Autowired MonthlyReportService reports;
    @Autowired PasswordEncoder passwords;

    @Test
    void cleansOnlyExpiredEligibleRecordsByWeekAndPreservesFrozenReports() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String studentNo = "0000" + Long.parseLong(suffix, 16);
        String className = "保留期测试班-" + suffix;
        jdbc.update("INSERT INTO class_group(name, counselor) VALUES (?, ?)", className, "测试老师");
        long classId = number("SELECT id FROM class_group WHERE name = ?", className);
        jdbc.update("INSERT INTO student(student_no, name, class_id, phone, valid_until) VALUES (?, ?, ?, ?, ?)",
                studentNo, "保留期测试学生", classId, "13800000001", LocalDate.of(2030, 9, 1));
        var data = new Fixture(studentNo, number("SELECT id FROM student WHERE student_no = ?", studentNo), classId,
                className, number("SELECT id FROM app_user WHERE username = ?", "test_admin"));

        long recorded = row(data, WEEK_END, "HANDED_BACK", "2008-03-01T10:00:00", false);
        long appealed = row(data, WEEK_START, "APPEALED", null, false);
        long deleted = row(data, WEEK_START.plusDays(1), "HANDED_BACK", "2008-03-02T10:00:00", true);
        long permanent = row(data, WEEK_START.plusDays(2), "APPROVED", null, false);
        long waiting = row(data, WEEK_START.plusDays(3), "HANDED_BACK", "2009-02-27T15:00:00", false);
        long pending = row(data, WEEK_START.plusDays(3), "PENDING", null, false);
        long future = row(data, WEEK_START.plusWeeks(1), "APPEALED", null, false);
        assertTrue(reports.seal(FIRST_REPORT));
        assertEquals("RECORDED", jdbc.queryForObject("SELECT status FROM violation_record WHERE id = ?", String.class, recorded));
        long archiveId = number("SELECT id FROM monthly_report WHERE month_start = ?", FIRST_REPORT.atDay(1));

        var admin = login("test_admin");
        String viewerName = "retention_reader_" + suffix;
        jdbc.update("INSERT INTO app_user(username,password_hash) VALUES(?,?)", viewerName, passwords.encode(SECRET));
        var ordinary = login(viewerName);
        mvc.perform(get("/api/admin/retention").session(ordinary)).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/retention/history").session(ordinary)).andExpect(status().isForbidden());
        var originalFile = workbook("/api/reports/" + archiveId + "/download", admin);

        mvc.perform(get("/api/admin/retention").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.purgeable").value(0));
        mvc.perform(get("/api/violations/{id}", recorded).session(admin)).andExpect(status().isOk());
        assertEquals(0, retention.purgeWeek(WEEK_START));

        clock.set("2009-02-28T00:00:00");
        mvc.perform(get("/api/admin/retention").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(true))
                .andExpect(jsonPath("$.data.purgeable").value(3))
                .andExpect(jsonPath("$.data.deferred").value(1))
                .andExpect(jsonPath("$.data.weeks.items[0].weekEnd").value("2008-02-29"))
                .andExpect(jsonPath("$.data.weeks.items[0].expiresOn").value("2009-02-28"));
        assertEquals(List.of(WEEK_START), retention.nextWeeks());
        mvc.perform(get("/api/violations/{id}", recorded).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/violations/{id}", waiting).session(admin)).andExpect(status().isNotFound());
        mvc.perform(get("/api/violations/{id}", permanent).session(admin)).andExpect(status().isOk());
        mvc.perform(get("/api/violations").session(admin).param("from", WEEK_START.toString()).param("to", WEEK_END.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        mvc.perform(get("/api/violations/statistics").session(admin).param("from", WEEK_START.toString()).param("to", WEEK_END.toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
        try (var export = new XSSFWorkbook(new ByteArrayInputStream(workbook(
                "/api/violations/export?from=" + WEEK_START + "&to=" + WEEK_END, admin)))) {
            var sheet = export.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("已审核", sheet.getRow(3).getCell(11).getStringCellValue());
        }

        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a = executor.submit(() -> { start.await(); return retention.purgeWeek(WEEK_START); });
            var b = executor.submit(() -> { start.await(); return retention.purgeWeek(WEEK_START); });
            start.countDown();
            assertEquals(3, a.get(20, TimeUnit.SECONDS) + b.get(20, TimeUnit.SECONDS));
        }
        assertEquals(0L, number("SELECT COUNT(*) FROM violation_record WHERE id IN (?, ?, ?)", recorded, appealed, deleted));
        assertEquals(4L, number("SELECT COUNT(*) FROM violation_record WHERE id IN (?, ?, ?, ?)", permanent, waiting, pending, future));
        assertTrue(retention.nextWeeks().isEmpty());
        assertEquals(1L, number("SELECT COUNT(*) FROM audit_log WHERE action = 'PURGE_RETENTION_WEEK'"));
        mvc.perform(get("/api/admin/retention/history").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].removedRecords").value(3));
        assertArrayEquals(originalFile, workbook("/api/reports/" + archiveId + "/download", admin));
        mvc.perform(get("/api/reports/{id}", archiveId).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.items[0].sourceRecordId").value(recorded));

        clock.set("2009-03-01T18:00:00");
        YearMonth secondReport = YearMonth.of(2009, 2);
        assertTrue(reports.seal(secondReport));
        assertEquals(1, retention.purgeWeek(WEEK_START));
        assertEquals(0L, number("SELECT COUNT(*) FROM violation_record WHERE id = ?", waiting));
        long secondArchive = number("SELECT id FROM monthly_report WHERE month_start = ?", secondReport.atDay(1));
        mvc.perform(get("/api/reports/{id}", secondArchive).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.items[0].sourceRecordId").value(waiting));
    }

    private long row(Fixture f, LocalDate occurred, String status, String returned, boolean deleted) {
        String key = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO violation_record(student_id, class_id, student_no, student_name, class_name, counselor,
                  occurred_on, department_code, type_code, reason, disposition, inspection_scope,
                  status, created_by, created_at, submission_id, handed_back_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, f.studentId(), f.classId(), f.number(), "保留期测试学生", f.className(), "测试老师", occurred,
                "ACADEMIC", "CLASS_ATTENDANCE", "测试原因", "测试意见", "COLLEGE", status, f.adminId(), clock.millis(),
                key, returned == null ? null : LocalDateTime.parse(returned).atZone(ZONE).toInstant().toEpochMilli(),
                deleted ? clock.millis() : null);
        return number("SELECT id FROM violation_record WHERE submission_id = ?", key);
    }

    private MockHttpSession login(String username) throws Exception {
        var session = new MockHttpSession();
        var csrf = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn();
        var token = JSON.readTree(csrf.getResponse().getContentAsByteArray()).get("data");
        mvc.perform(post("/api/login").session(session)
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .param("username", username).param("password", SECRET))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(1));
        return session;
    }

    private byte[] workbook(String path, MockHttpSession session) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
    }

    private long number(String sql, Object... values) {
        return jdbc.queryForObject(sql, Long.class, values);
    }

    record Fixture(String number, long studentId, long classId, String className, long adminId) {}
}
