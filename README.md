# QueryPilot v1.0.0

Enterprise-grade Text-to-SQL middleware powered by **DeepSeek V3** via **Spring AI**. QueryPilot empowers non-technical users to query databases using natural language, with built-in RBAC security and SQL safety guardrails.

## Features

- **Multi-Source Connectivity** -- Dynamically connect to MySQL, PostgreSQL, and H2 databases
- **DeepSeek AI Integration** -- Natural language to SQL via Spring AI + DeepSeek V3
- **RBAC Permission System** -- Fine-grained access control (ADMIN / ANALYST roles)
- **SQL Safety Guardrails** -- JSqlParser-based enforcement: only SELECT queries pass through
- **AES-256 Encryption** -- Database credentials encrypted at rest
- **Schema Caching** -- Extracted DDL cached to reduce AI token usage

## Tech Stack

| Component       | Technology                             |
|-----------------|----------------------------------------|
| Language        | Java 17+                               |
| Framework       | Spring Boot 3.3.6                      |
| AI Integration  | Spring AI (OpenAI client for DeepSeek) |
| Internal DB     | H2 (embedded)                          |
| Target DBs      | MySQL, PostgreSQL, H2                  |
| SQL Parsing     | JSqlParser 5.0                         |
| Security        | Spring Security + JWT (JJWT)           |
| Connection Pool | HikariCP                               |
| Frontend        | React 18 + TypeScript + Ant Design 5   |
| Build Tool (FE) | Vite 6                                 |
| HTTP Client     | Axios                                  |

## Prerequisites

