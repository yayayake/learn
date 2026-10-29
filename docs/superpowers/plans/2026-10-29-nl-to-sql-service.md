# NL→SQL 生成服务 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 构建一个无状态的 NL→SQL 生成服务：`POST /api/sql/generate` 接收 `{question, schema}`，调用 DeepSeek `deepseek-chat`，返回 `{sql}`。

**Architecture:** Spring Boot 3 Web 应用。`SqlGenerationController` 收请求，`SqlGenerationService` 用 `RestClient` 调 DeepSeek 的 OpenAI 兼容接口（`POST /chat/completions`）并解析出 SQL。schema 以 DDL 文本随请求传入，不落库、不执行、不存状态。

**Tech Stack:** Java 17、Maven 3.9、Spring Boot 3.5.x、Spring Web、RestClient、JUnit 5 + MockMvc + MockRestServiceServer。

**Spec:** `docs/superpowers/specs/2026-10-29-nl-to-sql-service-design.md`

## Global Constraints

- Java **17**（本机默认 JDK 是 17；设计文档原写 21，但机器上无 21，故改用 17——Spring Boot 3.5 基线、LTS，本任务功能无差异）。
- 构建固定用 JDK 17。本机 `mvn` 默认会解析到 Homebrew 的 JDK 25（与 Spring Boot 3.5 不兼容），故每个 `mvn` 命令前先 `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home`。
- Spring Boot parent `org.springframework.boot:spring-boot-starter-parent:3.5.4`；`<java.version>17</java.version>`。
- 包名 `com.example.learn2`；groupId `com.example`，artifactId `learn2`。
- 模型 `deepseek-chat`；base-url `https://api.deepseek.com`；API key 从环境变量 `DEEPSEEK_API_KEY` 读取（`${DEEPSEEK_API_KEY:}`），不得写入仓库。
- 返回 JSON `{ "sql": "..." }`；DDL 以请求字段 `schema` 传入。
- 不执行 SQL、不做多轮、不做鉴权、不做前端。
- 依赖仅 `spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-test`(test)。

## Review Focus

以下五类输入/失败模式最可能坑到使用者，逐条在对应任务用测试钉住：

1. DeepSeek 返回内容带 markdown 代码围栏（` ```sql ... ``` `）——用户期望裸 SQL。→ Task 2：`stripsMarkdownFencesFromResponse`。
2. DeepSeek 返回空/空白 content——应报错而非返回空 SQL。→ Task 2：`throwsWhenResponseContentIsBlank`。
3. DeepSeek 返回非 200（4xx/5xx）——对外应返回明确 502，而非裸 500 堆栈。→ Task 2：`throwsWhenDeepSeekReturnsError`（service 抛异常）+ Task 3：`returns502WhenServiceThrows`（映射 502）。
4. API key 未配置（空）——应快速失败，而非发 `Bearer ` 空头。→ Task 2：`throwsWhenApiKeyMissing`。
5. 请求缺 `question` 或 `schema`（null/空白）——应 400，而非带 null 去调 DeepSeek。→ Task 3：`returns400WhenFieldsBlank`。

---

## 文件结构

生产代码（均在 `src/main/java/com/example/learn2/` 下）：
- `Learn2Application.java` — 启动类（`@SpringBootApplication` + `@ConfigurationPropertiesScan`）
- `config/DeepSeekProperties.java` — 配置绑定 record
- `config/DeepSeekConfig.java` — `RestClient` bean
- `service/SqlGenerationException.java` — 业务异常
- `service/SqlGenerationService.java` — 调 DeepSeek、拼 prompt、解析 SQL
- `dto/SqlGenerationRequest.java` — 请求体 record
- `dto/SqlGenerationResponse.java` — 响应体 record
- `controller/SqlGenerationController.java` — REST 端点
- `controller/GlobalExceptionHandler.java` — 异常映射
- `pom.xml`、`src/main/resources/application.yml`

测试（`src/test/java/com/example/learn2/`）：
- `Learn2ApplicationTests.java`
- `service/SqlGenerationServiceTest.java`
- `controller/SqlGenerationControllerTest.java`

---

### Task 1: 项目脚手架与配置绑定

**Files:**
- Create: `pom.xml`
- Create: `src/main/java/com/example/learn2/Learn2Application.java`
- Create: `src/main/java/com/example/learn2/config/DeepSeekProperties.java`
- Create: `src/main/resources/application.yml`
- Test: `src/test/java/com/example/learn2/Learn2ApplicationTests.java`

