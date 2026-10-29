package com.example.learn2.dto;

import jakarta.validation.constraints.NotBlank;

public record SqlGenerationRequest(@NotBlank String question, @NotBlank String schema) {}