- Java 17 or later (tested with Java 21)
- Maven 3.9+
- Node.js 18+ and npm (only needed if rebuilding the frontend)
- A DeepSeek API key (obtain from [https://platform.deepseek.com](https://platform.deepseek.com))

## Environment Variables

| Variable         | Required | Default                                   | Description                              |
|------------------|----------|-------------------------------------------|------------------------------------------|
| `DEEPSEEK_KEY`   | Yes      | `sk-placeholder`                          | DeepSeek API key                         |
| `JWT_SECRET`     | No       | Dev default (64+ char string)             | HMAC-SHA256 signing key for JWT tokens   |
| `ENCRYPTION_KEY` | No       | Dev default (32 char string)              | AES-256 key for encrypting DB passwords  |

## Quick Start

### Step 1 -- Set the DeepSeek API key

The AI query feature uses DeepSeek V3. Export your API key before starting:

```bash
export DEEPSEEK_KEY=your-deepseek-api-key
```

Without this, the app starts but natural language queries will fail. Login, connections, and permissions still work without it.

For production, also set:

```bash
export JWT_SECRET=YourProductionSecretKeyThatIsAtLeast256BitsLong
export ENCRYPTION_KEY=YourProductionEncKey32CharsLong!
```

### Step 2 -- Build and run

```bash
git clone <repository-url>
cd QueryPolit
mvn spring-boot:run
```

Or build a JAR and run it:

```bash
mvn clean package -DskipTests
java -jar target/querypilot-1.0.0.jar
```

The server starts on **http://localhost:8080** serving the web UI.

### Step 3 -- Register an account

Open **http://localhost:8080** in your browser. You will see the login page.

1. Click the **Register** tab
2. Create a new account (e.g. username `myadmin`, password `mypassword`)
3. New accounts are assigned the **ANALYST** role by default

### Step 4 -- Promote to ADMIN (if needed)

To access the Admin Dashboard (managing connections and permissions), you need an ADMIN account. Open the H2 console at **http://localhost:8080/h2-console** and connect with:

- **JDBC URL**: `jdbc:h2:file:./data/querypilot;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- **Username**: `sa`
- **Password**: *(leave empty)*

Then run:

```sql
SELECT * FROM APP_USER;
```

Find your username and run:

```sql
UPDATE APP_USER SET role = 'ADMIN' WHERE username = 'myadmin';
```

Log out and log back in. You should now see the **Admin Dashboard** in the sidebar.

### Step 5 -- Add a database connection

Go to **Admin Dashboard** > **Connections** tab > **Add Connection**.

**Option A: Connect to an external database (MySQL / PostgreSQL)**

| Field    | Example value                              |
|----------|--------------------------------------------|
| Name     | `Sales DB`                                 |
| Type     | `MYSQL`                                    |
| JDBC URL | `jdbc:mysql://localhost:3306/sales`         |
| Username | `readonly_user`                            |
| Password | `db_password`                              |

Make sure the database server is running and reachable before clicking Create.

**Option B: Use a file-based H2 database for testing**

This requires no external database server:

| Field    | Value                                                            |
|----------|------------------------------------------------------------------|
| Name     | `Test H2 DB`                                                     |
| Type     | `H2`                                                             |
| JDBC URL | `jdbc:h2:file:./data/testdb;DB_CLOSE_DELAY=-1;AUTO_SERVER=TRUE` |
| Username | `sa`                                                             |
| Password | `sa`                                                             |

> **Important**: Do NOT use `jdbc:h2:mem:...` (in-memory). In-memory databases are empty and don't persist across restarts. Always use `jdbc:h2:file:...` for test connections.

### Step 6 -- Create sample tables (H2 test database only)

If you used Option B above, the test database is empty. Open the H2 console at **http://localhost:8080/h2-console** and connect to:

- **JDBC URL**: `jdbc:h2:file:./data/testdb;AUTO_SERVER=TRUE`
- **Username**: `sa`
- **Password**: `sa`

Run the following SQL to create sample tables with data:

```sql
CREATE TABLE IF NOT EXISTS users (
    id INT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100),
    email VARCHAR(200),
    registration_date DATE
);

INSERT INTO users (name, email, registration_date) VALUES
('Alice', 'alice@example.com', CURRENT_DATE - 1),
('Bob', 'bob@example.com', CURRENT_DATE - 3),
('Charlie', 'charlie@example.com', CURRENT_DATE - 10),
('Diana', 'diana@example.com', CURRENT_DATE - 30);

CREATE TABLE IF NOT EXISTS orders (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_id INT,
    product VARCHAR(100),
    amount DECIMAL(10,2),
    order_date DATE,
    FOREIGN KEY (user_id) REFERENCES users(id)
);

INSERT INTO orders (user_id, product, amount, order_date) VALUES
(1, 'Laptop', 999.99, CURRENT_DATE - 1),
(2, 'Phone', 599.00, CURRENT_DATE - 2),
(1, 'Keyboard', 79.99, CURRENT_DATE - 5),
(3, 'Monitor', 349.00, CURRENT_DATE - 8);
```

### Step 7 -- Refresh the schema

Go back to **Admin Dashboard** > **Connections** tab and click the **refresh** button on your connection. This extracts the schema DDL so the AI knows the table and column names when generating SQL.

### Step 8 -- Ask a query

1. Go to **Query Playground** (default page after login)
2. Select your database connection from the dropdown
3. Type a natural language question, e.g. *"Show me all users who registered in the last 7 days"*
4. Click **Ask QueryPilot** (or press Ctrl+Enter)
5. The AI generates SQL, the safety sanitizer validates it, and the results appear in a table

### Step 9 -- Grant permissions to analysts (optional)

If you want other users (ANALYST role) to query a connection:

1. Go to **Admin Dashboard** > **Permissions** tab
2. Click **Grant Permission**
3. Enter the analyst's **User ID** (find it via `SELECT * FROM APP_USER` in the H2 console) and select a **Connection**
4. Click **Grant**

## API Reference

### Authentication

#### Register a new user

```
POST /api/v1/auth/register
Content-Type: application/json

{
  "username": "analyst1",
  "password": "securePassword"
}
```

New users are assigned the `ANALYST` role by default.

#### Login

```
POST /api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

Response:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "username": "admin",
  "role": "ADMIN"
}
```

### Admin: Connection Management (requires ADMIN role)

#### Add a database connection

```
POST /api/v1/admin/connections
Authorization: Bearer <token>
Content-Type: application/json

{
  "name": "Sales DB",
  "type": "MYSQL",
  "url": "jdbc:mysql://localhost:3306/sales",
  "username": "readonly_user",
  "password": "db_password"
}
```

Supported types: `MYSQL`, `POSTGRESQL`, `H2`

#### List all connections

```
GET /api/v1/admin/connections
Authorization: Bearer <token>
```

#### Get a single connection

```
GET /api/v1/admin/connections/{id}
Authorization: Bearer <token>
```

#### Delete a connection

```
DELETE /api/v1/admin/connections/{id}
Authorization: Bearer <token>
```

#### Refresh schema cache

```
POST /api/v1/admin/connections/{id}/refresh-schema
Authorization: Bearer <token>
```

### Admin: Permission Management (requires ADMIN role)

#### Grant permission

```
POST /api/v1/admin/permissions
Authorization: Bearer <token>
Content-Type: application/json

{
  "userId": 2,
  "connectionId": 1
}
```

#### Revoke permission

```
DELETE /api/v1/admin/permissions?userId=2&connectionId=1
Authorization: Bearer <token>
```

#### List permissions by user

```
GET /api/v1/admin/permissions/user/{userId}
Authorization: Bearer <token>
```

#### List permissions by connection

```
GET /api/v1/admin/permissions/connection/{connectionId}
Authorization: Bearer <token>
```

### Core: Natural Language Query

#### Ask a question

```
POST /api/v1/query/ask
Authorization: Bearer <token>
Content-Type: application/json

{
  "connectionId": 1,
  "question": "Show me the top 3 selling products from last month"
}
```

Response:
```json
{
  "sql": "SELECT name, SUM(sales) as total FROM products WHERE month = ... ORDER BY total DESC LIMIT 3",
  "result": [
    {"name": "Widget A", "total": 1500},
    {"name": "Widget B", "total": 1200},
    {"name": "Widget C", "total": 900}
  ],
  "safetyCheck": "PASSED"
}
```

## H2 Console (Development)

The H2 database console is available at **http://localhost:8080/h2-console** with:
- JDBC URL: `jdbc:h2:file:./data/querypilot;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE`
- Username: `sa`
- Password: *(empty)*

## Project Structure

```
QueryPolit/
  pom.xml                              -- Maven build configuration
  frontend/                            -- React SPA (Vite + TypeScript + Ant Design 5)
    package.json                       -- npm dependencies
    vite.config.ts                     -- Vite config (dev proxy + build output to static/)
    src/
      main.tsx                         -- Entry point, React Router setup
      api/client.ts                    -- Axios HTTP client with JWT interceptor
      contexts/AuthContext.tsx          -- Auth state management (token, user, role)
      components/
        AppLayout.tsx                  -- Sidebar + header layout
        ProtectedRoute.tsx             -- Auth & role route guard
      pages/
        LoginPage.tsx                  -- Login / Register page
        QueryPage.tsx                  -- Query Playground (main feature)
        AdminPage.tsx                  -- Admin Dashboard (connections + permissions)
  src/main/java/com/querypilot/
    QueryPilotApplication.java         -- Application entry point
    config/
      SecurityConfig.java              -- Spring Security + JWT configuration
      WebConfig.java                   -- SPA fallback routing
    security/
      JwtTokenProvider.java            -- JWT generation & validation
      JwtAuthenticationFilter.java     -- Request filter for JWT extraction
      UserDetailsServiceImpl.java      -- User loading from internal DB
    model/
      entity/                          -- JPA entities (User, DataSourceConfig, Permission)
      enums/                           -- DatabaseType, UserRole
      dto/                             -- Request/Response DTOs
    repository/                        -- Spring Data JPA repositories
    service/
      AiQueryService.java              -- Query orchestration pipeline
      SqlSanitizer.java                -- SQL safety enforcement (JSqlParser)
      DynamicConnectionFactory.java    -- Dynamic HikariCP DataSource management
      ConnectionManagerService.java    -- Connection CRUD + schema extraction
      PermissionService.java           -- RBAC access validation
      SchemaExtractorService.java      -- information_schema DDL extraction
      EncryptionService.java           -- AES-256-CBC encryption
      AuthService.java                 -- User registration & login
    controller/
      AuthController.java              -- /api/v1/auth endpoints
      AdminConnectionController.java   -- /api/v1/admin/connections endpoints
      AdminPermissionController.java   -- /api/v1/admin/permissions endpoints
      QueryController.java             -- /api/v1/query endpoints
    exception/
      GlobalExceptionHandler.java      -- Centralized error handling
      SqlSafetyException.java          -- SQL guardrail violation
      AccessDeniedException.java       -- Permission denied
  src/main/resources/
    application.yml                    -- Application configuration
    data.sql                           -- Seed default admin user
    static/                            -- Built frontend assets (generated by Vite)
```

## Frontend Development

The frontend is a React SPA located in `frontend/`. The production build is output to `src/main/resources/static/` and served by Spring Boot.

### Rebuild the frontend

```bash
cd frontend
npm install
npm run build
```

Then restart Spring Boot to serve the updated assets.

### Development mode (hot reload)

Run the Vite dev server alongside Spring Boot for live reloading during frontend development:

```bash
# Terminal 1: Start the backend
mvn spring-boot:run

# Terminal 2: Start the frontend dev server
cd frontend
npm run dev
```

The Vite dev server runs on **http://localhost:5173** and proxies API requests (`/api/*`) to the Spring Boot backend on port 8080.

## Testing

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=SqlSanitizerTest
mvn test -Dtest=AiQueryServiceTest
mvn test -Dtest=QueryControllerTest
```

## Security Notes

1. **SQL Guardrails**: All AI-generated SQL is parsed through JSqlParser. Only `SELECT` statements are permitted. `DELETE`, `UPDATE`, `INSERT`, `DROP`, `TRUNCATE`, `ALTER`, `GRANT`, `EXECUTE`, and `CREATE` are blocked.
2. **Automatic LIMIT**: Queries without a `LIMIT` clause get `LIMIT 100` appended automatically.
3. **Comment Stripping**: SQL comments (`--` and `/* */`) are stripped before parsing to prevent injection hiding.
4. **Credential Encryption**: Database passwords are encrypted with AES-256-CBC before storage.
5. **Recommended**: Configure target database credentials with SELECT-only privileges.

## License

Proprietary -- Internal Enterprise Use Only
