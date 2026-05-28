# OpenTelemetry Guide — HireAI

## Architecture

```
Spring Boot App
      │
      │  OTLP/gRPC (port 4317)
      ▼
OTel Collector  ──────────────────────────────────────────────────────────
      │                                                                    │
      │  OTLP/gRPC (port 4317)          Prometheus scrape (port 8889)     │
      ▼                                          ▼                        │
   Jaeger                               /actuator/prometheus              │
  (port 16686)                         (your app's Micrometer metrics)   │
```

---

## 1. Start the Observability Stack

```bash
# Start everything including Jaeger + OTel Collector
docker compose up -d

# Or just the observability stack + infra (run the app locally)
docker compose up -d postgres redis rabbitmq jaeger otel-collector
```

Open these URLs:
| URL | What it is |
|-----|-----------|
| http://localhost:16686 | **Jaeger UI** — search and visualize traces |
| http://localhost:8080/actuator/prometheus | **Prometheus metrics** — raw metric text |
| http://localhost:8080/actuator/health | Health check (includes circuit breaker state) |
| http://localhost:15672 | RabbitMQ Management UI (hireai / hireai_secret) |

---

## 2. Run the App Locally (with OTel)

```bash
export OPENAI_API_KEY=your-key-here
./mvnw spring-boot:run
```

You'll see in the logs:
```
[traceId=  spanId=] ... Started HireaiApplication in 4.2 seconds
```
The traceId/spanId fields are empty at startup — they populate on each request.

---

## 3. Generate Traces — Step by Step

### Test 1: Simple HTTP trace (no AI)

```bash
# Register a recruiter
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"recruiter@test.com","password":"Test@123","fullName":"Test Recruiter","role":"RECRUITER"}' | jq

# Login and save the token
TOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"recruiter@test.com","password":"Test@123"}' | jq -r '.data.accessToken')

echo "Token: $TOKEN"
```

Now open Jaeger UI → Search → Service: **hireai** → Find Traces.
You'll see a trace for `POST /api/v1/auth/login` with child spans for:
- The HTTP handler
- The JDBC query (`SELECT * FROM users WHERE email = ?`)
- The Redis check (if applicable)

**What to look for:** The trace tree shows the full call chain. Click any span to see its tags.

---

### Test 2: Job creation (triggers embedding generation)

```bash
# Create a job — this calls OpenAI to generate an embedding
curl -s -X POST http://localhost:8080/api/v1/jobs \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{
    "title": "Senior Java Engineer",
    "description": "Build microservices with Spring Boot",
    "companyName": "TechCorp",
    "location": "Remote",
    "employmentType": "FULL_TIME",
    "experienceLevel": "SENIOR",
    "mustHaveSkills": "Java, Spring Boot, PostgreSQL",
    "niceToHaveSkills": "Kubernetes, Redis"
  }' | jq
```

In Jaeger, find the `POST /api/v1/jobs` trace. You'll see:
```
POST /api/v1/jobs                              ~2.5s total
├── JobService.createJob (JDBC INSERT)          ~5ms
└── ai.embedding.generate                       ~2.4s  ← your custom span!
    ├── Tag: ai.model = text-embedding-ada-002
    ├── Tag: ai.input_chars = 847
    ├── Tag: ai.result.dimensions = 1536
    └── (HTTP call to api.openai.com)           ~2.3s  ← auto-instrumented
```

---

### Test 3: Resume upload (async trace across RabbitMQ)

```bash
# Register a candidate
curl -s -X POST http://localhost:8080/api/v1/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"candidate@test.com","password":"Test@123","fullName":"Jane Dev","role":"CANDIDATE"}'

CTOKEN=$(curl -s -X POST http://localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"candidate@test.com","password":"Test@123"}' | jq -r '.data.accessToken')

# Upload a resume PDF
curl -s -X POST http://localhost:8080/api/v1/resumes/upload \
  -H "Authorization: Bearer $CTOKEN" \
  -F "file=@/path/to/your/resume.pdf" | jq
```

In Jaeger, find the `POST /api/v1/resumes/upload` trace. Then look for a SEPARATE trace
that starts from the RabbitMQ consumer — this is the async part:
```
[RabbitMQ Consumer] resume.parse              ~5s total
├── ai.resume.parse                            ~3s
│   ├── Tag: ai.model = gpt-4o-mini
│   ├── Tag: ai.input_chars = 2341
│   ├── Tag: ai.result.name = Jane Dev
│   ├── Tag: ai.result.skills_count = 12
│   └── Event: openai.request.sent / openai.response.received
├── ai.embedding.generate                      ~2s
│   └── Tag: ai.result.dimensions = 1536
└── JDBC UPDATE resumes SET embedding = ...    ~5ms
```

**Key insight:** The HTTP request returns immediately (resume saved as PENDING).
The AI processing happens asynchronously. You see TWO separate traces connected
by the RabbitMQ message. This is the power of distributed tracing.

