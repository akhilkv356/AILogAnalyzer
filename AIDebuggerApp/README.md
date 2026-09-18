# AI Debugger: incident ingestion and AI suggestions

Java 21 / Spring Boot application for the hackathon. The transaction application
POSTs an error; AI Debugger saves it immediately. A separate request asks Azure's
Responses API for a suggestion and saves the analysis in a second table.
No project files are modified, and no PRs or deployments are performed.

## Setup

Run commands from the directory containing `pom.xml` and `mvnw.cmd`.
Set `JAVA_HOME` to your installed JDK 21 directory if necessary.

### Azure SQL (default)

Supply credentials through environment variables or the IDE run configuration:

```powershell
$env:DB_USERNAME = "<database-user>"
$env:DB_PASSWORD = "<database-password>"
# Optional: override the configured hackathon Azure SQL connection string.
# $env:DB_URL = "<jdbc:sqlserver connection string>"
.\mvnw.cmd spring-boot:run
```

The app listens on port 8081 (`SERVER_PORT` overrides it). The API key is not
required for incident ingestion. The previously embedded database credential
has been removed from configuration; rotate that credential if it was shared
or committed. Never put real credentials into this README or source files.

### Local database instead

```powershell
$env:SPRING_PROFILES_ACTIVE = "h2"
.\mvnw.cmd spring-boot:run
```

This uses file-backed H2 at `.\data\ai-debugger`. Set `H2_URL` / `H2_PASSWORD`
to override. Do not use the `test` profile with data you want to keep.
The legacy Oracle configuration is not part of this SQL Server/H2 demo and
requires a separately installed Oracle JDBC dependency and database setup.

### Enable Azure analysis

Set these before launching the app, in the same terminal or IDE run configuration:

```powershell
$env:AI_ENABLED = "true"
$env:AI_API_KEY = "<organization-api-key>"
$env:AI_MODEL = "gpt-6-astra"
$env:AI_RESPONSES_URL = "https://ilb-3790-team11aifoundry.services.ai.azure.com/openai/v1/responses"
```

`AI_MODEL` must be the actual deployment name available at this endpoint.
The client uses the Azure `api-key` header, the v1 Responses API, `store: false`,
and strict JSON-schema output through `text.format`. There is no
chat-completions request or `choices[0]` parser.
The deployment must support Responses and structured outputs; incompatible
responses are recorded as failures, never as successful suggestions.

AI is disabled by default. Configure it only for approved, non-sensitive demo
inputs. Do not submit proprietary source code, credentials, or personal data.
The client masks common password/token patterns, emails, private keys, and URL
credentials, but this is defense in depth, not a complete sensitive-data detector.
Redact in the transaction application **before storage** as well.
`store: false` does not override the provider's organizational logging policies.

Optional limits:

| Variable | Default |
| --- | --- |
| `AI_CONNECT_TIMEOUT_SECONDS` | 10 |
| `AI_READ_TIMEOUT_SECONDS` | 120 |
| `AI_MAX_INPUT_CHARS` | 16000 |
| `AI_MAX_OUTPUT_TOKENS` | 4000 |

Input is redacted before truncation. If too long, it is cut to the configured
limit with an explicit truncation marker. Suggestions from truncated input may
miss important cause-chain information. Provider responses, prompts, and keys
are not logged by application code; do not enable HTTP wire logging with secrets.

## Database structure

| Table | Purpose |
| --- | --- |
| `DEBUG_INCIDENTS` | Original exception, message, stack trace, application, request path, UTC receipt time |
| `INCIDENT_ANALYSES` | Incident FK, model, status, timestamps, suggestion fields or sanitized failure reason |

Each incident can have multiple analysis attempts. Suggestions include probable
root cause, suggested fix, affected file/class/method/line, and optional example code.
The incident is not duplicated or overwritten when analysis runs.

For this demo, Hibernate `ddl-auto: update` creates the analysis table and adds
incident metadata on application startup. The database user needs DDL permissions.
An optional SQL Server setup script is available at
`src\main\resources\db\sqlserver-setup.sql`; it is not executed automatically.
Back up the database and review it before running manually. If using managed
schema changes, apply the script and set `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`.
The script assumes the current incident schema and the default `dbo` schema.

Existing suggestion columns in `DEBUG_INCIDENTS` are deliberately **not dropped**
by either approach. The application no longer maps or writes them. Any older
suggestions remain in those legacy columns and are not automatically imported
into the new history API. Fresh databases have only the incident fields in table 1.
Existing incident IDs and rows are retained; added metadata may be null on old rows.

## Demo workflow

### 1. Save an error (no LLM call)

