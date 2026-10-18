package com.autocompany.test.integration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * WEB-002: 콘솔은 SPA(frontend/ + nginx front 컨테이너)로 분리되었다.
 * 백엔드는 더 이상 정적 콘솔을 서빙하지 않는다(설계 §17).
 * 콘솔 서빙·/api 프록시는 Docker E2E 스모크에서 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
public class BackendNoStaticConsoleTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    public void testRootNoLongerServesConsole() throws Exception {
        mockMvc.perform(get("/")).andExpect(status().isNotFound());
    }

    @Test
    public void testIndexHtmlNoLongerServed() throws Exception {
        mockMvc.perform(get("/index.html")).andExpect(status().isNotFound());
    }

    @Test
    public void testAppJsNoLongerServed() throws Exception {
        mockMvc.perform(get("/app.js")).andExpect(status().isNotFound());
    }
}
