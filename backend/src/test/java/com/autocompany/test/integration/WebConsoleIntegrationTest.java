package com.autocompany.test.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Web Console 정적 리소스 서빙 검증 (설계 §17 Web Console)
 */
@SpringBootTest
@AutoConfigureMockMvc
public class WebConsoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testRootServesConsolePage() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("index.html"));
    }

    @Test
    public void testIndexHtmlServedDirectly() throws Exception {
        mockMvc.perform(get("/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"tabbar\"")))
                .andExpect(content().string(containsString("data-tab=\"goal\"")))
                .andExpect(content().string(containsString("id=\"goalFiles\"")))
                .andExpect(content().string(containsString("id=\"btnEvoOpen\""))) // REQ-005 진화 개설
                .andExpect(content().string(containsString("id=\"btnCancel\"")));
    }

    @Test
    public void testAppJsServed() throws Exception {
        mockMvc.perform(get("/app.js"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/api/v1/organizations/template/import")))
                .andExpect(content().string(containsString("/api/v1/employees")))
                .andExpect(content().string(containsString("/clarification-sessions")))
                .andExpect(content().string(containsString("/pending-sessions")))
                .andExpect(content().string(containsString("clientDirective"))) // §28.6 "이 정도면 진행"
                .andExpect(content().string(containsString("/cancel")))
                .andExpect(content().string(containsString("changeNote")));
    }

    @Test
    public void testStyleCssServed() throws Exception {
        mockMvc.perform(get("/style.css"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.parseMediaType("text/css")))
                .andExpect(content().string(containsString("--accent")));
    }
}
