# Product Requirement Document: QueryPilot ✈️

> **Project Name**: QueryPilot (Enterprise Edition)
> **Version**: 1.0.0
> **Status**: Approved for Development
> **Type**: Java Backend / AI-Native SaaS Engine
> **Core Engine**: Spring AI + DeepSeek V3

## 1. Project Vision & Scope

**QueryPilot** is an enterprise-grade "Text-to-SQL" middleware. It empowers non-technical users (Business Analysts, PMs, Ops) to query disparate internal databases using natural language.

Unlike simple demos, QueryPilot v1.0.0 is built with **Security** and **Multi-Tenancy** at its core. It acts as a secure proxy between the user and the raw database, ensuring that AI agents never hallucinate destructive commands and that users only access data they are permitted to see.

### 1.1 Key Features (v1.0.0)

1. **Multi-Source Connectivity**: Dynamically connect to and query multiple distinct databases (MySQL, PostgreSQL, H2) via API configuration.
2. **DeepSeek Powered**: Utilizes DeepSeek-V3 (via Spring AI) as the default inference engine for high accuracy and low cost.
3. **RBAC Permission System**: Fine-grained access control to determine *who* can query *which* database.
4. **SQL Guardrails**: A strict "Read-Only" enforcement layer that mathematically prevents `DROP`, `UPDATE`, or `DELETE` operations.

---

## 2. Technical Architecture

### 2.1 Tech Stack

