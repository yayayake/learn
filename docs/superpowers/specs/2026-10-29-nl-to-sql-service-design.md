# NL→SQL 生成服务 设计

日期：2026-10-29

## 意图与背景

一个「自然语言 + 表结构 → MySQL SQL」的无状态生成服务，作为智能问答系统的后端。调用方每次请求带上问题和表结构（DDL），后端用 DeepSeek 生成 MySQL 的 SQL 文本返回。学习向项目，重点是把 Spring Boot 接 DeepSeek 的链路走通。

## 目标 / 非目标 / 反目标

- 目标：一个 HTTP 接口，输入「问题 + DDL schema」，输出 MySQL SQL 文本；能跑通、有基本测试。
- 非目标：不执行 SQL、不做多轮对话、不存状态、不做前端、不做鉴权。
- 反目标：不要把 schema 硬编码进系统（必须由请求带入）；不要引入重框架（Spring AI / LangChain4j）掩盖调用细节。

## 约束

- Java 21 + Maven + Spring Boot 3.x。
- 模型 `deepseek-chat`，走 OpenAI 兼容接口 `POST /chat/completions`。
- API key 走环境变量（如 `DEEPSEEK_API_KEY`），不进代码仓库。
- 返回 JSON `{ "sql": "..." }`。

## 方案（Approaches）

- A（已选）：纯 Spring Boot + `RestClient` 直接调 DeepSeek。透明、依赖少、易维护。
- B（未选）：Spring AI 的 OpenAI 兼容 starter。对单接口偏重、调用细节被封装。

选 A 的原因：任务小、学习向，直接调接口最清楚。

## 已定的实现细节

- 单个 REST 端点 `POST /api/sql/generate`。
- 请求体：`{ "question": "...", "schema": "CREATE TABLE ..." }`（schema 用 DDL 文本——我的默认，可改）。
- 服务层一个 `SqlGenerationService`：拼 prompt（含 schema + question，要求只返回 SQL），用 `RestClient` 调 DeepSeek，解析 `choices[0].message.content` 作为 SQL。
- 配置：base-url、model、api-key 放 `application.yml` + 环境变量。

## 留给实现者

- 具体 prompt 措辞（要求 DeepSeek 只输出 SQL，不带解释）。
- 错误处理：DeepSeek 非 200、超时、缺 API key 时的返回。
- 测试：controller/service 层单测（用 mock 的 DeepSeek 响应）。
