package org.example.disciplinaryrecordsmanagement;

import java.io.ByteArrayInputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.disciplinaryrecordsmanagement.constant.ReportConstants;
import org.example.disciplinaryrecordsmanagement.model.WorkbookColumn;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.MonthlyReportService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:monthly;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DB_USER:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "app.bootstrap.username=test_admin",
        "app.bootstrap.password=TestUser_2026@a",
        "app.report.enabled=false",
        "app.retention.enabled=false",
        "app.notice.pending-cron=-",
        "logging.file.name=target/test-application.log"
})
@AutoConfigureMockMvc
class MonthlyReportIntegrationTests {
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");
    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);
    private static final YearMonth OCTOBER = SEPTEMBER.plusMonths(1);
    private static final String SECRET = "TestUser_2026@a";
    private static final String TEMPLATE_PATH = "/api/admin/export-templates/MONTHLY_REPORT";
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @TestConfiguration
    static class ClockConfig {
        @Bean @Primary TestClock reportClock() { return new TestClock(); }
    }

    static final class TestClock extends Clock {
        private volatile Instant now = Instant.parse("2026-10-01T09:59:00Z");

        @Override public ZoneId getZone() { return ZONE; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
        void set(String localTime) { now = LocalDateTime.parse(localTime).atZone(ZONE).toInstant(); }
    }

    @Autowired MonthlyReportService reports;
    @Autowired TestClock clock;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder passwords;

    @Test
    void monthCutoffFrozenExcelAndRoleBoundariesHoldAcrossConcurrentRuns() throws Exception {
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String studentNo = "000" + Long.parseLong(suffix, 16);
        String className = "月报测试班-" + suffix;
        jdbc.update("INSERT INTO class_group(name, counselor) VALUES (?, ?)", className, "测试老师");
        long classId = number("SELECT id FROM class_group WHERE name = ?", className);
        jdbc.update("INSERT INTO student(student_no, name, class_id, phone, valid_until) VALUES (?, ?, ?, ?, ?)",
                studentNo, "测试学生", classId, "13800000001", LocalDate.of(2029, 9, 1));
        long studentId = number("SELECT id FROM student WHERE student_no = ?", studentNo);
        long adminId = number("SELECT id FROM app_user WHERE username = ?", "test_admin");
        var fixture = new Fixture(studentNo, studentId, classId, className, adminId);
        MockHttpSession disciplineHead = createAccount("discipline_" + suffix, "DEPARTMENT_HEAD", "DISCIPLINE");

        long eligible = record(fixture, "HANDED_BACK", "2026-08-31", "2026-09-30T23:59:59.999", false, "=1+1");
        long next = record(fixture, "HANDED_BACK", "2026-09-22", "2026-10-01T00:00:00", false, "下月入报");
        jdbc.update("UPDATE violation_record SET inspection_label = ? WHERE id = ?", "登记时的检查名称", next);
        long appealed = record(fixture, "APPEALED", "2026-09-24", null, false, "申诉成功");
        long approved = record(fixture, "APPROVED", "2026-09-24", null, false, "尚未交回");
        long pending = record(fixture, "PENDING", "2026-09-30", null, false, "尚未审核");
        long deleted = record(fixture, "HANDED_BACK", "2026-09-25", "2026-09-29T12:00:00", true, "已删除");
        assertEquals(SEPTEMBER.minusMonths(1), reports.latestDueMonth());
        assertFalse(reports.seal(SEPTEMBER));
        assertTrue(reports.seal(SEPTEMBER.minusMonths(1)));
        assertEquals(0L, number("SELECT record_count FROM monthly_report WHERE month_start = ?", SEPTEMBER.minusMonths(1).atDay(1)));

        clock.set("2026-10-01T18:00:00");
        assertEquals(SEPTEMBER, reports.nextDueMonth());
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> { start.await(); return reports.seal(SEPTEMBER); });
            var second = executor.submit(() -> { start.await(); return reports.seal(SEPTEMBER); });
            start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertNull(reports.nextDueMonth());
        assertEquals("RECORDED", statusOf(eligible));
        assertEquals("HANDED_BACK", statusOf(next));
        assertEquals("APPEALED", statusOf(appealed));
        assertEquals("APPROVED", statusOf(approved));
        assertEquals("PENDING", statusOf(pending));
        assertEquals("HANDED_BACK", statusOf(deleted));

        long reportId = number("SELECT id FROM monthly_report WHERE month_start = ?", SEPTEMBER.atDay(1));
        assertEquals(1L, number("SELECT COUNT(*) FROM audit_log WHERE action = 'GENERATE_MONTHLY_REPORT' AND target_id = ?", reportId));
        MockHttpSession quality = createAccount("quality_" + suffix, "QUALITY_HEAD", "");
        MockHttpSession ordinary = createAccount("ordinary_" + suffix, null, "");

        mvc.perform(get("/api/notices").session(disciplineHead)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].target").value("MONTHLY_REPORT"))
                .andExpect(jsonPath("$.data.items[0].subjectId").value(reportId))
                .andExpect(jsonPath("$.data.items[0].message").value("月报生成时有 1 条尚未审核的记录没有入报。"));
        mvc.perform(get("/api/notices").session(quality)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));

        mvc.perform(get("/api/reports").session(ordinary)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports/{id}/download", reportId).session(ordinary)).andExpect(status().isForbidden());
        mvc.perform(get("/api/reports").session(quality)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(reportId));
        mvc.perform(get("/api/violations/{id}", eligible).session(quality)).andExpect(status().isNotFound());

        var firstDownload = download(quality, reportId);
        String disposition = firstDownload.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION);
        assertNotNull(disposition);
        assertEquals("2026-09 违纪统计月报.xlsx", ContentDisposition.parse(disposition).getFilename());
        byte[] frozen = firstDownload.getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(frozen))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum());
            assertEquals("学号", sheet.getRow(2).getCell(3).getStringCellValue());
            var firstRow = sheet.getRow(3);
            assertEquals(LocalDate.of(2026, 8, 31), firstRow.getCell(0).getLocalDateTimeCellValue().toLocalDate());
            assertEquals(studentNo, firstRow.getCell(3).getStringCellValue());
            assertEquals(CellType.STRING, firstRow.getCell(5).getCellType());
            assertEquals("=1+1", firstRow.getCell(5).getStringCellValue());
            assertEquals("学院检查", firstRow.getCell(7).getStringCellValue());
        }

        var admin = login("test_admin");
        mvc.perform(get("/api/violations/{id}", eligible).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.correctable").value(false));
        putJson(admin, "/api/admin/violations/" + eligible + "/correction", Map.of(
                "version", number("SELECT version FROM violation_record WHERE id = ?", eligible),
                "studentNo", studentNo, "occurredOn", "2026-09-01", "typeCode", "CLASS_ATTENDANCE",
                "status", "APPROVED", "correctionReason", "已归档的记录不能重新流转"), 409);

        jdbc.update("UPDATE violation_record SET reason = ? WHERE id = ?", "之后改过的原因", eligible);
        jdbc.update("DELETE FROM violation_record WHERE id = ?", eligible);
        assertFalse(reports.seal(SEPTEMBER));
        mvc.perform(get("/api/reports/{id}", reportId).session(quality)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.total").value(1))
                .andExpect(jsonPath("$.data.entries.items[0].reason").value("=1+1"));
        assertArrayEquals(frozen, download(quality, reportId).getResponse().getContentAsByteArray());
        assertEquals(2L, number("SELECT COUNT(*) FROM audit_log WHERE action = 'DOWNLOAD_MONTHLY_REPORT' AND target_id = ?", reportId));

        mvc.perform(get("/api/admin/export-templates").session(quality)).andExpect(status().isForbidden());
        var defaults = Arrays.stream(WorkbookColumn.values()).filter(column -> column != WorkbookColumn.STATUS).toList();
        var columns = new ArrayList<>(defaults);
        columns.remove(WorkbookColumn.REMARKS);
        columns.remove(WorkbookColumn.NUMBER);
        columns.addFirst(WorkbookColumn.NUMBER);
        var templateChange = Map.of("title", "学院违纪月报", "columns", columns, "version", 0);
        updateTemplate(quality, templateChange, 403);
        updateTemplate(admin, Map.of("title", "无效模板", "columns", List.of(WorkbookColumn.NUMBER), "version", 0), 400);
        updateTemplate(admin, templateChange, 200);
        updateTemplate(admin, templateChange, 409);

        long late = record(fixture, "HANDED_BACK", "2026-08-30", "2026-09-30T21:00:00", false, "上期遗漏");
        clock.set("2026-11-01T18:00:00");
        assertEquals(OCTOBER, reports.nextDueMonth());
        assertTrue(reports.seal(OCTOBER));
        assertEquals("RECORDED", statusOf(next));
        assertEquals("RECORDED", statusOf(late));
        long october = number("SELECT id FROM monthly_report WHERE month_start = ?", OCTOBER.atDay(1));
        mvc.perform(get("/api/reports/{id}", october).param("size", "1").session(quality)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.entries.total").value(2))
                .andExpect(jsonPath("$.data.entries.items[0].sourceRecordId").value(late));
        var newer = download(quality, october).getResponse().getContentAsByteArray();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(newer))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals("2026-10 学院违纪月报", sheet.getRow(0).getCell(0).getStringCellValue());
            assertEquals(columns.size(), sheet.getRow(2).getLastCellNum());
            assertEquals("学号", sheet.getRow(2).getCell(0).getStringCellValue());
            assertEquals(studentNo, sheet.getRow(3).getCell(0).getStringCellValue());
            assertEquals("登记时的检查名称",
                    sheet.getRow(4).getCell(columns.indexOf(WorkbookColumn.INSPECTION)).getStringCellValue());
        }
        assertArrayEquals(frozen, download(quality, reportId).getResponse().getContentAsByteArray());
        updateTemplate(admin, Map.of("title", ReportConstants.MONTHLY_TITLE, "columns", defaults, "version", 1), 200);
    }

    private long record(Fixture f, String status, String date, String returned, boolean deleted, String reason) {
        String submission = UUID.randomUUID().toString();
        jdbc.update("""
                INSERT INTO violation_record(student_id, class_id, student_no, student_name, class_name, counselor,
                  occurred_on, department_code, type_code, reason, disposition, inspection_scope,
                  status, created_by, created_at, submission_id, handed_back_at, deleted_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, f.studentId(), f.classId(), f.studentNo(), "测试学生", f.className(), "测试老师", LocalDate.parse(date),
                "ACADEMIC", "CLASS_ATTENDANCE", reason, "测试意见", "COLLEGE", status, f.adminId(), clock.millis(),
                submission, returned == null ? null : LocalDateTime.parse(returned).atZone(ZONE).toInstant().toEpochMilli(),
                deleted ? clock.millis() : null);
        return number("SELECT id FROM violation_record WHERE submission_id = ?", submission);
    }

    private String statusOf(long id) {
        return jdbc.queryForObject("SELECT status FROM violation_record WHERE id = ?", String.class, id);
    }

    private long number(String query, Object... arguments) {
        return jdbc.queryForObject(query, Long.class, arguments);
    }

    private MockHttpSession createAccount(String name, String role, String scope) throws Exception {
        jdbc.update("INSERT INTO app_user(username, password_hash) VALUES (?, ?)", name, passwords.encode(SECRET));
        if (role != null) jdbc.update("INSERT INTO user_grant(user_id, role_code, scope_key) VALUES (?, ?, ?)",
                number("SELECT id FROM app_user WHERE username = ?", name), role, scope);

        return login(name);
    }

    private MockHttpSession login(String name) throws Exception {
        var session = new MockHttpSession();
        var csrf = csrf(session);
        mvc.perform(post("/api/login").session(session)
                        .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                        .param("username", name).param("password", SECRET))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(Result.SUCCESS));
        return session;
    }

    private JsonNode csrf(MockHttpSession session) throws Exception {
        return read(mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn()).get("data");
    }

    private void updateTemplate(MockHttpSession session, Object change, int expected) throws Exception {
        putJson(session, TEMPLATE_PATH, change, expected);
    }

    private void putJson(MockHttpSession session, String path, Object change, int expected) throws Exception {
        var csrf = csrf(session);
        mvc.perform(put(path).session(session)
                        .header(csrf.get("headerName").asString(), csrf.get("token").asString())
                        .contentType("application/json").content(JSON.writeValueAsBytes(change)))
                .andExpect(status().is(expected));
    }

    private MvcResult download(MockHttpSession session, long reportId) throws Exception {
        return mvc.perform(get("/api/reports/{id}/download", reportId).session(session))
                .andExpect(status().isOk()).andExpect(content().contentType(ReportConstants.XLSX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
    }

    private JsonNode read(MvcResult result) throws Exception {
        return JSON.readTree(result.getResponse().getContentAsByteArray());
    }

    record Fixture(String studentNo, long studentId, long classId, String className, long adminId) {}
}