---

### Test 4: AI match explanation (most interesting trace)

```bash
# First apply to the job (get applicationId from response)
APP_ID=$(curl -s -X POST http://localhost:8080/api/v1/applications/apply \
  -H "Authorization: Bearer $CTOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"jobId": 1, "resumeId": 1}' | jq -r '.data.id')

# Get AI match explanation
curl -s -X GET "http://localhost:8080/api/v1/match/explain/$APP_ID" \
  -H "Authorization: Bearer $TOKEN" | jq
```

In Jaeger, find `GET /api/v1/match/explain/{id}`. You'll see:
```
GET /api/v1/match/explain/1                    ~3s total
├── JDBC SELECT applications WHERE id=1        ~3ms
├── JDBC SELECT jobs WHERE id=1                ~2ms
├── JDBC SELECT resumes WHERE id=1             ~2ms
└── ai.match.explain                           ~2.8s
    ├── Tag: ai.model = gpt-4o-mini
    ├── Tag: application.id = 1
    ├── Tag: job.title = Senior Java Engineer
    ├── Tag: ai.result.score = 87
    ├── Event: openai.request.sent
    └── Event: openai.response.received
```

---

### Test 5: Observe cache behavior

```bash
# First call — cache MISS (hits pgvector)
curl -s "http://localhost:8080/api/v1/match/job/1?limit=5" \
  -H "Authorization: Bearer $TOKEN" | jq

# Second call — cache HIT (served from Redis, much faster)
curl -s "http://localhost:8080/api/v1/match/job/1?limit=5" \
  -H "Authorization: Bearer $TOKEN" | jq
```

Compare the two traces in Jaeger:
- **First call:** `GET /api/v1/match/job/1` → `vector.search.candidates` → SQL query (~50ms)
- **Second call:** `GET /api/v1/match/job/1` → Redis GET (~1ms) — NO SQL child span

This visually proves your cache is working.

---

## 4. Reading Traces in Jaeger UI

### Search page
1. Open http://localhost:16686
2. Select **Service: hireai**
3. Select **Operation** (e.g., `ai.resume.parse`) to filter by span name
4. Set a time range and click **Find Traces**

### Trace detail page
- **Timeline view:** horizontal bars = spans. Width = duration. Nesting = parent-child.
- **Span details:** click any bar to see tags, events, and logs
- **Red spans:** errors — click to see the exception
- **Tags tab:** all the custom attributes you set with `span.tag()`
- **Logs tab:** span events you set with `span.event()`

### Useful searches
| What you want to find | How |
|---|---|
| All slow AI calls | Operation: `ai.resume.parse`, Min Duration: `2s` |
| All failed spans | Tags: `error=true` |
| Specific job's traces | Tags: `job.id=1` |
| Low-scoring candidates | Tags: `ai.result.score` (look for low values) |

---

## 5. Reading Metrics

```bash
# See all custom metrics
curl -s http://localhost:8080/actuator/prometheus | grep "^ai_"

# See HTTP request metrics (auto-instrumented)
curl -s http://localhost:8080/actuator/prometheus | grep "^http_server_requests"

# See JVM metrics
curl -s http://localhost:8080/actuator/prometheus | grep "^jvm_memory"

# See circuit breaker state
curl -s http://localhost:8080/actuator/circuitbreakers | jq
```

---

## 6. Log Correlation

Every log line now includes `traceId` and `spanId`:
```
14:23:01.456 [http-nio-8080-exec-3] INFO  [traceId=4bf92f3577b34da6a3ce929d0e0e4736 spanId=00f067aa0ba902b7] c.h.s.ResumeAIService - AI: Parsing resume text (2341 chars)
```

To find all logs for a specific request:
1. Get the `traceId` from Jaeger (click any span → copy trace ID)
2. `grep "traceId=4bf92f3577b34da6a3ce929d0e0e4736" app.log`

This connects your traces to your logs without any extra tooling.

---

## 7. What Each File Does

| File | Purpose |
|---|---|
| `pom.xml` | Added OTel dependencies: tracing bridge, OTLP exporter, SDK autoconfigure |
| `application.yml` | OTel config: sampling rate, OTLP endpoint, log pattern with traceId |
| `otel-collector-config.yml` | Collector pipeline: OTLP receiver → batch → Jaeger + Prometheus |
| `docker-compose.yml` | Added `jaeger` and `otel-collector` services |
| `ResumeAIService.java` | Custom spans: `ai.resume.parse`, `ai.embedding.generate`, `ai.candidate.score`, `ai.candidate.screen` |
| `InterviewAIService.java` | Custom spans: `ai.interview.generate_questions`, `ai.interview.evaluate_answer`, `ai.interview.generate_summary` |
| `JobMatchService.java` | Custom spans: `vector.search.candidates`, `vector.search.jobs`, `ai.match.explain` |
| `ObservabilityConfig.java` | Custom Micrometer metrics: counters and timers for AI calls |
