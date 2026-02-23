# HireAI — Project Structure

This is an **AI-powered hiring platform** where recruiters post jobs, candidates upload resumes, and AI handles matching, screening, and interviews.

```
hireai_v1/
├── .env                          # Your secrets (OpenAI key) — gitignored
├── docker-compose.yml            # Spins up Postgres, Redis, RabbitMQ
├── Dockerfile                    # For containerizing the app itself
├── pom.xml                       # Maven build — Java 21, Spring Boot 3.5
└── src/main/
    ├── java/com/hireai/
    │   ├── HireaiApplication.java        # Entry point
    │   ├── ai/dto/                       # AI response models
    │   ├── config/                       # App configuration
    │   ├── controller/                   # REST API endpoints
    │   ├── domain/                       # Entities, DTOs, Enums
    │   ├── exception/                    # Error handling
    │   ├── messaging/                    # RabbitMQ async processing
    │   ├── repository/                   # Database access
    │   ├── security/                     # JWT auth
    │   ├── service/                      # Business logic
    │   └── util/                         # File storage, PDF extraction
    └── resources/
        ├── application.yml               # Base config
        ├── application-dev.yml           # Dev profile (local ports/creds)
        ├── application-prod.yml          # Prod profile (env vars)
        ├── db/migration/                 # Flyway SQL migrations
        └── prompts/                      # AI prompt templates
```

---

## Layer by Layer

### 1. `config/` — Configuration

| File | What it does |
|------|-------------|
| `SecurityConfig` | Defines which endpoints are public (`/api/v1/auth/**`) vs protected, adds JWT filter |
| `AiConfig` | Creates the `ChatClient` bean from Spring AI (connects to OpenAI) |
| `RabbitMQConfig` | Declares queues, exchanges, bindings, dead-letter queues for async events |
| `RedisConfig` | Sets up Redis cache manager with TTLs |
| `RateLimitInterceptor` | Limits API calls per user using Redis counters |
| `SwaggerConfig` | OpenAPI/Swagger docs with JWT auth header support |
| `WebMvcConfig` | Registers the rate limiter interceptor |

### 2. `domain/` — Data Models

**Entities** (database tables):
```
User ──┬── Recruiter (posts jobs)
       └── Candidate ── Resume ── (parsed by AI)
                │
                └── Application ── Interview ── Questions ── Responses
                        │
                       Job
```

| Entity | Purpose |
|--------|---------|
| `User` | Login credentials + role (RECRUITER, CANDIDATE, ADMIN) |
| `Job` | Job posting with skills, salary, experience level + **vector embedding** |
| `Candidate` | Profile linked to a User |
| `Resume` | Uploaded file + AI-extracted data (skills, education) + **vector embedding** |
| `Application` | A candidate applying to a job, with AI match score |
| `Interview` | AI-generated interview for an application |
| `InterviewQuestion` | Individual questions categorized by type |
| `InterviewResponse` | Candidate's answers + AI scoring/feedback |

**Enums**: `UserRole`, `JobStatus` (DRAFT/OPEN/CLOSED), `ApplicationStatus` (APPLIED/SCREENING/SHORTLISTED/REJECTED), `InterviewStatus`, `ParseStatus`, etc.

**DTOs**: Request objects (login, job creation) and Response objects (API responses) — keeps entities separate from the API layer.

### 3. `controller/` — REST API (`/api/v1/...`)

| Controller | Endpoints | Who uses it |
|-----------|-----------|-------------|
| `AuthController` | `/auth/register`, `/auth/login`, `/auth/refresh` | Everyone |
| `JobController` | CRUD for jobs | Recruiters |
| `CandidateController` | Profile management | Candidates |
| `ResumeController` | Upload resume, check parse status | Candidates |
| `ApplicationController` | Apply to jobs, update status | Both |
| `InterviewController` | Start interview, submit answers, get results | Both |
| `MatchController` | Top candidates for a job, recommended jobs for candidate | Both |
| `DashboardController` | Stats and analytics | Both |

### 4. `service/` — Business Logic

| Service | What it does |
|---------|-------------|
| `AuthService` | Register/login, JWT token generation |
| `JobService` | Create/update jobs, generates vector embeddings for semantic search |
| `CandidateService` | Profile CRUD |
| `ResumeService` | Stores file, extracts text, publishes parse event to RabbitMQ |
| `ResumeAIService` | **Calls OpenAI** to parse resumes, score candidates, screen applications |
| `InterviewService` | Creates interviews, manages Q&A flow |
| `InterviewAIService` | **Calls OpenAI** to generate questions and evaluate answers |
| `JobMatchService` | **Vector similarity search** (pgvector) + AI match explanations |
| `ApplicationService` | Apply to job, triggers async screening |
| `DashboardService` | Aggregated stats for recruiters/candidates |

### 5. `messaging/` — Async Processing (RabbitMQ)

This is the event-driven backbone. Heavy AI work runs **asynchronously** so API calls return fast.

```
API Request
    │
    ▼
  Service ──publish──▶ RabbitMQ Queue ──consume──▶ Consumer ──▶ AI Service
                                                                    │
                                                              Updates DB
```

