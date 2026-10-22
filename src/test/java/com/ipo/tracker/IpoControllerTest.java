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
    void testGetActiveIposReturnsOnlyOpenIpos() throws Exception {
        mockMvc.perform(get("/api/v1/ipos/active")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", greaterThan(0)))
                .andExpect(jsonPath("$.data[*].status", everyItem(is("OPEN"))))
                .andExpect(jsonPath("$.data[?(@.symbol == 'SWIGGY')]", notNullValue()));
    }

    @Test
    void testGetAllIpos() throws Exception {
        mockMvc.perform(get("/api/v1/ipos")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.count", greaterThanOrEqualTo(5)));
    }

    @Test
    void testGetIpoById() throws Exception {
        mockMvc.perform(get("/api/v1/ipos/swiggy-ltd")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.data.symbol", is("SWIGGY")))
                .andExpect(jsonPath("$.data.companyName", is("Swiggy Limited")));
    }

    @Test
    void testGetUpcomingIpos() throws Exception {
        mockMvc.perform(get("/api/v1/ipos/upcoming")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[*].status", everyItem(is("UPCOMING"))));
    }
}
