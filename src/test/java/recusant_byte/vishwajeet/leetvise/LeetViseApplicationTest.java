/*
 * LeetVise – revise the LeetCode problems you've already solved.
 *
 * Copyright (c) 2026 Vishwajeet Pratap Singh
 *
 * Author:    Vishwajeet Pratap Singh
 * GitHub:    https://github.com/vishwajeet-singhh
 * LinkedIn:  https://www.linkedin.com/in/vishwajeetsage/
 * Portfolio: https://vishwajeet.me
 * Source:    https://github.com/vishwajeet-singhh/LeetVise
 */

package recusant_byte.vishwajeet.leetvise;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "leetvise.open-browser=false",
        "leetvise.username=tester",
        "leetvise.session=",
        "leetvise.excel=${java.io.tmpdir}/leetvise-test/leetvise.xlsx"
})
@AutoConfigureMockMvc
class LeetViseApplicationTest {

    @Autowired
    MockMvc mvc;

    @Test
    void servesStateAndPage() throws Exception {
        mvc.perform(get("/api/state"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("tester"))
                .andExpect(jsonPath("$.usernameSource").value("env"))
                .andExpect(jsonPath("$.problems").isArray());
        mvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics").isArray())
                .andExpect(jsonPath("$.activity").isNotEmpty());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void rejectsOtherWebsites() throws Exception {
        mvc.perform(post("/api/revise").header("Origin", "https://evil.example")
                        .contentType("application/json").content("{\"slug\":\"two-sum\",\"rating\":\"easy\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void usernameFromEnvCannotBeChangedInTheApp() throws Exception {
        mvc.perform(post("/api/username").contentType("application/json").content("{\"username\":\"someone\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(org.hamcrest.Matchers.containsString(".env")));
    }

    @Test
    void validatesInput() throws Exception {
        mvc.perform(post("/api/revise").contentType("application/json").content("{\"slug\":\"\",\"rating\":\"good\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
        mvc.perform(post("/api/revise").contentType("application/json").content("{\"slug\":\"nope\",\"rating\":\"good\"}"))
                .andExpect(status().isNotFound());
    }
}

// LeetVise · © 2026 Vishwajeet Pratap Singh · github.com/vishwajeet-singhh · linkedin.com/in/vishwajeetsage · vishwajeet.me