| Event → Queue → Consumer | What happens |
|--------------------------|-------------|
| `ResumeParseEvent` → `resume.parse` → `ResumeParseConsumer` | AI extracts skills, education, experience from resume text |
| `CandidateScoreEvent` → `candidate.score` → `CandidateScoreConsumer` | AI scores candidate against a specific job |
| `ApplicationScreenEvent` → `application.screen` → `ApplicationScreenConsumer` | AI screens application (pass/fail + notes) |
| `InterviewEvalEvent` → `interview.evaluate` → `InterviewEvalConsumer` | AI evaluates candidate's interview answer |
| `NotificationEvent` → `notification` → `NotificationConsumer` | Logs notifications (email integration placeholder) |

Each queue has a **dead-letter queue (DLQ)** — failed messages go there instead of being lost.

### 6. `security/` — JWT Authentication

```
Request → JwtAuthenticationFilter → checks Bearer token → sets SecurityContext
```

| File | Role |
|------|------|
| `JwtTokenProvider` | Creates & validates JWT access/refresh tokens |
| `JwtAuthenticationFilter` | Intercepts every request, extracts and verifies token |
| `CustomUserDetailsService` | Loads user from DB for Spring Security |
| `SecurityUtils` | Helper to get current authenticated user |

### 7. `ai/dto/` — AI Response Models

These are the structured outputs that OpenAI returns:

| DTO | Used by |
|-----|---------|
| `ParsedResume` | Resume parsing — name, skills, experience, education, summary |
| `CandidateScore` | Scoring — numeric score + matched/missing skills + reasoning |
| `ScreeningResult` | Screening — pass/fail + strengths + concerns |
| `InterviewQuestions` | Question generation — list of categorized questions |
| `AnswerEvaluation` | Answer scoring — score + feedback per answer |
| `InterviewSummary` | Overall interview summary + recommendation |
| `MatchExplanation` | Why a candidate matches a job |

### 8. `resources/prompts/` — AI Prompt Templates

StringTemplate (`.st`) files that get filled with data and sent to GPT-4o-mini:

| Prompt | Purpose |
|--------|---------|
| `resume-parse.st` | "Extract structured data from this resume text..." |
| `resume-score.st` | "Score this candidate against this job..." |
| `screening-check.st` | "Should this candidate pass initial screening..." |
| `interview-generate.st` | "Generate interview questions for this role..." |
| `interview-evaluate.st` | "Evaluate this answer..." |
| `interview-summary.st` | "Summarize the overall interview performance..." |
| `match-explain.st` | "Explain why this candidate matches this job..." |

### 9. `resources/db/migration/` — Database Schema (Flyway)

| Migration | What it does |
|-----------|-------------|
| `V1__init_schema.sql` | Creates all 8 tables (users, jobs, candidates, resumes, applications, interviews, questions, responses) |
| `V2__add_vector_extension.sql` | Enables `pgvector`, adds 1536-dimension embedding columns to jobs & resumes |
| `V3__add_indexes.sql` | Adds indexes for performance + IVFFlat vector indexes for similarity search |

---

## Infrastructure

### What runs in Docker (docker-compose)

| Service | Image | Port | Purpose |
|---------|-------|------|---------|
| PostgreSQL | `pgvector/pgvector:pg16` | 5433 | Database with vector search |
| Redis | `redis:7-alpine` | 6379 | Caching + rate limiting |
| RabbitMQ | `rabbitmq:3-management-alpine` | 5672 / 15672 (UI) | Async message processing |

### How to start

```bash
# 1. Start infrastructure
docker-compose up -d postgres redis rabbitmq

# 2. Start the app
source .env && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

### Useful URLs

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator Health | http://localhost:8080/actuator/health |
| RabbitMQ Management | http://localhost:15672 (hireai/hireai_secret) |

---

## The Full Flow (Example)

```
1. Recruiter registers        → POST /api/v1/auth/register
2. Recruiter posts job         → POST /api/v1/jobs
   └─ Job embedding generated (OpenAI) and stored in pgvector

3. Candidate registers         → POST /api/v1/auth/register
4. Candidate uploads resume    → POST /api/v1/resumes/upload
   └─ Text extracted (PDFBox/POI)
   └─ ResumeParseEvent → RabbitMQ → AI parses → skills/education saved
   └─ Resume embedding generated and stored in pgvector

5. Candidate applies           → POST /api/v1/applications
   └─ ApplicationScreenEvent → RabbitMQ → AI screens → pass/fail
   └─ CandidateScoreEvent → RabbitMQ → AI scores match

6. Recruiter views top candidates → GET /api/v1/match/jobs/{id}/candidates
   └─ pgvector cosine similarity search

7. Interview started           → POST /api/v1/interviews/start
   └─ AI generates tailored questions

8. Candidate answers           → POST /api/v1/interviews/{id}/answer
   └─ InterviewEvalEvent → RabbitMQ → AI evaluates each answer

9. Interview completed         → AI generates summary + recommendation
```
