package com.leetvise;

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
                .andExpect(jsonPath("$.username").value("recusant_byte"))
                .andExpect(jsonPath("$.problems").isArray());
        mvc.perform(get("/index.html")).andExpect(status().isOk());
    }

    @Test
    void rejectsOtherWebsites() throws Exception {
        mvc.perform(post("/api/revise").header("Origin", "https://evil.example")
                        .contentType("application/json").content("{\"slug\":\"two-sum\",\"rating\":\"easy\"}"))
                .andExpect(status().isForbidden());
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
