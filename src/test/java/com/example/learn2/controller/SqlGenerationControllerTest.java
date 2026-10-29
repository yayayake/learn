package com.example.learn2.controller;

import com.example.learn2.service.SqlGenerationException;
import com.example.learn2.service.SqlGenerationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SqlGenerationController.class)
class SqlGenerationControllerTest {
    @Autowired MockMvc mockMvc;
    @MockitoBean SqlGenerationService service;

    @Test
    void returnsSql() throws Exception {
        when(service.generateSql("查询所有用户", "CREATE TABLE users (id INT);"))
            .thenReturn("SELECT * FROM users;");

        mockMvc.perform(post("/api/sql/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"查询所有用户\",\"schema\":\"CREATE TABLE users (id INT);\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.sql").value("SELECT * FROM users;"));
    }

    @Test
    void returns502WhenServiceThrows() throws Exception {
        when(service.generateSql(anyString(), anyString()))
            .thenThrow(new SqlGenerationException("DeepSeek error"));

        mockMvc.perform(post("/api/sql/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"q\",\"schema\":\"CREATE TABLE t (c INT);\"}"))
            .andExpect(status().isBadGateway())
            .andExpect(jsonPath("$.error").value("DeepSeek error"));
    }

    @Test
    void returns400WhenFieldsBlank() throws Exception {
        mockMvc.perform(post("/api/sql/generate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"question\":\"\",\"schema\":\"\"}"))
            .andExpect(status().isBadRequest());
    }
}
