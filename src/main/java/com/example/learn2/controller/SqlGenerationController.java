package com.example.learn2.controller;

import com.example.learn2.dto.SqlGenerationRequest;
import com.example.learn2.dto.SqlGenerationResponse;
import com.example.learn2.service.SqlGenerationService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/sql")
public class SqlGenerationController {
    private final SqlGenerationService service;

    public SqlGenerationController(SqlGenerationService service) {
        this.service = service;
    }

    @PostMapping("/generate")
    public SqlGenerationResponse generate(@Valid @RequestBody SqlGenerationRequest request) {
        return new SqlGenerationResponse(service.generateSql(request.question(), request.schema()));
    }
}
