package com.example.learn2.service;

import com.example.learn2.config.DeepSeekProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class SqlGenerationServiceTest {
    private MockRestServiceServer server;
    private SqlGenerationService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        RestClient client = builder.baseUrl("https://api.deepseek.com").build();
        DeepSeekProperties props = new DeepSeekProperties("https://api.deepseek.com", "test-key", "deepseek-chat");
        service = new SqlGenerationService(client, props);
    }

    @Test
    void generatesSqlFromQuestionAndSchema() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
            .andExpect(method(HttpMethod.POST))
            .andExpect(header("Authorization", "Bearer test-key"))
            .andExpect(content().string(containsString("CREATE TABLE users")))
            .andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"SELECT * FROM users;\"}}]}",
                MediaType.APPLICATION_JSON));

        assertEquals("SELECT * FROM users;",
            service.generateSql("查询所有用户", "CREATE TABLE users (id INT, name VARCHAR(50));"));
    }

    @Test
    void stripsMarkdownFencesFromResponse() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
            .andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"```sql\\nSELECT 1;\\n```\"}}]}",
                MediaType.APPLICATION_JSON));

        assertEquals("SELECT 1;", service.generateSql("问", "CREATE TABLE t (c INT);"));
    }

    @Test
    void throwsWhenResponseContentIsBlank() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
            .andRespond(withSuccess(
                "{\"choices\":[{\"message\":{\"content\":\"\"}}]}",
                MediaType.APPLICATION_JSON));

        assertThrows(SqlGenerationException.class,
            () -> service.generateSql("问", "CREATE TABLE t (c INT);"));
    }

    @Test
    void throwsWhenDeepSeekReturnsError() {
        server.expect(requestTo("https://api.deepseek.com/chat/completions"))
            .andRespond(withServerError());

        assertThrows(SqlGenerationException.class,
            () -> service.generateSql("问", "CREATE TABLE t (c INT);"));
    }

    @Test
    void throwsWhenApiKeyMissing() {
        DeepSeekProperties noKey = new DeepSeekProperties("https://api.deepseek.com", "", "deepseek-chat");
        SqlGenerationService noKeyService = new SqlGenerationService(RestClient.builder().build(), noKey);

        assertThrows(SqlGenerationException.class,
            () -> noKeyService.generateSql("问", "CREATE TABLE t (c INT);"));
    }
}