* **Language**: Java 17+ (LTS).
* **Framework**: Spring Boot 3.3.x.
* **AI Integration**: **Spring AI** (OpenAI Client configured for DeepSeek API).
* **Internal DB**: H2 (embedded) or PostgreSQL (for storing QueryPilot's own user/connection config).
* **Target DB Support**: MySQL, PostgreSQL, H2 (Dynamic DataSource routing).
* **SQL Parsing**: `JSqlParser` (for robust Abstract Syntax Tree analysis).
* **Security**: Spring Security (JWT or Basic Auth).

### 2.2 System Context Diagram

---

## 3. Functional Requirements

### 3.1 Module A: Connection Manager (Multi-DB Support)

**Goal**: Manage metadata for external database connections dynamically.

* **Data Model (`DataSourceConfig`)**:
* `id` (Long): Unique ID.
* `name` (String): Friendly name (e.g., "Sales DB Production").
* `type` (Enum): MYSQL, POSTGRESQL.
* `url`, `username`, `password` (Encrypted).


* **Functionality**:
* Admins can add/remove database connections via API.
* The system must maintain a pool of `DataSource` objects or create them on demand using `HikariCP`.


* **Schema Extraction**:
* When a connection is added, QueryPilot must scan the `information_schema` and cache the table structure (DDL) locally to save context window tokens later.



### 3.2 Module B: Identity & Access Control (RBAC)

**Goal**: Ensure users can only query databases they are authorized to access.

* **Data Model**:
* `User`: `id`, `username`, `role` (ADMIN, ANALYST).
* `Permission`: `user_id`, `connection_id` (Many-to-Many mapping).


* **Logic**:
* **ADMIN**: Can manage connections and query any DB.
* **ANALYST**: Can only query databases linked in the `Permission` table.
* *Interceptor*: Before processing any NLP query, check: `Does User(X) have permission for Connection(Y)?` If no, return `403 Forbidden`.



### 3.3 Module C: The DeepSeek Inference Engine

**Goal**: Convert Natural Language to SQL using DeepSeek-V3.

* **Configuration**:
* Spring AI must be configured with `base-url: https://api.deepseek.com` and `model: deepseek-chat`.


* **Prompt Strategy (RAG-Lite)**:
* Retrieve the cached Schema DDL for the specific target connection.
* **System Prompt**:
> "You are a SQL expert using {Dialect}.
> Here is the schema: {Schema_DDL}.
> Generate a single executable SQL query for the user question.
> Do NOT allow destructive actions.
> Output JSON: { "sql": "..." }"




* **Error Handling**: If DeepSeek returns invalid SQL, the system should catch the error and return a "Clarification Request" to the user.

### 3.4 Module D: Safety Guardrails (The Sanitizer)

**Goal**: Prevent data loss or corruption via AI hallucinations or malicious prompts.

* **Mechanism**:
1. **Syntax Parsing**: Use `JSqlParser` to parse the AI-generated SQL into an AST (Abstract Syntax Tree).
2. **Statement Verification**:
* Assert `Statement` is of type `Select`.
* Reject types: `Delete`, `Update`, `Insert`, `Drop`, `Truncate`, `Alter`, `Grant`.


3. **Comment Stripping**: Remove SQL comments (`--`, `/* */`) to prevent injection hiding.
4. **Limit Enforcement**: Automatically append `LIMIT 100` if the AI didn't include a limit, to prevent crashing the application memory.



---

## 4. API Specification (RESTful)

### 4.1 Admin: Connection Management

* **POST** `/api/v1/admin/connections`
* *Payload*: `{ "name": "Prod DB", "type": "MYSQL", "url": "...", "creds": "..." }`
* *Action*: Verifies connection, extracts schema, saves config.



### 4.2 Admin: User Permissions

* **POST** `/api/v1/admin/permissions`
* *Payload*: `{ "userId": 1, "connectionId": 5 }`
* *Action*: Grants User 1 access to Database 5.



### 4.3 Core: Intelligent Query

* **POST** `/api/v1/query/ask`
* *Headers*: `Authorization: Bearer <token>`
* *Payload*:
```json
{
  "connectionId": 5,
  "question": "Show me the top 3 selling products from last month"
}

```


* *Process Flow*:
1. **Auth Check**: Is Token valid?
2. **Perm Check**: Does User have access to Connection 5?
3. **Schema Load**: Load DDL for Connection 5.
4. **AI Gen**: Call DeepSeek API with Question + Schema.
5. **Sanitize**: Pass SQL through `JSqlParser` (Block non-SELECT).
6. **Execute**: Run on Connection 5.


* *Response*:
```json
{
  "sql": "SELECT name, SUM(sales) as total FROM products...",
  "result": [ ...data... ],
  "safetyCheck": "PASSED"
}

```





---

## 5. Security & Non-Functional Requirements

1. **Credential Encryption**: Database passwords stored in QueryPilot's internal DB must be encrypted (e.g., AES-256).
2. **Read-Only Database Users**: It is *highly recommended* (though not code-enforceable) that the DB credentials provided to QueryPilot correspond to a database user with only `SELECT` privileges.
3. **Token Usage**: Implement caching for Schema DDL to avoid sending massive schemas to DeepSeek on every request (save costs).

---

## 6. Implementation Prompt for AI Developer

> **Instruction for AI Coding Assistant:**
> "Act as a Senior Backend Architect. Generate a Spring Boot 3.3 application named 'QueryPilot' based on the PRD above.
> **Key Implementation Details:**
> 1. **Dependencies**: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-security`, `spring-ai-openai-spring-boot-starter`, `jsqlparser` (com.github.jsqlparser), `h2` (internal), `mysql-connector-j` (target support).
> 2. **Config**: Configure `application.yml` for DeepSeek usage (`base-url: https://api.deepseek.com`, `api-key: ${DEEPSEEK_KEY}`).
> 3. **Dynamic DataSource**: Create a `ConnectionManager` service that can create `JdbcTemplate` instances on the fly based on the stored config in the H2 database.
> 4. **Safety**: Implement a `SqlSanitizer` class using JSqlParser. It **must** throw a `SecurityException` if the parsed SQL is not a SELECT statement.
> 5. **Permissions**: Implement a simple Interceptor or Service check: `validateAccess(userId, connectionId)`.
> 6. **Controller**: Implement the `/ask` endpoint.
> 
> 
> Please provide the project structure and the complete code for the `AiQueryService`, `SqlSanitizer`, and `DynamicConnectionFactory` classes."