```powershell
$base = "http://localhost:8081"
$body = @{
    applicationName = "transaction-app"
    exceptionType = "java.lang.ArithmeticException"
    message = "/ by zero"
    stackTrace = "java.lang.ArithmeticException: / by zero`n`t at com.example.TransactionService.calculateAverage(TransactionService.java:42)"
    requestPath = "/transactions"
} | ConvertTo-Json

$incident = Invoke-RestMethod -Method Post -Uri "$base/api/incidents" `
    -ContentType "application/json" -Body $body
$incident
```

Returns **201 Created** after saving:

```json
{
  "id": 1,
  "applicationName": "transaction-app",
  "receivedAt": "2026-09-18T10:00:00Z"
}
```

Both `message` and the legacy name `errorMessage` are accepted; send only one.
All fields except `requestPath` are required and nonblank.
Maximum lengths: application 100, exception 500, message 10000,
stack trace 100000, request path 2048.
Invalid fields or malformed JSON return 400.

### 2. Analyze the saved incident

```powershell
$analysis = Invoke-RestMethod -Method Post `
    -Uri "$base/api/incidents/$($incident.id)/analyze"
$analysis | ConvertTo-Json -Depth 6
```

This endpoint waits for the LLM response. It is intentionally a manual,
synchronous trigger, not an automatic background job.
The incident-ingestion request remains independent and fast.

Illustrative response (**201 Created**):

```json
{
  "id": 10,
  "incidentId": 1,
  "status": "SUCCEEDED",
  "model": "gpt-6-astra",
  "startedAt": "2026-09-18T10:01:00Z",
  "completedAt": "2026-09-18T10:01:05Z",
  "failureReason": null,
  "suggestion": {
    "rootCause": "Probable zero divisor; source code is needed to confirm.",
    "suggestedFix": "Validate the divisor and confirm the intended zero-count behavior.",
    "affectedFile": "TransactionService.java",
    "affectedClass": "com.example.TransactionService",
    "affectedMethod": "calculateAverage",
    "affectedLine": 42,
    "exampleCode": null
  }
}
```

Unknown locations and unavailable example code are null. This is AI advice based
on stack traces, not a verified fix. No source repository is provided to the model.
Review suggestions before acting on them and render their text as untrusted content.

### 3. Read the error and its analysis history

```powershell
Invoke-RestMethod "$base/api/incidents/$($incident.id)"
Invoke-RestMethod "$base/api/incidents/$($incident.id)/analyses?page=0&size=20" |
    ConvertTo-Json -Depth 8
```

History is newest-first and paginated (`size` 1-100). Calling analyze again
after completion creates a new attempt, including after failure.

| Result | HTTP status |
| --- | --- |
| Saved incident or successful analysis | 201 |
| Invalid request | 400 |
| Unknown incident | 404 |
| Another analysis for this incident is running | 409 |
| Provider failure, timeout, refusal, incomplete or invalid output | 502, with persisted `FAILED` attempt |
| AI disabled or key/endpoint not configured correctly | 503, no attempt created |

Analysis start and completion are committed in separate short transactions.
A database row lock prevents duplicate active requests across app instances.
The LLM network call holds no database transaction. Errors from persistence
are surfaced, not converted into successful results.
There are no automatic LLM retries, avoiding hidden duplicate calls and charges.

If the process stops during a call or the final database update fails, an attempt
may remain `ANALYZING`. Before manually marking it `FAILED` in the database,
an operator must confirm no worker is still running. Retrying then creates a new
attempt. Durable queues, automatic crash recovery, authentication, rate limiting,
and deployment automation are outside this initial hackathon implementation.
Keep this API on a trusted private network; do not expose it publicly.

## Code map

| Class | Responsibility |
| --- | --- |
| `IncidentService` | Persist and retrieve original errors |
| `AiConfiguration` / `AiProperties` | Azure endpoint, credentials and HTTP timeouts |
| `IncidentRedactor` | Defense-in-depth masking before outbound requests |
| `AiSuggestionService` | Responses request, response parsing and field validation |
| `IncidentAnalysisService` | Orchestrate analysis outside DB transactions |
| `AnalysisStore` | Transactional start, success, failure and history |
| `IncidentAnalysis` | Separate analysis table |

## Automated checks

```powershell
.\mvnw.cmd test
```

Uses an isolated in-memory H2 database and mocked Responses API. No live Azure
requests or SQL Server connections are made. Coverage includes ingestion aliases
and validation, two-table persistence, structured requests, secret masking,
repeated attempts, failure retention, refusals, invalid output, disabled AI,
duplicate active requests, pagination, and absence of a transaction during AI calls.
Live deployment permissions, model availability, and Azure SQL migration require
your local credentials and approved environment.