**Interfaces:**
- Produces: `DeepSeekProperties(String baseUrl, String apiKey, String model)`，标注 `@ConfigurationProperties(prefix = "deepseek")`；经 `@ConfigurationPropertiesScan` 注册为 bean，供 Task 2/3 使用。

- [ ] **Step 1: 写失败测试 `contextLoadsAndBindsProperties`**

```java
package com.example.learn2;

import com.example.learn2.config.DeepSeekProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
class Learn2ApplicationTests {
    @Autowired DeepSeekProperties properties;

    @Test
    void contextLoadsAndBindsProperties() {
        assertEquals("https://api.deepseek.com", properties.baseUrl());
        assertEquals("deepseek-chat", properties.model());
    }
}
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test -Dtest=Learn2ApplicationTests`
Expected: 编译失败（`DeepSeekProperties` 不存在）。

- [ ] **Step 3: 实现脚手架**

`pom.xml`：parent `spring-boot-starter-parent:3.5.4`；`<java.version>17</java.version>`；依赖 `spring-boot-starter-web`、`spring-boot-starter-validation`、`spring-boot-starter-test`(scope=test)。

`Learn2Application.java`：
```java
@SpringBootApplication
@ConfigurationPropertiesScan
public class Learn2Application {
    public static void main(String[] args) {
        SpringApplication.run(Learn2Application.class, args);
    }
}
```

`DeepSeekProperties.java`：
```java
@ConfigurationProperties(prefix = "deepseek")
public record DeepSeekProperties(String baseUrl, String apiKey, String model) {}
```

`application.yml`：
```yaml
deepseek:
  base-url: https://api.deepseek.com
  api-key: ${DEEPSEEK_API_KEY:}
  model: deepseek-chat
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test -Dtest=Learn2ApplicationTests`
Expected: PASS。

- [ ] **Step 5: 提交**

```bash
git add pom.xml src/main/java/com/example/learn2/Learn2Application.java \
  src/main/java/com/example/learn2/config/DeepSeekProperties.java \
  src/main/resources/application.yml \
  src/test/java/com/example/learn2/Learn2ApplicationTests.java
git commit -m "feat: scaffold Spring Boot app with DeepSeek properties

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 2: SQL 生成服务（DeepSeek 调用 + prompt + 解析）

**Files:**
- Create: `src/main/java/com/example/learn2/service/SqlGenerationException.java`
- Create: `src/main/java/com/example/learn2/service/SqlGenerationService.java`
- Create: `src/main/java/com/example/learn2/config/DeepSeekConfig.java`
- Test: `src/test/java/com/example/learn2/service/SqlGenerationServiceTest.java`

**Interfaces:**
- Consumes: `DeepSeekProperties`（Task 1）。
- Produces: `String SqlGenerationService.generateSql(String question, String schema)`；`SqlGenerationException extends RuntimeException`（构造参数 `(String message)`、`(String message, Throwable cause)`）。供 Task 3 使用。

- [ ] **Step 1: 写失败测试**

`SqlGenerationServiceTest`（setup 用 `MockRestServiceServer.bindTo(RestClient.Builder)`）：

```java
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test -Dtest=SqlGenerationServiceTest`
Expected: 编译失败（`SqlGenerationService`、`SqlGenerationException` 不存在）。

- [ ] **Step 3: 实现 service、exception、config**

`SqlGenerationException.java`：`extends RuntimeException`，两个构造器（message；message+cause）。

`SqlGenerationService.java`：
```java
@Service
public class SqlGenerationService {
    private final RestClient restClient;
    private final DeepSeekProperties properties;
    public SqlGenerationService(RestClient deepSeekRestClient, DeepSeekProperties properties) {
        this.restClient = deepSeekRestClient;
        this.properties = properties;
    }
    public String generateSql(String question, String schema) { ... }
}
```

实现要点：
- 若 `properties.apiKey()` 为 null 或 blank，抛 `SqlGenerationException("DeepSeek API key is not configured")`。
- POST `/chat/completions`，header `Authorization: Bearer <apiKey>`，body：
  ```json
  {"model":"deepseek-chat","temperature":0,"messages":[
    {"role":"system","content":"You are a SQL expert. Given a database schema (DDL) and a natural language question, generate a single MySQL SQL statement. Return only the SQL, with no explanation and no markdown."},
    {"role":"user","content":"Schema:\n<schema>\n\nQuestion:\n<question>"}
  ]}
  ```
- 解析返回 `choices[0].message.content`；去掉 markdown 围栏（前导 ` ```sql ` 或 ` ``` ` 及结尾 ` ``` `），trim；若结果空白，抛 `SqlGenerationException`。
- 非 2xx 或解析失败，抛 `SqlGenerationException`（可包 cause）。

