package com.example.learn2.service;

import com.example.learn2.config.DeepSeekProperties;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class SqlGenerationService {
    private static final String SYSTEM_PROMPT = "You are a SQL expert. Given a database schema (DDL) and a natural language question, generate a single MySQL SQL statement. Return only the SQL, with no explanation and no markdown.";

    private final RestClient restClient;
    private final DeepSeekProperties properties;

    public SqlGenerationService(RestClient deepSeekRestClient, DeepSeekProperties properties) {
        this.restClient = deepSeekRestClient;
        this.properties = properties;
    }

    public String generateSql(String question, String schema) {
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            throw new SqlGenerationException("DeepSeek API key is not configured");
        }

        Map<String, Object> requestBody = Map.of(
            "model", properties.model(),
            "temperature", 0,
            "messages", List.of(
                Map.of("role", "system", "content", SYSTEM_PROMPT),
                Map.of("role", "user", "content", "Schema:\n" + schema + "\n\nQuestion:\n" + question)
            )
        );

        try {
            Map<?, ?> response = restClient.post()
                .uri("/chat/completions")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + properties.apiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(requestBody)
                .retrieve()
                .body(Map.class);

            return extractSql(response);
        } catch (SqlGenerationException e) {
            throw e;
        } catch (Exception e) {
            throw new SqlGenerationException("Failed to generate SQL from DeepSeek", e);
        }
    }

    private String extractSql(Map<?, ?> response) {
        String content = null;
        try {
            Object choices = response.get("choices");
            if (choices instanceof List<?> list && !list.isEmpty()) {
                Object first = list.get(0);
                if (first instanceof Map<?, ?> choice && choice.get("message") instanceof Map<?, ?> message) {
                    Object c = message.get("content");
                    if (c != null) {
                        content = c.toString();
                    }
                }
            }
        } catch (Exception e) {
            throw new SqlGenerationException("Failed to parse DeepSeek response", e);
        }

        if (content == null) {
            throw new SqlGenerationException("DeepSeek returned no content");
        }

        String sql = stripMarkdownFences(content.trim());
        if (sql.isBlank()) {
            throw new SqlGenerationException("DeepSeek returned empty SQL");
        }
        return sql;
    }

    private String stripMarkdownFences(String content) {
        String result = content;
        if (result.startsWith("```")) {
            int firstNewline = result.indexOf('\n');
            if (firstNewline >= 0) {
                result = result.substring(firstNewline + 1);
            } else {
                result = result.substring(3);
            }
        }
        if (result.endsWith("```")) {
            result = result.substring(0, result.length() - 3);
        }
        return result.trim();
    }
}
