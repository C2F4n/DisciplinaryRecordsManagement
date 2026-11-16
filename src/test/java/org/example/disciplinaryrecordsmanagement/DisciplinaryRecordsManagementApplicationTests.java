package org.example.disciplinaryrecordsmanagement;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.example.disciplinaryrecordsmanagement.config.BusinessPolicy;
import org.example.disciplinaryrecordsmanagement.constant.ReportConstants;
import org.example.disciplinaryrecordsmanagement.constant.RosterConstants;
import org.example.disciplinaryrecordsmanagement.exception.BusinessException;
import org.example.disciplinaryrecordsmanagement.exception.ErrorCode;
import org.example.disciplinaryrecordsmanagement.model.WorkbookColumn;
import org.example.disciplinaryrecordsmanagement.result.Result;
import org.example.disciplinaryrecordsmanagement.service.NotificationService;
import org.example.disciplinaryrecordsmanagement.service.RegistrationMailSender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:disciplinary;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1}",
        "spring.datasource.username=${TEST_DB_USER:sa}",
        "spring.datasource.password=${TEST_DB_PASSWORD:}",
        "app.bootstrap.username=test_admin",
        "app.bootstrap.password=TestUser_2026@a",
        "app.policy.default-disposition=测试配置处理意见",
        "app.policy.login-attempts-per-ip=32",
        "app.report.enabled=false",
        "app.retention.enabled=false",
        "app.notice.pending-cron=-",
        "app.notice.email.key-file=target/smtp-config-integration.key",
        "app.export.max-rows=1",
        "logging.file.name=target/test-application.log"
})
@AutoConfigureMockMvc
class DisciplinaryRecordsManagementApplicationTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String SECRET = "TestUser_2026@a";
    private static final String STUDENT_EMAIL = "student@example.test";

    @TestConfiguration
    static class DateConfiguration {
        @Bean @Primary Clock fixedBusinessDate(BusinessPolicy policy) {
            return Clock.fixed(Instant.parse("2026-11-11T03:00:00Z"), ZoneId.of(policy.timeZone()));
        }

        @Bean FailureProbe failureProbe() { return new FailureProbe(); }
    }

    @TestComponent
    @RestController
    static class FailureProbe {
        @GetMapping("/api/test-failure")
        void fail() { throw new IllegalStateException("internal-only-marker"); }
    }

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired BusinessPolicy policy;
    @Autowired NotificationService notifications;
    @MockitoBean RegistrationMailSender registrationMail;

    @BeforeEach
    void seedCounselors() {
        jdbc.update("INSERT INTO counselor(name) SELECT ? WHERE NOT EXISTS (SELECT 1 FROM counselor WHERE name = ?)",
                "测试辅导员", "测试辅导员");
    }

    @Test
    void permissionGuideCountsAccountsByIdentityAndReportsConfiguredLimits() throws Exception {
        String path = "/api/information/permissions";
        mvc.perform(get(path)).andExpect(status().isUnauthorized());
        var admin = login("test_admin", "192.0.2.112");
        var before = read(mvc.perform(get(path).session(admin)).andExpect(status().isOk()).andReturn());
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String username = "guide_" + suffix;
        long userId = postJson(admin, "/api/admin/accounts", Map.of("username", username, "password", SECRET), 200)
                .get("id").asLong();
        var ordinary = login(username, "192.0.2.112");
        var unassigned = read(mvc.perform(get(path).session(ordinary)).andExpect(status().isOk()).andReturn());
        assertEquals(roleCount(before, "UNASSIGNED") + 1, roleCount(unassigned, "UNASSIGNED"));
        grant(admin, userId, "OFFICER", "ACADEMIC");
        grant(admin, userId, "OFFICER", "DORM");
        grant(admin, userId, "DEPARTMENT_HEAD", "DISCIPLINE");
        grant(admin, userId, "DEPARTMENT_HEAD", "ACADEMIC");
        putJson(admin, "/api/admin/accounts/" + userId + "/enabled", Map.of("enabled", false), 200);

        var result = read(mvc.perform(get(path).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.identities.length()").value(11))
                .andExpect(jsonPath("$.data.identities[?(@.code == 'CORE_ADMIN')].maximum").value(org.hamcrest.Matchers.hasItem(1)))
                .andReturn());
        assertEquals(policy.editDays(), result.get("editDays").asInt());
        assertEquals(1, roleCount(result, "CORE_ADMIN"));
        assertEquals(roleCount(before, "UNASSIGNED"), roleCount(result, "UNASSIGNED"));
        assertEquals(roleCount(before, "OFFICER") + 1, roleCount(result, "OFFICER"));
        assertEquals(roleCount(before, "DISCIPLINE_HEAD") + 1, roleCount(result, "DISCIPLINE_HEAD"));
        assertEquals(roleCount(before, "DEPARTMENT_HEAD") + 1, roleCount(result, "DEPARTMENT_HEAD"));
    }

    private static long roleCount(JsonNode guide, String role) {
        for (JsonNode identity : guide.get("identities")) {
            if (role.equals(identity.get("code").asString())) return identity.get("accountCount").asLong();
        }
        throw new AssertionError("Identity missing: " + role);
    }

    @Test
    void registrationScopesAndConcurrentReviewWorkThroughRealEndpoints() throws Exception {
        mvc.perform(get("/api/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(ErrorCode.UNAUTHENTICATED.code()));
        mvc.perform(post("/api/login").param("username", "test_admin").param("password", SECRET))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID_CSRF.code()));

        MockHttpSession admin = login("test_admin");
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String numbers = studentNumberPrefix(suffix);
        String noA = numbers + "1";
        String noB = numbers + "2";
        String noC = numbers + "3";
        long classA = addClass(admin, "机电一班-" + suffix);
        long classB = addClass(admin, "机电二班-" + suffix);
        MockHttpSession student = bind(admin, noA, "学生甲", classA, "13800000001", true);
        bind(admin, noB, "学生乙", classB, "13800000002", false);
        bind(admin, noC, "学生丙", classA, "13800000003", false);
        long studentAccount = jdbc.queryForObject("SELECT id FROM app_user WHERE username = ?", Long.class, noA);
        grant(admin, studentAccount, "QUALITY_HEAD", "");

        String operatorName = "officer_" + suffix;
        String headName = "head_" + suffix;
        String officeName = "office_" + suffix;
        MockHttpSession officer = staff(admin, operatorName, "OFFICER", "ACADEMIC");
        MockHttpSession head = staff(admin, headName, "DEPARTMENT_HEAD", "ACADEMIC");
        MockHttpSession office = staff(admin, officeName, "DISCIPLINE_OFFICE", "");
        MockHttpSession reportOnly = staff(admin, "quality_" + suffix, "QUALITY_HEAD", "");
        assertTrue(read(mvc.perform(get("/api/me").session(student)).andExpect(status().isOk()).andReturn())
                .get("canViewRecords").asBoolean());

        Map<String, Object> ownBody = draft(noA, "CLASS_ATTENDANCE", "2026-11-11");
        JsonNode own = postJson(officer, "/api/violations", ownBody, 200);
        assertEquals("测试配置处理意见", own.get("disposition").asString());
        mvc.perform(get("/api/metadata").session(officer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.defaultDisposition").value("测试配置处理意见"));
        mvc.perform(get("/api/students/lookup").param("q", noA).session(officer)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].studentNo").value(noA));
        postJson(officer, "/api/violations", ownBody, 409);
        JsonNode other = postJson(admin, "/api/violations", draft(noB, "DORM_HYGIENE", "2026-11-11"), 200);
        JsonNode classmate = postJson(officer, "/api/violations", draft(noC, "CLASS_ATTENDANCE", "2026-11-10"), 200);
        postJson(officer, "/api/violations", draft(noA, "DORM_HYGIENE", "2026-11-11"), 403);
        postJson(officer, "/api/violations", draft(noA, "CLASS_ATTENDANCE", "2026-11-09"), 400);

        long id = own.get("id").asLong();
        long outsideId = other.get("id").asLong();
        long classmateId = classmate.get("id").asLong();
        var inbox = read(mvc.perform(get("/api/notices").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].subjectId").value(id)).andReturn());
        long notificationId = inbox.get("items").get(0).get("id").asLong();
        putRequest(head, "/api/notices/" + notificationId + "/read", 404);
        putRequest(student, "/api/notices/" + notificationId + "/read", 200);
        putRequest(student, "/api/notices/" + notificationId + "/read", 200);
        mvc.perform(get("/api/notices/unread").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));

        assertEquals(0, notifications.remindPending());
        jdbc.update("UPDATE violation_record SET created_at = ? WHERE id = ?",
                Instant.parse("2026-11-10T03:00:00Z").toEpochMilli(), classmateId);
        assertEquals(1, notifications.remindPending());
        assertEquals(0, notifications.remindPending());
        mvc.perform(get("/api/notices").session(head))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].subjectId").value(classmateId));
        assertEquals(classmateId, postJson(head, "/api/violations/review",
                Map.of("action", "REJECT", "reason", "请核对日期", "items", List.of(Map.of("id", classmateId, "version", 0))), 200)
                .get("changed").get(0).asLong());
        mvc.perform(get("/api/notices").session(officer))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].subjectId").value(classmateId))
                .andExpect(jsonPath("$.data.items[0].title").value("登记已被驳回"));
        mvc.perform(get("/api/violations/{id}", id).session(student)).andExpect(status().isOk());
        mvc.perform(get("/api/violations/{id}", classmateId).session(student)).andExpect(status().isNotFound());
        grant(admin, studentAccount, "CLASS_COMMITTEE", String.valueOf(classA));
        mvc.perform(get("/api/violations/{id}", classmateId).session(student)).andExpect(status().isOk());
        mvc.perform(get("/api/violations/{id}", outsideId).session(student)).andExpect(status().isNotFound());
        mvc.perform(get("/api/violations/{id}", outsideId).session(head)).andExpect(status().isNotFound());
        mvc.perform(get("/api/students/lookup").param("q", noB).session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/violations/{id}", id).session(reportOnly)).andExpect(status().isNotFound());
        assertEquals(0, read(mvc.perform(get("/api/violations").session(reportOnly))
                .andExpect(status().isOk()).andReturn()).get("total").asInt());
        assertEquals(2, read(mvc.perform(get("/api/violations").session(student))
                .andExpect(status().isOk()).andReturn()).get("total").asInt());
        postJson(student, "/api/violations/review", review(id, 0), 403);
        assertEquals(outsideId, postJson(head, "/api/violations/review", review(outsideId, 0), 200)
                .get("skipped").get(0).asLong());

        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var first = executor.submit(() -> race(ready, start, head, id));
            var second = executor.submit(() -> race(ready, start, admin, id));
            assertTrue(ready.await(5, TimeUnit.SECONDS));
            start.countDown();
            assertEquals(1, first.get(15, TimeUnit.SECONDS) + second.get(15, TimeUnit.SECONDS));
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'APPROVE' AND target_id = ?",
                Integer.class, id));
        assertEquals("APPROVED", jdbc.queryForObject("SELECT status FROM violation_record WHERE id = ?", String.class, id));

        postJson(office, "/api/violations/" + id + "/resolve", Map.of("version", 1, "resolution", "HANDED_BACK"), 200);
        assertEquals("HANDED_BACK", read(mvc.perform(get("/api/violations/{id}", id).session(student))
                .andExpect(status().isOk()).andReturn()).get("status").asString());
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'CREATE_RECORD' AND target_id = ?",
                Integer.class, id));
        mvc.perform(get("/api/notices/unread").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(2));
        putRequest(student, "/api/notices/read-all", 200);
        mvc.perform(get("/api/notices/unread").session(student))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.count").value(0));

        mvc.perform(get("/api/violations/export").session(reportOnly)).andExpect(status().isForbidden());
        mvc.perform(get("/api/violations/export").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/violations/export").param("status", "PENDING").session(admin))
                .andExpect(status().isBadRequest());
        var excel = mvc.perform(get("/api/violations/export").session(admin)
                        .param("from", "2026-11-10").param("to", "2026-11-11").param("classId", Long.toString(classA))
                        .param("department", "ACADEMIC").param("type", "CLASS_ATTENDANCE"))
                .andExpect(status().isOk()).andExpect(content().contentType(ReportConstants.XLSX_MEDIA_TYPE))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store")).andReturn();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(excel.getResponse().getContentAsByteArray()))) {
            var sheet = workbook.getSheetAt(0);
            assertEquals(3, sheet.getLastRowNum());
            assertEquals(noA, sheet.getRow(3).getCell(3).getStringCellValue());
            assertEquals("已交回", sheet.getRow(3).getCell(11).getStringCellValue());
        }
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'DOWNLOAD_FILTERED_VIOLATIONS'",
                Integer.class));
        String template = "/api/admin/export-templates/FILTERED_VIOLATIONS";
        var defaults = List.of(WorkbookColumn.values());
        var columns = new ArrayList<>(defaults);
        columns.remove(WorkbookColumn.STATUS);
        putJson(admin, template, Map.of("title", "缺少状态的模板", "columns", columns, "version", 0), 400);
        columns.addFirst(WorkbookColumn.STATUS);
        putJson(admin, template, Map.of("title", "正式记录测试导出", "columns", columns, "version", 0), 200);
        try {
            var updatedExport = mvc.perform(get("/api/violations/export").session(admin)
                            .param("from", "2026-11-10").param("to", "2026-11-11").param("classId", Long.toString(classA))
                            .param("department", "ACADEMIC").param("type", "CLASS_ATTENDANCE"))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(updatedExport))) {
                var sheet = workbook.getSheetAt(0);
                assertEquals("2026-11-11 正式记录测试导出", sheet.getRow(0).getCell(0).getStringCellValue());
                assertEquals("状态", sheet.getRow(2).getCell(0).getStringCellValue());
                assertEquals("已交回", sheet.getRow(3).getCell(0).getStringCellValue());
            }
        } finally {
            putJson(admin, template, Map.of("title", ReportConstants.FILTERED_TITLE, "columns", defaults, "version", 1), 200);
        }
        postJson(admin, "/api/violations/review", review(outsideId, 0), 200);
        mvc.perform(get("/api/violations/export").session(admin).param("from", "2026-11-11").param("to", "2026-11-11"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/violations/statistics").session(reportOnly)).andExpect(status().isForbidden());
        mvc.perform(get("/api/violations/statistics").session(student)).andExpect(status().isForbidden());
        mvc.perform(get("/api/violations/statistics").session(admin).param("status", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get("/api/violations/statistics").session(head).param("from", "2026-11-09")
                        .param("to", "2026-11-12").param("period", "WEEK"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.byDepartment[0].label").value("学风建设部"))
                .andExpect(jsonPath("$.data.trend[0].start").value("2026-11-09"))
                .andExpect(jsonPath("$.data.trend[0].count").value(1));
        mvc.perform(get("/api/violations/statistics").session(admin).param("from", "2026-11-09")
                        .param("to", "2026-11-12").param("period", "DAY").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.trend[0].count").value(0))
                .andExpect(jsonPath("$.data.trend[2].start").value("2026-11-11"))
                .andExpect(jsonPath("$.data.trend[2].count").value(2));
    }

    @Test
    void smtpSettingsRequireCoreAdminAndSessionUnlockAndProtectTheAuthorizationCode() throws Exception {
        String path = "/api/admin/smtp-settings";
        String gate = "a018779";
        String newGate = "Config_2026";
        String smtpPassword = "FixtureMailSecret";
        String source = "192.0.2.50";
        var admin = login("test_admin", source);
        var secondSession = login("test_admin", source);
        var user = permissionReviewer(admin, "smtp_noncore", "TEACHER", "", source);
        mvc.perform(get(path).session(admin)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.SMTP_LOCKED.code()));
        postJson(user, path + "/unlock", Map.of("password", gate), 403, source);
        postJson(admin, path + "/unlock", Map.of("password", "wrong-pass"), 400, source);
        var first = postJson(admin, path + "/unlock", Map.of("password", gate), 200, source);
        String token = first.get("token").asString();
        String secondToken = postJson(secondSession, path + "/unlock", Map.of("password", gate), 200, source)
                .get("token").asString();
        mvc.perform(get(path).session(secondSession).header("X-SMTP-Unlock", token)).andExpect(status().isForbidden());
        mvc.perform(get(path).session(user).header("X-SMTP-Unlock", token)).andExpect(status().isForbidden());
        mvc.perform(get(path).session(admin).header("X-SMTP-Unlock", token)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"));
        var change = new HashMap<String, Object>(Map.of("enabled", true, "host", "smtp.example.test", "port", 587,
                "username", "sender@example.test", "senderAddress", "sender@example.test", "authentication", true,
                "transport", "STARTTLS", "password", smtpPassword, "version", first.get("settings").get("version").asLong()));
        String originalHash = jdbc.queryForObject("SELECT gate_hash FROM smtp_settings WHERE id = 1", String.class);
        try {
            putSmtp(admin, path, "", change, 403);
            var saved = putSmtp(admin, path, token, change, 200);
            assertEquals("smtp.example.test", saved.get("host").asString());
            assertTrue(saved.get("restartRequired").asBoolean());
            assertTrue(saved.get("passwordConfigured").asBoolean());
            assertFalse(saved.has("password"));
            assertFalse(saved.has("encryptedPassword"));
            String ciphertext = jdbc.queryForObject("SELECT encrypted_password FROM smtp_settings WHERE id = 1", String.class);
            assertNotNull(ciphertext);
            assertNotEquals(smtpPassword, ciphertext);
            putSmtp(admin, path, token, change, 409);
            change.put("version", saved.get("version").asLong());
            change.put("password", "");
            putSmtp(admin, path, token, change, 200);
            assertEquals(ciphertext, jdbc.queryForObject("SELECT encrypted_password FROM smtp_settings WHERE id = 1", String.class));

            putSmtp(admin, path + "/password", token, Map.of("currentPassword", gate, "newPassword", newGate), 200);
            mvc.perform(get(path).session(secondSession).header("X-SMTP-Unlock", secondToken)).andExpect(status().isForbidden());
            postJson(admin, path + "/unlock", Map.of("password", gate), 400, source);
            var reopened = postJson(admin, path + "/unlock", Map.of("password", newGate), 200, source);
            assertEquals("smtp.example.test", reopened.get("settings").get("host").asString());
        } finally {
            jdbc.update("""
                    UPDATE smtp_settings SET gate_hash = ?, gate_version = 0, config_version = 0, enabled = FALSE,
                        host = '', port = 587, username = '', sender_address = '', authentication = TRUE,
                        transport = 'STARTTLS', encrypted_password = NULL WHERE id = 1
                    """, originalHash);
        }
    }

    private JsonNode putSmtp(MockHttpSession session, String path, String access, Object input, int expected) throws Exception {
        var token = csrf(session);
        return read(mvc.perform(put(path).session(session)
                        .header(token.get("headerName").asString(), token.get("token").asString()).header("X-SMTP-Unlock", access)
                        .contentType("application/json").content(JSON.writeValueAsBytes(input)))
                .andExpect(status().is(expected)).andReturn());
    }

    @Test
    void registrationRequiresEmailAndStudentsCanUpdateTheirOwnAddress() throws Exception {
        String source = "192.0.2.49";
        var admin = login("test_admin", source);
        long classId = addClass(admin, "邮箱测试班");
        String number = "202507101493";
        String name = "邮箱测试学生";
        postJson(admin, "/api/admin/students", Map.of("studentNo", number, "name", name,
                "classId", classId, "enrolledOn", "2025-09-01"), 200);

        var form = new HashMap<String, Object>(Map.of("studentNo", number, "name", name, "classId", classId,
                "password", SECRET));
        postJson(new MockHttpSession(), "/api/bind", form, 400, source);
        form.put("email", "invalid");
        postJson(new MockHttpSession(), "/api/bind", form, 400, source);
        form.put("email", STUDENT_EMAIL);
        var registration = new MockHttpSession();
        postJson(registration, "/api/bind", form, 400, source);
        var mismatched = new HashMap<>(form);
        mismatched.put("name", "名册不匹配");
        postJson(registration, "/api/bind/email-code", mismatched, 400, source);
        verify(registrationMail, never()).send(anyString(), anyString(), anyLong());

        String firstCode = requestEmailCode(registration, form, source);
        postJson(registration, "/api/bind/email-code", form, 429, source);
        form.put("emailCode", firstCode);
        form.put("email", "changed@example.test");
        postJson(registration, "/api/bind", form, 400, source);
        form.put("email", STUDENT_EMAIL);
        jdbc.update("UPDATE registration_email_code SET issued_at = 0, expires_at = 0 WHERE email = ?", STUDENT_EMAIL);
        postJson(registration, "/api/bind", form, 400, source);

        doThrow(BusinessException.registrationEmailUnavailable()).doNothing()
                .when(registrationMail).send(eq(STUDENT_EMAIL), anyString(), eq(10L));
        postJson(registration, "/api/bind/email-code", form, 503, source);
        var rejected = new HashMap<>(form);
        var nextCode = requestEmailCode(registration, form, source);
        rejected.put("emailCode", nextCode.equals("000000") ? "000001" : "000000");
        for (int i = 0; i < 5; i++) postJson(registration, "/api/bind", rejected, 400, source);
        form.put("emailCode", nextCode);
        postJson(registration, "/api/bind", form, 400, source);
        jdbc.update("UPDATE registration_email_code SET issued_at = 0 WHERE email = ?", STUDENT_EMAIL);
        registerWithEmail(registration, form, source);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM registration_email_code WHERE email = ?",
                Integer.class, STUDENT_EMAIL));

        var student = login(number, source);
        String path = "/api/notification-email";
        mvc.perform(get(path).session(student)).andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, "no-store"))
                .andExpect(jsonPath("$.data.email").value(STUDENT_EMAIL));
        putJson(student, path, Map.of("email", "one@example.test,two@example.test"), 400);
        putJson(student, path, Map.of("email", "updated@example.test"), 200);
        mvc.perform(get(path).session(student)).andExpect(jsonPath("$.data.email").value("updated@example.test"));
        mvc.perform(get(path).session(admin)).andExpect(status().isForbidden());
    }

    @Test
    void permissionRequestsAreScopedAndKeepTheDecisionAndApprover() throws Exception {
        String source = "192.0.2.48";
        var admin = login("test_admin", source);
        long classId = addClass(admin, "权限申请测试班");
        var student = bindCorrectionStudent(admin, "202507101491", "权限申请学生", classId, source);
        var other = bindCorrectionStudent(admin, "202507101492", "同班申请测试", classId, source);
        var head = permissionReviewer(admin, "permission_head_a", "DEPARTMENT_HEAD", "INSPECTION", source);
        var otherHead = permissionReviewer(admin, "permission_head_b", "DEPARTMENT_HEAD", "DISCIPLINE", source);
        var teacher = permissionReviewer(admin, "permission_teacher", "TEACHER", "", source);
        String endpoint = "/api/permission-requests";
        mvc.perform(get("/api/me").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.student").value(true))
                .andExpect(jsonPath("$.data.grants.length()").value(0));
        mvc.perform(get(endpoint + "/queue").session(student)).andExpect(status().isForbidden());
        postJson(student, endpoint, Map.of("role", "TEACHER", "reason", "申请老师身份"), 400);
        postJson(student, endpoint, Map.of("role", "OFFICER", "scope", "INVALID", "reason", "申请干事"), 400);

        var officer = Map.of("role", "OFFICER", "scope", "INSPECTION", "reason", "已任自律督察部干事");
        postJson(student, endpoint, officer, 200);
        postJson(student, endpoint, officer, 409);
        var first = read(mvc.perform(get(endpoint + "/mine").session(student)).andExpect(status().isOk()).andReturn())
                .get("items").get(0);
        long officerId = first.get("id").asLong();
        mvc.perform(get(endpoint + "/mine").session(other)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(endpoint + "/queue").session(otherHead)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + officerId + ")]").isEmpty());
        mvc.perform(get(endpoint + "/queue").session(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + officerId + ")]").isNotEmpty());
        mvc.perform(get("/api/notices").session(head)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.target == 'PERMISSION_REVIEW')]").isNotEmpty());
        String decide = endpoint + "/" + officerId + "/decision";
        putJson(otherHead, decide, Map.of("approve", true), 403);

        try (var workers = Executors.newFixedThreadPool(2)) {
            var race = List.of(
                    workers.submit(() -> permissionDecision(head, decide, true, "确认任职")),
                    workers.submit(() -> permissionDecision(teacher, decide, true, "确认任职")));
            var statuses = new ArrayList<Integer>();
            for (var item : race) statuses.add(item.get(10, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), statuses.stream().sorted().toList());
        }

        mvc.perform(get("/api/me").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.officerDepartments[0]").value("INSPECTION"));
        var approved = read(mvc.perform(get(endpoint + "/mine").session(student)).andExpect(status().isOk()).andReturn())
                .get("items").get(0);
        assertEquals("APPROVED", approved.get("status").asString());
        assertTrue(approved.get("decidedAt").asLong() >= approved.get("submittedAt").asLong());
        assertTrue(approved.get("decidedBy").asLong() > 0);
        assertTrue(List.of("permission_head_a", "permission_teacher").contains(approved.get("reviewerName").asString()));
        assertEquals("确认任职", approved.get("decisionNote").asString());
        mvc.perform(get("/api/notices").session(student)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.target == 'PERMISSION_REQUEST')]").isNotEmpty());
        postJson(student, endpoint, officer, 409);

        postJson(student, endpoint, Map.of("role", "DEPARTMENT_HEAD", "scope", "ACADEMIC", "reason", "部长申请"), 200);
        long higher = jdbc.queryForObject("SELECT id FROM permission_request WHERE requester_id = ? AND role_code = ?",
                Long.class, first.get("requesterId").asLong(), "DEPARTMENT_HEAD");
        putJson(teacher, endpoint + "/" + higher + "/decision", Map.of("approve", true), 403);
        putJson(admin, endpoint + "/" + higher + "/decision", Map.of("approve", false, "note", "名单待核实"), 200);
        mvc.perform(get(endpoint + "/queue").session(admin).param("status", "REJECTED")).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[?(@.id == " + higher + ")].decisionNote").value("名单待核实"));
        postJson(student, endpoint, Map.of("role", "DEPARTMENT_HEAD", "scope", "ACADEMIC", "reason", "补充后重新申请"), 200);

        postJson(student, endpoint, Map.of("role", "CLASS_COMMITTEE", "scope", "99999", "reason", "已任班委"), 200);
        var committee = read(mvc.perform(get(endpoint + "/mine").session(student)).andExpect(status().isOk()).andReturn())
                .get("items").get(0);
        assertEquals(Long.toString(classId), committee.get("scopeKey").asString());
        postJson(student, endpoint, Map.of("role", "OFFICER", "scope", "ACADEMIC", "reason", "验证本人不可审批"), 200);
        grant(admin, first.get("requesterId").asLong(), "DEPARTMENT_HEAD", "ACADEMIC");
        var self = read(mvc.perform(get(endpoint + "/mine").session(student)).andExpect(status().isOk()).andReturn())
                .get("items").get(0);
        putJson(student, endpoint + "/" + self.get("id").asLong() + "/decision", Map.of("approve", true), 403);
        putJson(admin, endpoint + "/" + self.get("id").asLong() + "/decision", Map.of("approve", false), 200);
        jdbc.update("DELETE FROM user_grant WHERE user_id = ? AND role_code = 'DEPARTMENT_HEAD' AND scope_key = 'ACADEMIC'",
                first.get("requesterId").asLong());
        var profile = read(mvc.perform(get("/api/admin/roster/students").session(admin).param("query", "202507101491"))
                .andExpect(status().isOk()).andReturn()).get("items").get(0);
        putJson(admin, "/api/admin/roster/students/" + profile.get("id").asLong(),
                Map.of("classId", addClass(admin, "权限申请转入班"), "name", "权限申请学生", "phone", "13800000001",
                        "validUntil", "2029-08-31", "version", profile.get("version").asLong()), 200);
        String committeeDecision = endpoint + "/" + committee.get("id").asLong() + "/decision";
        putJson(admin, committeeDecision, Map.of("approve", true), 409);
        putJson(admin, committeeDecision, Map.of("approve", false, "note", "已转班"), 200);
    }

    private MockHttpSession permissionReviewer(MockHttpSession admin, String name, String role, String scope,
                                               String source) throws Exception {
        long id = postJson(admin, "/api/admin/accounts", Map.of("username", name, "password", SECRET), 200).get("id").asLong();
        grant(admin, id, role, scope);
        return login(name, source);
    }

    private int permissionDecision(MockHttpSession session, String path, boolean approve, String note) throws Exception {
        var token = csrf(session);
        return mvc.perform(put(path).session(session).header(token.get("headerName").asString(), token.get("token").asString())
                        .contentType("application/json").content(JSON.writeValueAsBytes(Map.of("approve", approve, "note", note))))
                .andReturn().getResponse().getStatus();
    }

    @Test
    void rosterPreviewIsReadOnlyAndImportAllowsRegistrationOnlyForValidFiles() throws Exception {
        String source = "192.0.2.12";
        var admin = login("test_admin", source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String className = "导入测试-" + suffix;
        long classId = addClass(admin, className);
        String number = studentNumberPrefix(suffix) + "1";
        String second = number.substring(0, number.length() - 1) + "2";
        String viewerName = "roster_reader_" + suffix;
        postJson(admin, "/api/admin/accounts", Map.of("username", viewerName, "password", SECRET), 200);
        var ordinary = login(viewerName, source);

        var template = mvc.perform(get("/api/admin/roster/template").session(admin))
                .andExpect(status().isOk()).andExpect(content().contentType(ReportConstants.XLSX_MEDIA_TYPE)).andReturn();
        try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(template.getResponse().getContentAsByteArray()))) {
            var header = workbook.getSheetAt(0).getRow(0);
            for (int i = 0; i < RosterConstants.ROSTER_HEADERS.size(); i++) {
                assertEquals(RosterConstants.ROSTER_HEADERS.get(i), header.getCell(i).getStringCellValue());
            }
        }
        mvc.perform(get("/api/admin/roster/template").session(ordinary)).andExpect(status().isForbidden());
        String shortNumber = number.substring(1);
        for (String malformed : List.of(shortNumber, number + "1")) {
            postJson(admin, "/api/admin/students", Map.of("studentNo", malformed, "name", "长度错误测试",
                    "classId", classId, "phone", "13800000001", "enrolledOn", "2025-09-01"), 400);
        }
        byte[] wrongLength = rosterWorkbook(shortNumber, second, className, true);
        var lengthCheck = read(uploadRoster(admin, "check", wrongLength, 200));
        assertFalse(lengthCheck.get("ready").asBoolean());
        assertEquals(1, lengthCheck.get("issues").size());
        assertTrue(lengthCheck.get("issues").get(0).get("message").asString().contains("12 位数字"));
        uploadRoster(admin, "import", wrongLength, 400);
        assertEquals(0, rosterCount(shortNumber, second));

        byte[] invalid = rosterWorkbook(number, second, className, false);
        uploadRoster(ordinary, "check", invalid, 403);
        var issues = read(uploadRoster(admin, "check", invalid, 200));
        assertFalse(issues.get("ready").asBoolean());
        assertEquals(3, issues.get("issues").get(0).get("row").asInt());
        uploadRoster(admin, "import", invalid, 400);
        assertEquals(0, rosterCount(number, second));

        byte[] valid = rosterWorkbook(number, second, className, true);
        var preview = read(uploadRoster(admin, "check", valid, 200));
        assertTrue(preview.get("ready").asBoolean());
        assertEquals(2, preview.get("rows").asInt());
        assertEquals(0, rosterCount(number, second));
        var imported = read(uploadRoster(admin, "import", valid, 200));
        assertEquals(2, imported.get("studentCount").asInt());
        assertEquals(2, rosterCount(number, second));
        registerWithEmail(new MockHttpSession(), Map.of("studentNo", number, "name", "导入甲", "classId", classId,
                "phone", "13800000001", "email", STUDENT_EMAIL, "password", SECRET), source);
        uploadRoster(admin, "import", valid, 400);
        assertEquals(2, rosterCount(number, second));
    }

    private int rosterCount(String first, String second) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE student_no IN (?, ?)", Integer.class, first, second);
    }

    private byte[] rosterWorkbook(String first, String second, String className, boolean valid) throws Exception {
        return rosterWorkbook(first, second, className, valid, "13800000001");
    }

    private byte[] rosterWorkbook(String first, String second, String className, boolean valid, String firstPhone) throws Exception {
        try (var workbook = new XSSFWorkbook(); var output = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet("名册");
            var header = sheet.createRow(0);
            for (int i = 0; i < RosterConstants.ROSTER_HEADERS.size(); i++) {
                header.createCell(i).setCellValue(RosterConstants.ROSTER_HEADERS.get(i));
            }
            var a = sheet.createRow(1);
            var b = sheet.createRow(2);
            a.createCell(0).setCellValue(first);
            a.createCell(1).setCellValue("导入甲");
            a.createCell(2).setCellValue(className);
            a.createCell(3).setCellValue(firstPhone);
            a.createCell(4).setCellValue("2025-09-01");
            b.createCell(0).setCellValue(second);
            b.createCell(1).setCellValue("导入乙");
            b.createCell(2).setCellValue(valid ? className : "未建立的班级");
            if (valid) b.createCell(3).setCellValue("13800000002");
            else b.createCell(3).setCellFormula("1+1");
            var date = b.createCell(4);
            date.setCellValue(LocalDate.of(2025, 9, 1));
            var dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            date.setCellStyle(dateStyle);
            workbook.write(output);
            return output.toByteArray();
        }
    }

    private MvcResult uploadRoster(MockHttpSession session, String operation, byte[] bytes, int expected) throws Exception {
        JsonNode token = csrf(session);
        return mvc.perform(multipart("/api/admin/roster/" + operation)
                        .file(new MockMultipartFile("file", "roster.xlsx", ReportConstants.XLSX_MEDIA_TYPE, bytes))
                        .session(session).header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().is(expected)).andReturn();
    }

    @Test
    void classRosterImportValidatesWholeFileAndReusesCounselors() throws Exception {
        var admin = login("test_admin", "192.0.2.115");
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String className = "导入班-" + suffix;
        String newCounselor = "导入辅导员-" + suffix;
        String standalone = "独立辅导员-" + suffix;
        var validRows = List.of(List.of(className, "测试辅导员", newCounselor), List.of("", standalone));
        var invalid = classRosterWorkbook(List.of(validRows.getFirst(), List.of(className, standalone)));
        assertFalse(read(uploadRoster(admin, "class-import/check", invalid, 200)).get("ready").asBoolean());
        uploadRoster(admin, "class-import/import", invalid, 400);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM counselor WHERE name = ?", Integer.class, newCounselor));

        var valid = classRosterWorkbook(validRows);
        assertTrue(read(uploadRoster(admin, "class-import/check", valid, 200)).get("ready").asBoolean());
        var result = read(uploadRoster(admin, "class-import/import", valid, 200));
        assertEquals(1, result.get("classCount").asInt());
        assertEquals(2, result.get("counselorCount").asInt());
        assertEquals("测试辅导员、" + newCounselor,
                jdbc.queryForObject("SELECT counselor FROM class_group WHERE name = ?", String.class, className));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM counselor WHERE name = ?", Integer.class, standalone));
        uploadRoster(admin, "class-import/import", valid, 400);
    }

    private byte[] classRosterWorkbook(List<List<String>> rows) throws Exception {
        try (var workbook = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            var sheet = workbook.createSheet();
            var headers = RosterConstants.CLASS_ROSTER_HEADERS;
            var header = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) header.createCell(i).setCellValue(headers.get(i));
            int index = 1;
            for (var values : rows) {
                var row = sheet.createRow(index++);
                for (int i = 0; i < values.size(); i++) row.createCell(i).setCellValue(values.get(i));
            }
            workbook.write(bytes);
            return bytes.toByteArray();
        }
    }

    @Test
    void counselorDirectoryAndOptionalPhoneWorkAcrossRegistrationAndBinding() throws Exception {
        String source = "192.0.2.101";
        var admin = login("test_admin", source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String className = "选项测试班-" + suffix;
        String counselorName = "张老师-" + suffix;
        String directory = "/api/admin/counselors";
        postJson(admin, "/api/admin/classes", Map.of("name", className, "counselor", counselorName), 400);
        var counselor = postJson(admin, directory, Map.of("name", counselorName), 200);
        long counselorId = counselor.get("id").asLong();
        postJson(admin, directory, Map.of("name", " " + counselorName + " "), 409);
        mvc.perform(get(directory).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + counselorId + ")].name").value(org.hamcrest.Matchers.hasItem(counselorName)));
        long classId = postJson(admin, "/api/admin/classes", Map.of("name", className, "counselor", counselorName), 200)
                .get("id").asLong();
        String secondCounselor = "李老师-" + suffix;
        String thirdCounselor = "王老师-" + suffix;
        postJson(admin, directory, Map.of("name", secondCounselor), 200);
        postJson(admin, directory, Map.of("name", thirdCounselor), 200);
        String classPath = "/api/admin/roster/classes/" + classId;
        putJson(admin, classPath, Map.of("name", className, "version", 0,
                "counselors", List.of(counselorName, counselorName)), 400);
        putJson(admin, classPath, Map.of("name", className, "version", 0,
                "counselors", List.of(counselorName, secondCounselor, thirdCounselor, "测试辅导员")), 400);
        var updated = putJson(admin, classPath, Map.of("name", className, "version", 0,
                "counselors", List.of(counselorName, secondCounselor, thirdCounselor)), 200);
        assertEquals(counselorName + "、" + secondCounselor + "、" + thirdCounselor, updated.get("counselor").asString());
        assertEquals(3, updated.get("counselors").size());
        assertEquals(3, jdbc.queryForObject("SELECT COUNT(*) FROM class_counselor WHERE class_id = ?", Integer.class, classId));

        String numbers = studentNumberPrefix(suffix);
        String noPhone = numbers + "1";
        var enrollment = new HashMap<String, Object>(Map.of("studentNo", noPhone, "name", "无手机号学生",
                "classId", classId, "enrolledOn", "2025-09-01"));
        enrollment.put("phone", "123");
        postJson(admin, "/api/admin/students", enrollment, 400);
        enrollment.remove("phone");
        postJson(admin, "/api/admin/students", enrollment, 200);
        assertNull(jdbc.queryForObject("SELECT phone FROM student WHERE student_no = ?", String.class, noPhone));
        registerWithEmail(new MockHttpSession(), Map.of("studentNo", noPhone, "name", "无手机号学生",
                "classId", classId, "email", STUDENT_EMAIL, "password", SECRET), source);
        var student = login(noPhone, source);
        mvc.perform(get(directory).session(student)).andExpect(status().isForbidden());
        postJson(student, directory, Map.of("name", "无权限"), 403);

        String phoneNumber = numbers + "2";
        enrollment.put("studentNo", phoneNumber);
        enrollment.put("phone", "13800000001");
        postJson(admin, "/api/admin/students", enrollment, 200);
        postJson(new MockHttpSession(), "/api/bind/email-code", Map.of("studentNo", phoneNumber, "name", "无手机号学生",
                "classId", classId, "email", STUDENT_EMAIL), 400, source);
        registerWithEmail(new MockHttpSession(), Map.of("studentNo", phoneNumber, "name", "无手机号学生",
                "classId", classId, "phone", "13800000001", "email", STUDENT_EMAIL, "password", SECRET), source);

        String firstImport = numbers + "3";
        byte[] workbook = rosterWorkbook(firstImport, numbers + "4", className, true, "");
        assertTrue(read(uploadRoster(admin, "check", workbook, 200)).get("ready").asBoolean());
        uploadRoster(admin, "import", workbook, 200);
        assertNull(jdbc.queryForObject("SELECT phone FROM student WHERE student_no = ?", String.class, firstImport));
    }

    @Test
    void rosterDeletionRemovesActiveEntriesAndPreservesReferencedHistory() throws Exception {
        String source = "192.0.2.93";
        var admin = login("test_admin", source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String numbers = studentNumberPrefix(suffix);
        long group = addClass(admin, "删除验证班-" + suffix);
        long emptyGroup = addClass(admin, "空班-" + suffix);
        String classes = "/api/admin/roster/classes/";
        String students = "/api/admin/roster/students/";
        String phone = "13800000001";
        String unboundNumber = numbers + "1";
        String boundNumber = numbers + "2";
        var unboundForm = Map.of("studentNo", unboundNumber, "name", "未绑定学生", "classId", group,
                "phone", phone, "enrolledOn", "2025-09-01");
        postJson(admin, "/api/admin/students", unboundForm, 200);
        var student = bindCorrectionStudent(admin, boundNumber, "已绑定学生", group, source);
        long unboundId = jdbc.queryForObject("SELECT id FROM student WHERE student_no = ?", Long.class, unboundNumber);
        long boundId = jdbc.queryForObject("SELECT id FROM student WHERE student_no = ?", Long.class, boundNumber);
        long userId = jdbc.queryForObject("SELECT id FROM app_user WHERE student_id = ?", Long.class, boundId);

        deleteRosterItem(student, students + unboundId, 0, 403);
        deleteRosterItem(admin, classes + group, 0, 409);
        deleteRosterItem(admin, classes + emptyGroup, 1, 409);
        deleteRosterItem(admin, classes + emptyGroup, 0, 200);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM class_group WHERE id = ?", Integer.class, emptyGroup));

        deleteRosterItem(admin, students + unboundId, 0, 200);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE id = ?", Integer.class, unboundId));
        postJson(new MockHttpSession(), "/api/bind/email-code", Map.of("studentNo", unboundNumber, "name", "未绑定学生",
                "classId", group, "phone", phone, "email", STUDENT_EMAIL), 400, source);
        postJson(admin, "/api/admin/students", unboundForm, 200);
        long replacement = jdbc.queryForObject("SELECT id FROM student WHERE student_no = ?", Long.class, unboundNumber);
        assertNotEquals(unboundId, replacement);
        long record = postJson(admin, "/api/violations", draft(boundNumber, "CLASS_ATTENDANCE", "2026-11-11"), 200).get("id").asLong();
        deleteRosterItem(admin, students + boundId, 1, 409);
        String bulk = classes + group + "/students";
        deleteRosterItem(student, bulk + "?studentCount=2", 0, 403);
        deleteRosterItem(admin, bulk + "?studentCount=1", 0, 409);
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE class_id = ? AND deleted_at IS NULL", Integer.class, group));
        deleteRosterItem(admin, bulk + "?studentCount=2", 0, 200);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM student WHERE id = ?", Integer.class, replacement));
        mvc.perform(get("/api/me").session(student)).andExpect(status().isUnauthorized());
        login(boundNumber, SECRET, source, 401);
        putJson(admin, "/api/admin/accounts/" + userId + "/enabled", Map.of("enabled", true), 404);
        mvc.perform(get("/api/students/lookup").param("q", boundNumber).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));
        mvc.perform(get("/api/admin/roster/students").param("classId", Long.toString(group)).session(admin))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM student WHERE id = ?", Long.class, boundId));
        deleteRosterItem(admin, classes + group, 0, 200);
        assertNotNull(jdbc.queryForObject("SELECT deleted_at FROM class_group WHERE id = ?", Long.class, group));
        mvc.perform(get("/api/admin/roster/classes").session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id == " + group + ")]").isEmpty());
        mvc.perform(get("/api/violations/" + record).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentNo").value(boundNumber))
                .andExpect(jsonPath("$.data.classId").value(group));
        postJson(admin, "/api/violations", draft(boundNumber, "CLASS_ATTENDANCE", "2026-11-11"), 400);
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'DELETE_STUDENT' AND target_id = ?",
                Integer.class, boundId));
        deleteRosterItem(admin, "/api/violations/" + record, 0, 200);
    }

    private void deleteRosterItem(MockHttpSession session, String path, long version, int expected) throws Exception {
        JsonNode token = csrf(session);
        mvc.perform(delete(path).session(session).param("version", Long.toString(version))
                        .header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().is(expected));
    }

    @Test
    void rosterEditsRespectAccountScopeAndApplyNumberRulesOnlyToNewEntries() throws Exception {
        String source = "192.0.2.35";
        String roster = "/api/admin/roster";
        String numberRule = roster + "/student-number-rule";
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String numbers = studentNumberPrefix(suffix);
        String oldNo = numbers + "1";
        String secondNo = numbers + "2";
        String name = "名册_甲";
        String phone = "13800000001";
        var admin = login("test_admin", source);
        long oldClass = addClass(admin, "旧班-" + suffix);
        long newClass = addClass(admin, "新班-" + suffix);
        var enrollment = Map.of("studentNo", oldNo, "name", name, "classId", oldClass,
                "phone", phone, "enrolledOn", "2025-09-01");
        postJson(admin, "/api/admin/students", enrollment, 200);
        postJson(admin, "/api/admin/students", Map.of("studentNo", secondNo, "name", "名册乙",
                "classId", oldClass, "phone", phone, "enrolledOn", "2025-09-01"), 200);
        var originalRule = read(mvc.perform(get(numberRule).session(admin)).andExpect(status().isOk()).andReturn());
        long version = originalRule.get("version").asLong();
        putJson(admin, numberRule, Map.of("minLength", 14, "maxLength", 10, "prefix", "9", "version", version), 400);
        var updatedRule = putJson(admin, numberRule,
                Map.of("minLength", 13, "maxLength", 20, "prefix", "9", "version", version), 200);
        try {
            postJson(admin, "/api/admin/students", Map.of("studentNo", numbers + "3", "name", name, "classId", oldClass,
                    "phone", phone, "enrolledOn", "2025-09-01"), 400);
            var file = rosterWorkbook(numbers + "3", "9" + numbers + "4", "旧班-" + suffix, true);
            var check = read(uploadRoster(admin, "check", file, 200));
            assertFalse(check.get("ready").asBoolean());
            assertEquals(1, check.get("issues").size());
            uploadRoster(admin, "import", file, 400);

            registerWithEmail(new MockHttpSession(), Map.of("studentNo", oldNo, "name", name, "classId", oldClass,
                    "phone", phone, "email", STUDENT_EMAIL, "password", SECRET), source);
            var student = login(oldNo, "192.0.2.36");
            long accountId = read(mvc.perform(get("/api/me").session(student)).andExpect(status().isOk()).andReturn())
                    .get("id").asLong();
            grant(admin, accountId, "CLASS_COMMITTEE", Long.toString(oldClass));
            mvc.perform(get(roster + "/students").session(student)).andExpect(status().isForbidden());
            mvc.perform(get(numberRule).session(student)).andExpect(status().isForbidden());
            putJson(student, numberRule, updatedRule, 403);
            mvc.perform(get(roster + "/students").session(admin).param("classId", Long.toString(oldClass)).param("size", "1"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2))
                    .andExpect(jsonPath("$.data.items.length()").value(1));
            var matching = read(mvc.perform(get(roster + "/students").session(admin).param("query", "_甲")
                            .param("classId", Long.toString(oldClass)))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                    .andExpect(jsonPath("$.data.items[0].bound").value(true)).andReturn());
            long id = matching.get("items").get(0).get("id").asLong();
            mvc.perform(get(roster + "/students").session(admin).param("query", "%"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(0));

            String newCounselor = "新辅导员-" + suffix;
            postJson(admin, "/api/admin/counselors", Map.of("name", newCounselor), 200);
            var classEdit = Map.of("name", "更名班-" + suffix, "counselor", newCounselor, "version", 0);
            putJson(admin, roster + "/classes/" + newClass, classEdit, 200);
            putJson(admin, roster + "/classes/" + newClass, classEdit, 409);
            var change = Map.of("name", name, "phone", phone, "classId", newClass, "validUntil", "2026-11-10", "version", 0);
            var changed = putJson(admin, roster + "/students/" + id, change, 200);
            assertEquals(newCounselor, changed.get("counselor").asString());
            assertEquals(1, changed.get("version").asInt());
            putJson(admin, roster + "/students/" + id, change, 409);
            mvc.perform(get("/api/me").session(student)).andExpect(status().isUnauthorized());
            putJson(admin, roster + "/students/" + id,
                    Map.of("name", name, "phone", phone, "classId", newClass, "validUntil", "2030-11-11", "version", 1), 200);
            mvc.perform(get("/api/me").session(student)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.grants.length()").value(0));

            var second = read(mvc.perform(get(roster + "/students").session(admin).param("query", secondNo))
                    .andExpect(status().isOk()).andReturn()).get("items").get(0);
            putJson(admin, roster + "/students/" + second.get("id").asLong(),
                    Map.of("name", "更正后姓名", "phone", phone, "classId", oldClass, "validUntil", "2030-11-11", "version", 0), 200);
            var bindForm = new HashMap<String, Object>(Map.of("studentNo", secondNo, "name", "名册乙",
                    "classId", oldClass, "phone", phone, "email", STUDENT_EMAIL, "password", SECRET));
            postJson(new MockHttpSession(), "/api/bind/email-code", bindForm, 400, source);
            bindForm.put("name", "更正后姓名");
            registerWithEmail(new MockHttpSession(), bindForm, source);
        } finally {
            putJson(admin, numberRule, Map.of("minLength", originalRule.get("minLength").asInt(),
                    "maxLength", originalRule.get("maxLength").asInt(), "prefix", originalRule.get("prefix").asString(),
                    "version", updatedRule.get("version").asLong()), 200);
        }
    }

    @Test
    void correctionReassignsAccessAndKeepsResolutionAndAuditConsistent() throws Exception {
        String source = "192.0.2.61";
        var admin = login("test_admin", source);
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String numbers = studentNumberPrefix(suffix);
        long firstClass = addClass(admin, "纠错甲班-" + suffix);
        long secondClass = addClass(admin, "纠错乙班-" + suffix);
        String firstNo = numbers + "1";
        String secondNo = numbers + "2";
        var first = bindCorrectionStudent(admin, firstNo, "误登记学生", firstClass, source);
        var second = bindCorrectionStudent(admin, secondNo, "正确学生", secondClass, source);

        var created = postJson(admin, "/api/violations", draft(firstNo, "CLASS_ATTENDANCE", "2026-11-11"), 200);
        long id = created.get("id").asLong();
        String record = "/api/violations/" + id;
        String correction = "/api/admin/violations/" + id + "/correction";
        postJson(admin, "/api/violations/review", review(id, 0), 200);
        postJson(admin, record + "/resolve", Map.of("resolution", "HANDED_BACK", "version", 1), 200);
        Long firstReturn = jdbc.queryForObject("SELECT handed_back_at FROM violation_record WHERE id = ?", Long.class, id);
        assertNotNull(firstReturn);

        var input = new HashMap<String, Object>(Map.of("version", 2, "studentNo", secondNo, "occurredOn", "2026-11-10",
                "typeCode", "DORM_SAFETY", "status", "HANDED_BACK", "correctionReason", "登记时选错学生和类型"));
        putJson(first, correction, input, 403);
        putJson(admin, correction, input, 400);
        input.put("status", "RECORDED");
        putJson(admin, correction, input, 400);
        input.put("status", "APPROVED");
        input.put("occurredOn", "2026-11-08");
        putJson(admin, correction, input, 400);
        input.put("occurredOn", "2026-11-10");
        var updated = putJson(admin, correction, input, 200);
        assertEquals(secondNo, updated.get("studentNo").asString());
        assertEquals(secondClass, updated.get("classId").asLong());
        assertEquals("DORM", updated.get("departmentCode").asString());
        assertEquals("APPROVED", updated.get("status").asString());
        assertEquals(3, updated.get("version").asInt());
        assertNull(jdbc.queryForObject("SELECT handed_back_at FROM violation_record WHERE id = ?", Long.class, id));
        putJson(admin, correction, input, 409);
        mvc.perform(get(record).session(first)).andExpect(status().isNotFound());
        mvc.perform(get("/api/notices").session(first)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0));
        mvc.perform(get(record).session(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.occurredOn").value("2026-11-10"))
                .andExpect(jsonPath("$.data.correctable").value(false));
        mvc.perform(get("/api/notices").session(second)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].title").value("违纪记录已更正"));
        String auditDetails = jdbc.queryForObject("SELECT details FROM audit_log WHERE action = 'CORRECT_VIOLATION' AND target_id = ?",
                String.class, id);
        assertTrue(auditDetails.contains("CLASS_ATTENDANCE -> DORM_SAFETY"));
        assertTrue(auditDetails.contains("登记时选错学生和类型"));

        postJson(admin, record + "/resolve", Map.of("resolution", "APPEALED", "version", 3), 200);
        input.put("version", 4);
        input.put("status", "HANDED_BACK");
        input.put("correctionReason", "办公室确认处理状态");
        putJson(admin, correction, input, 200);
        assertNotNull(jdbc.queryForObject("SELECT handed_back_at FROM violation_record WHERE id = ?", Long.class, id));
        input.put("version", 5);
        input.put("status", "APPEALED");
        putJson(admin, correction, input, 200);
        assertNull(jdbc.queryForObject("SELECT handed_back_at FROM violation_record WHERE id = ?", Long.class, id));

        JsonNode token = csrf(admin);
        mvc.perform(delete(record).session(admin).param("version", "6")
                        .header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().isOk());
    }

    @Test
    void workflowOptionsKeepHistoricalLabelsAndBulletinExcludesAppeals() throws Exception {
        String source = "192.0.2.84";
        var admin = login("test_admin", source);
        String settings = "/api/admin/workflow";
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        String studentNo = studentNumberPrefix(suffix) + "1";
        long classId = addClass(admin, "业务配置测试班-" + suffix);
        var student = bindCorrectionStudent(admin, studentNo, "配置测试学生", classId, source);
        mvc.perform(get(settings).session(student)).andExpect(status().isForbidden());
        mvc.perform(get(settings).session(admin)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.options.length()").value(6))
                .andExpect(jsonPath("$.data.types.length()").value(6));

        String label = "样例检查-" + suffix;
        var option = postJson(admin, settings + "/inspections", Map.of("label", label, "category", "示例分类"), 200);
        assertEquals("示例分类", option.get("category").asString());
        String code = option.get("code").asString();
        String type = "MORNING_EXERCISE";
        String typePath = settings + "/types/" + type;
        long typeVersion = jdbc.queryForObject("SELECT version FROM violation_type WHERE code = ?", Long.class, type);
        var updatedType = putJson(admin, typePath, Map.of("departmentCode", "DORM", "version", typeVersion), 200);
        String appealPath = settings + "/options/APPEAL_1";
        String appealLabel = "测试申诉成功";
        long appealVersion = jdbc.queryForObject("SELECT version FROM workflow_option WHERE code = 'APPEAL_1'", Long.class);
        var appeal = putJson(admin, appealPath, Map.of("label", appealLabel, "enabled", true, "version", appealVersion), 200);
        var ids = new ArrayList<Long>();
        try {
            var draft = new HashMap<>(draft(studentNo, type, "2026-11-11"));
            draft.put("inspectionScope", code);
            var created = postJson(admin, "/api/violations", draft, 200);
            long appealed = created.get("id").asLong();
            ids.add(appealed);
            assertEquals("DORM", created.get("departmentCode").asString());
            assertEquals(label, created.get("inspectionLabel").asString());
            var changed = putJson(admin, settings + "/options/" + code,
                    Map.of("label", label + "新名称", "enabled", false, "version", 0, "category", "其他分类"), 200);
            assertFalse(changed.get("enabled").asBoolean());
            assertEquals("其他分类", changed.get("category").asString());
            putJson(admin, settings + "/options/" + code, Map.of("label", label, "enabled", true, "version", 0), 409);
            deleteRosterItem(admin, settings + "/inspections/" + code, 0, 409);
            deleteRosterItem(admin, settings + "/inspections/" + code, 1, 200);
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM workflow_option WHERE code = ?", Integer.class, code));
            mvc.perform(get("/api/metadata").session(student)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.appealOptions.length()").value(0))
                    .andExpect(jsonPath("$.data.inspectionOptions.length()").value(2));
            String recordPath = "/api/violations/" + appealed;
            putJson(admin, recordPath, Map.of("reason", "保留原来源", "disposition", "处理意见",
                    "version", 0, "inspectionScope", code), 200);
            mvc.perform(get(recordPath).session(student)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.inspectionLabel").value(label));

            postJson(admin, "/api/violations/review", review(appealed, 1), 200);
            postJson(admin, recordPath + "/resolve", Map.of("resolution", "APPEALED", "version", 2), 400);
            postJson(admin, recordPath + "/resolve", Map.of("resolution", "HANDED_BACK", "version", 2, "appealCode", "APPEAL_1"), 400);
            postJson(admin, recordPath + "/resolve", Map.of("resolution", "APPEALED", "version", 2, "appealCode", "APPEAL_1"), 200);
            mvc.perform(get(recordPath).session(admin)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.appealLabel").value(appealLabel));
            mvc.perform(get(recordPath).session(student)).andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.appealLabel").doesNotExist());

            draft.put("submissionId", UUID.randomUUID().toString());
            postJson(admin, "/api/violations", draft, 400);
            long approved = postJson(admin, "/api/violations", draft(studentNo, type, "2026-11-11"), 200).get("id").asLong();
            ids.add(approved);
            postJson(admin, "/api/violations/review", review(approved, 0), 200);
            ids.add(postJson(admin, "/api/violations", draft(studentNo, type, "2026-11-11"), 200).get("id").asLong());
            String classFilter = Long.toString(classId);
            mvc.perform(get("/api/violations").session(admin).param("classId", classFilter).param("formalOnly", "true"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(2));
            mvc.perform(get("/api/violations").session(admin).param("classId", classFilter).param("status", "PENDING"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1));
            mvc.perform(get("/api/violations/bulletin").session(student)).andExpect(status().isForbidden());
            var bulletin = mvc.perform(get("/api/violations/bulletin").session(admin).param("classId", classFilter))
                    .andExpect(status().isOk()).andExpect(content().contentType(ReportConstants.XLSX_MEDIA_TYPE))
                    .andReturn().getResponse().getContentAsByteArray();
            try (var workbook = new XSSFWorkbook(new ByteArrayInputStream(bulletin))) {
                var sheet = workbook.getSheetAt(0);
                assertEquals(ReportConstants.BULLETIN_TITLE, sheet.getSheetName());
                assertEquals(3, sheet.getLastRowNum());
                assertEquals("已审核", sheet.getRow(3).getCell(11).getStringCellValue());
            }
        } finally {
            for (long id : ids) jdbc.update("UPDATE violation_record SET deleted_at = ? WHERE id = ?", 1L, id);
            putJson(admin, typePath, Map.of("departmentCode", "INSPECTION", "version", updatedType.get("version").asLong()), 200);
            putJson(admin, appealPath, Map.of("label", "", "enabled", false, "version", appeal.get("version").asLong()), 200);
        }
    }

    @Test
    void temporaryPasswordRevokesSessionsAndAuditKeepsOnlyMetadata() throws Exception {
        String source = "192.0.2.77";
        var admin = login("test_admin", source);
        String username = "reset_" + UUID.randomUUID().toString().substring(0, 8);
        long id = postJson(admin, "/api/admin/accounts", Map.of("username", username, "password", SECRET), 200)
                .get("id").asLong();
        String reset = "/api/admin/accounts/" + id + "/password-reset";
        var beforeReset = login(username, source);
        postJson(beforeReset, reset, null, 403);
        String authHeader = "no-store";
        JsonNode token = csrf(admin);
        var resetResponse = mvc.perform(post(reset).session(admin)
                        .header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().isOk()).andExpect(header().string(HttpHeaders.CACHE_CONTROL, authHeader)).andReturn();
        var temporary = read(resetResponse);
        String code = temporary.get("password").asString();
        assertNotEquals(SECRET, code);
        assertEquals(18, code.length());
        assertTrue(code.matches(org.example.disciplinaryrecordsmanagement.constant.ValidationConstants.PASSWORD_PATTERN));
        assertEquals(Instant.parse("2026-11-12T03:00:00Z").toEpochMilli(), temporary.get("expiresAt").asLong());
        mvc.perform(get("/api/me").session(beforeReset)).andExpect(status().isUnauthorized());
        login(username, SECRET, source, 401);

        var tempSession = login(username, code, source, 200);
        var anotherTempSession = login(username, code, source, 200);
        mvc.perform(get("/api/me").session(tempSession)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true));
        mvc.perform(get("/api/metadata").session(tempSession)).andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ErrorCode.REQUIRE_PASSWORD_CHANGE.code()));
        mvc.perform(get("/api/admin/audit").session(tempSession)).andExpect(status().isForbidden());
        String newPassword = "Replacement_26@";
        putJson(tempSession, "/api/password", Map.of("currentPassword", "invalid", "newPassword", newPassword), 400, source);
        putJson(tempSession, "/api/password", Map.of("currentPassword", code, "newPassword", newPassword), 200, source);
        assertTrue(tempSession.isInvalid());
        mvc.perform(get("/api/me").session(anotherTempSession)).andExpect(status().isUnauthorized());
        login(username, code, source, 401);
        var current = login(username, newPassword, source, 200);
        mvc.perform(get("/api/me").session(current)).andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(false));
        mvc.perform(get("/api/admin/audit").session(current)).andExpect(status().isForbidden());

        var result = mvc.perform(get("/api/admin/audit").session(admin)
                        .param("from", "2026-11-11").param("to", "2026-11-11").param("target", "ACCOUNT")
                        .param("targetId", Long.toString(id)).param("action", "RESET_PASSWORD").param("actor", "test_admin"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].action").value("RESET_PASSWORD")).andReturn();
        String json = result.getResponse().getContentAsString();
        assertFalse(json.contains(code));
        assertFalse(json.contains(newPassword));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE action = 'CHANGE_PASSWORD' AND target_id = ?",
                Integer.class, id));
    }

    @Test
    void errorsAndSecurityFiltersKeepTheJsonContract() throws Exception {
        String source = "192.0.2.42";
        MockHttpSession anonymous = new MockHttpSession();
        JsonNode token = csrf(anonymous);
        mvc.perform(post("/api/login").session(anonymous)
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .with(request -> { request.setRemoteAddr(source); return request; })
                        .param("username", "test_admin").param("password", "wrong"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value(ErrorCode.BAD_CREDENTIALS.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCode.BAD_CREDENTIALS.message()));

        MockHttpSession admin = login("test_admin", source);
        mvc.perform(get("/api/violations").param("size", "0").session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID.code()));
        mvc.perform(get("/api/violations").param("from", "2026-11-12").param("to", "2026-11-11").session(admin))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value(ErrorCode.INVALID.code()));
        mvc.perform(get("/api/does-not-exist").session(admin))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value(ErrorCode.MISSING.code()));
        mvc.perform(get("/api/test-failure").session(admin))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.code").value(ErrorCode.UNEXPECTED.code()))
                .andExpect(jsonPath("$.msg").value(ErrorCode.UNEXPECTED.message()));
        token = csrf(admin);
        mvc.perform(post("/api/me").session(admin).header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.code").value(ErrorCode.WRONG_METHOD.code()));
        mvc.perform(post("/api/logout").session(admin).header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(Result.SUCCESS));
    }

    @Test
    void loginRateLimitUsesConfigurationAndReturnsTheSameEnvelope() throws Exception {
        MockHttpSession session = new MockHttpSession();
        JsonNode token = csrf(session);
        for (int i = 0; i <= policy.loginAttemptsPerIp(); i++) {
            var attempt = mvc.perform(post("/api/bind").session(session)
                    .with(request -> {
                        request.setRemoteAddr("192.0.2.4");
                        return request;
                    })
                    .header(token.get("headerName").asString(), token.get("token").asString())
                    .contentType("application/json").content("{}"));
            if (i < policy.loginAttemptsPerIp()) attempt.andExpect(status().isBadRequest());
            else attempt.andExpect(status().isTooManyRequests())
                    .andExpect(header().string("Retry-After", "60"))
                    .andExpect(jsonPath("$.code").value(ErrorCode.TOO_MANY_REQUESTS.code()));
        }
    }

    private int race(CountDownLatch ready, CountDownLatch start, MockHttpSession session, long id) throws Exception {
        ready.countDown();
        assertTrue(start.await(5, TimeUnit.SECONDS));
        return postJson(session, "/api/violations/review", review(id, 0), 200).get("changed").size();
    }

    private Map<String, Object> draft(String number, String type, String date) {
        return Map.of("studentNo", number, "occurredOn", date, "typeCode", type, "reason", "仅用于自动化测试",
                "inspectionScope", "COLLEGE", "submissionId", UUID.randomUUID().toString());
    }

    private static String studentNumberPrefix(String suffix) {
        return String.format(Locale.ROOT, "%011d", Long.parseLong(suffix, 16) % 10_000_000_000L);
    }

    private Map<String, Object> review(long id, int version) {
        return Map.of("action", "APPROVE", "items", List.of(Map.of("id", id, "version", version)));
    }

    private long addClass(MockHttpSession admin, String label) throws Exception {
        return postJson(admin, "/api/admin/classes", Map.of("name", label, "counselor", "测试辅导员"), 200).get("id").asLong();
    }

    private MockHttpSession bind(MockHttpSession admin, String number, String name, long group, String phone,
                                 boolean verifyRegistrationChecks) throws Exception {
        postJson(admin, "/api/admin/students", Map.of("studentNo", number, "name", name,
                "classId", group, "phone", phone, "enrolledOn", "2025-09-01"), 200);
        var bindSession = new MockHttpSession();
        if (verifyRegistrationChecks) postJson(bindSession, "/api/bind/email-code", Map.of("studentNo", number, "name", name, "classId", group,
                "phone", "13900000000", "email", STUDENT_EMAIL), 400);
        var form = Map.of("studentNo", number, "name", name, "classId", group,
                "phone", phone, "email", STUDENT_EMAIL, "password", SECRET);
        registerWithEmail(bindSession, form, "127.0.0.1");
        if (verifyRegistrationChecks) postJson(bindSession, "/api/bind/email-code", form, 400);
        return login(number);
    }

    private MockHttpSession staff(MockHttpSession admin, String name, String role, String scope) throws Exception {
        long id = postJson(admin, "/api/admin/accounts", Map.of("username", name, "password", SECRET), 200).get("id").asLong();
        grant(admin, id, role, scope);
        return login(name);
    }

    private MockHttpSession bindCorrectionStudent(MockHttpSession admin, String number, String name, long group,
                                                  String source) throws Exception {
        String phone = "13800000001";
        postJson(admin, "/api/admin/students", Map.of("studentNo", number, "name", name,
                "classId", group, "phone", phone, "enrolledOn", "2025-09-01"), 200);
        registerWithEmail(new MockHttpSession(), Map.of("studentNo", number, "name", name, "classId", group,
                "phone", phone, "email", STUDENT_EMAIL, "password", SECRET), source);
        return login(number, source);
    }

    private String requestEmailCode(MockHttpSession session, Map<String, ?> form, String source) throws Exception {
        var challenge = postJson(session, "/api/bind/email-code", form, 200, source);
        assertEquals(600, challenge.get("expiresInSeconds").asInt());
        assertEquals(60, challenge.get("resendAfterSeconds").asInt());
        assertFalse(challenge.has("emailCode"));
        var code = ArgumentCaptor.forClass(String.class);
        verify(registrationMail, atLeastOnce()).send(eq((String) form.get("email")), code.capture(), eq(10L));
        return code.getValue();
    }

    private void registerWithEmail(MockHttpSession session, Map<String, ?> form, String source) throws Exception {
        var confirmed = new HashMap<String, Object>(form);
        confirmed.put("emailCode", requestEmailCode(session, form, source));
        postJson(session, "/api/bind", confirmed, 200, source);
    }

    private void grant(MockHttpSession admin, long id, String role, String scope) throws Exception {
        postJson(admin, "/api/admin/accounts/" + id + "/roles", Map.of("role", role, "scope", scope), 200);
    }

    private MockHttpSession login(String username) throws Exception {
        return login(username, "127.0.0.1");
    }

    private MockHttpSession login(String username, String source) throws Exception {
        return login(username, SECRET, source, 200);
    }

    private MockHttpSession login(String username, String password, String source, int expected) throws Exception {
        var session = new MockHttpSession();
        JsonNode token = csrf(session);
        mvc.perform(post("/api/login").session(session).header(token.get("headerName").asString(), token.get("token").asString())
                .with(request -> { request.setRemoteAddr(source); return request; })
                .param("username", username).param("password", password)).andExpect(status().is(expected))
                .andExpect(jsonPath("$.code").value(expected == 200 ? Result.SUCCESS : ErrorCode.BAD_CREDENTIALS.code()));
        return session;
    }

    private JsonNode postJson(MockHttpSession session, String path, Object body, int expected) throws Exception {
        return postJson(session, path, body, expected, "127.0.0.1");
    }

    private JsonNode postJson(MockHttpSession session, String path, Object body, int expected, String source) throws Exception {
        JsonNode token = csrf(session);
        MvcResult result = mvc.perform(post(path).session(session)
                .header(token.get("headerName").asString(), token.get("token").asString())
                .with(request -> { request.setRemoteAddr(source); return request; })
                .contentType("application/json").content(JSON.writeValueAsBytes(body)))
                .andExpect(status().is(expected)).andReturn();
        return read(result);
    }

    private JsonNode csrf(MockHttpSession session) throws Exception {
        return read(mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn());
    }

    private JsonNode putJson(MockHttpSession session, String path, Object body, int expected) throws Exception {
        return putJson(session, path, body, expected, "127.0.0.1");
    }

    private JsonNode putJson(MockHttpSession session, String path, Object body, int expected, String source) throws Exception {
        JsonNode token = csrf(session);
        return read(mvc.perform(put(path).session(session)
                        .header(token.get("headerName").asString(), token.get("token").asString())
                        .with(request -> { request.setRemoteAddr(source); return request; })
                        .contentType("application/json").content(JSON.writeValueAsBytes(body)))
                .andExpect(status().is(expected)).andReturn());
    }

    private void putRequest(MockHttpSession session, String path, int expected) throws Exception {
        JsonNode token = csrf(session);
        mvc.perform(put(path).session(session)
                        .header(token.get("headerName").asString(), token.get("token").asString()))
                .andExpect(status().is(expected));
    }

    private JsonNode read(MvcResult result) throws Exception {
        JsonNode response = JSON.readTree(result.getResponse().getContentAsByteArray());
        assertTrue(response.has("data"));
        assertTrue(response.get("msg").isString());
        if (result.getResponse().getStatus() >= 400) {
            assertNotEquals(Result.SUCCESS, response.get("code").asInt());
        } else {
            assertEquals(Result.SUCCESS, response.get("code").asInt());
        }
        return response.get("data");
    }
}