`DeepSeekConfig.java`：
```java
@Configuration
public class DeepSeekConfig {
    @Bean
    public RestClient deepSeekRestClient(DeepSeekProperties properties) {
        return RestClient.builder().baseUrl(properties.baseUrl()).build();
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test -Dtest=SqlGenerationServiceTest`
Expected: 5 个测试 PASS。

- [ ] **Step 5: 提交**

```bash
git add src/main/java/com/example/learn2/service/SqlGenerationException.java \
  src/main/java/com/example/learn2/service/SqlGenerationService.java \
  src/main/java/com/example/learn2/config/DeepSeekConfig.java \
  src/test/java/com/example/learn2/service/SqlGenerationServiceTest.java
git commit -m "feat: add SQL generation service calling DeepSeek

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```

---

### Task 3: 控制器 + DTO + 校验 + 异常映射

**Files:**
- Create: `src/main/java/com/example/learn2/dto/SqlGenerationRequest.java`
- Create: `src/main/java/com/example/learn2/dto/SqlGenerationResponse.java`
- Create: `src/main/java/com/example/learn2/controller/SqlGenerationController.java`
- Create: `src/main/java/com/example/learn2/controller/GlobalExceptionHandler.java`
- Test: `src/test/java/com/example/learn2/controller/SqlGenerationControllerTest.java`

**Interfaces:**
- Consumes: `SqlGenerationService.generateSql(String, String)`（Task 2）。
- Produces: `POST /api/sql/generate` → 200 `SqlGenerationResponse`；502 于 `SqlGenerationException`；400 于参数校验失败。

- [ ] **Step 1: 写失败测试**

```java
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
```

- [ ] **Step 2: 跑测试确认失败**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test -Dtest=SqlGenerationControllerTest`
Expected: 编译失败（`SqlGenerationController` 等不存在）。

- [ ] **Step 3: 实现 DTO、controller、exception handler**

`SqlGenerationRequest.java`：
```java
public record SqlGenerationRequest(@NotBlank String question, @NotBlank String schema) {}
```

`SqlGenerationResponse.java`：
```java
public record SqlGenerationResponse(String sql) {}
```

`SqlGenerationController.java`：
```java
@RestController
@RequestMapping("/api/sql")
public class SqlGenerationController {
    private final SqlGenerationService service;
    public SqlGenerationController(SqlGenerationService service) { this.service = service; }

    @PostMapping("/generate")
    public SqlGenerationResponse generate(@Valid @RequestBody SqlGenerationRequest request) {
        return new SqlGenerationResponse(service.generateSql(request.question(), request.schema()));
    }
}
```

`GlobalExceptionHandler.java`：
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(SqlGenerationException.class)
    @ResponseStatus(HttpStatus.BAD_GATEWAY)
    public Map<String, String> handleSqlGeneration(SqlGenerationException e) {
        return Map.of("error", e.getMessage() == null ? "Failed to generate SQL" : e.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleValidation(MethodArgumentNotValidException e) {
        return Map.of("error", "question and schema are required");
    }
}
```

- [ ] **Step 4: 跑测试确认通过**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test -Dtest=SqlGenerationControllerTest`
Expected: 3 个测试 PASS。

- [ ] **Step 5: 全量测试 + 提交**

Run: `export JAVA_HOME=/Users/lizhao/Library/Java/JavaVirtualMachines/ms-17.0.16/Contents/Home && mvn test`
Expected: 全部测试（Task 1/2/3）PASS。

```bash
git add src/main/java/com/example/learn2/dto/SqlGenerationRequest.java \
  src/main/java/com/example/learn2/dto/SqlGenerationResponse.java \
  src/main/java/com/example/learn2/controller/SqlGenerationController.java \
  src/main/java/com/example/learn2/controller/GlobalExceptionHandler.java \
  src/test/java/com/example/learn2/controller/SqlGenerationControllerTest.java
git commit -m "feat: expose POST /api/sql/generate endpoint

Co-Authored-By: Claude Code <noreply@anthropic.com>"
```
