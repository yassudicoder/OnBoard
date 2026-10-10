package com.safarsaathi.backend.config;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The front-desk placeholder page is reachable at /desk, /desk/ and /desk/index.html, and the demo page at /demo,
 * /demo/ and /demo/index.html.
 * no-keys.properties fixes the settings, so a teammate's own .env cannot stop the app from starting here.
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
@AutoConfigureMockMvc
class WebConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @ParameterizedTest
    @ValueSource(strings = {"/desk", "/desk/"})
    void shortDeskAddressesForwardToThePage(String address) throws Exception {
        // MockMvc does not follow a forward; it only records where the request was handed on to.
        mockMvc.perform(get(address))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/desk/index.html"));
    }

    @Test
    void pageItselfIsServed() throws Exception {
        mockMvc.perform(get("/desk/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("arrives in Phase 4")))
                .andExpect(content().string(containsString("href=\"/demo\"")));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/demo", "/demo/"})
    void shortDemoAddressesForwardToTheDemoPage(String address) throws Exception {
        mockMvc.perform(get(address))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/demo/index.html"));
    }

    @Test
    void demoPageIsServed() throws Exception {
        mockMvc.perform(get("/demo/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Reset demo")))
                .andExpect(content().string(containsString("/api/demo/trigger-delay")));
    }
}
