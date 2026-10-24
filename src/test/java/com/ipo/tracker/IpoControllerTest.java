package com.ipo.tracker;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class IpoControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void testGetActiveIposReturnsSuccessfulResponse() throws Exception {
        mockMvc.perform(get("/api/v1/ipos/active")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("OPEN"))));
    }

    @Test
    void testGetAllIpos() throws Exception {
        mockMvc.perform(get("/api/v1/ipos")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(0)));
    }

    @Test
    void testGetUpcomingIpos() throws Exception {
        mockMvc.perform(get("/api/v1/ipos/upcoming")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("UPCOMING"))));
    }

    @Test
    void testValidatePanEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/allotment/validate-pan")
                .param("pan", "ABCDE1234F")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.pan", is("ABCDE****F")))
                .andExpect(jsonPath("$.data.isValid", is(true)));

        mockMvc.perform(get("/api/v1/allotment/validate-pan")
                .param("pan", "INVALID")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.isValid", is(false)));
    }

    @Test
    void testCheckAllotmentEndpoint() throws Exception {
        mockMvc.perform(get("/api/v1/allotment/check")
                .param("symbol", "CORE4ENGIN")
                .param("pan", "ABCDE1234F")
                .param("label", "Self")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.maskedPan", is("ABCDE****F")))
                .andExpect(jsonPath("$.data.label", is("Self")))
                .andExpect(jsonPath("$.data.status", notNullValue()));
    }
}

