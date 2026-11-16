package org.example.disciplinaryrecordsmanagement;

import java.util.List;
import java.util.Map;

import org.example.disciplinaryrecordsmanagement.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:bootstrapadmin;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "app.bootstrap.username=admin",
        "app.bootstrap.password=154829",
        "server.address=127.0.0.1",
        "app.report.enabled=false",
        "app.retention.enabled=false",
        "app.notice.pending-cron=-",
        "logging.file.name=target/test-application.log"
})
@AutoConfigureMockMvc
class BootstrapAdminIntegrationTests {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String INITIAL_PASSWORD = "154829";
    private static final String NEW_PASSWORD = "aB1_@9";

    @Autowired MockMvc mvc;
    @Autowired AccountService accounts;

    @Test
    void localBootstrapPasswordMustBeChangedAndIsNotRestoredAtStartup() throws Exception {
        var temporary = login(INITIAL_PASSWORD, 200);
        mvc.perform(get("/api/me").session(temporary))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.coreAdmin").value(true))
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(true));
        mvc.perform(get("/api/admin/accounts").session(temporary).param("username", "admin"))
                .andExpect(status().isForbidden());

        for (String invalid : List.of("aBc12", "123456", "Abcdef", "Abc1234567890123456", "Abc_12-",
                "Abc_12!", "Abc_12 ", "密码Abc123")) {
            changePassword(temporary, invalid, 400);
        }
        changePassword(temporary, NEW_PASSWORD, 200);
        accounts.bootstrap("admin", INITIAL_PASSWORD, "127.0.0.1");
        login(INITIAL_PASSWORD, 401);
        login("AB1_@9", 401);
        var changed = login(NEW_PASSWORD, 200);
        mvc.perform(get("/api/me").session(changed))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangeRequired").value(false));
    }

    private void changePassword(MockHttpSession session, String password, int expected) throws Exception {
        var token = csrf(session);
        mvc.perform(put("/api/password").session(session)
                        .header(token.get("headerName"), token.get("token"))
                        .contentType("application/json")
                        .content(JSON.writeValueAsBytes(Map.of("currentPassword", INITIAL_PASSWORD, "newPassword", password))))
                .andExpect(status().is(expected));
    }

    private MockHttpSession login(String password, int expected) throws Exception {
        var session = new MockHttpSession();
        var token = csrf(session);
        mvc.perform(post("/api/login").session(session)
                        .header(token.get("headerName"), token.get("token"))
                        .param("username", "admin").param("password", password))
                .andExpect(status().is(expected));
        return session;
    }

    private Map<String, String> csrf(MockHttpSession session) throws Exception {
        var body = mvc.perform(get("/api/csrf").session(session)).andExpect(status().isOk()).andReturn()
                .getResponse().getContentAsByteArray();
        var token = JSON.readTree(body).get("data");
        return Map.of("headerName", token.get("headerName").asString(), "token", token.get("token").asString());
    }
}
