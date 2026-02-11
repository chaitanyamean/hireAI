# HireAI — API Testing Guide

A step-by-step guide to test every API endpoint in order. Each step builds on the previous one.

**Base URL:** `http://localhost:8080`
**Swagger UI:** `http://localhost:8080/swagger-ui.html`
**Tool:** Use Postman, cURL, or any HTTP client.

---

## Prerequisites

Make sure the app and infrastructure are running:

```bash
# Start Postgres, Redis, RabbitMQ
docker-compose up -d postgres redis rabbitmq

# Start the app
source .env && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

---

## Quick Reference — Valid Enum Values

| Enum | Values |
|------|--------|
| **UserRole** | `RECRUITER`, `CANDIDATE`, `ADMIN` |
| **JobStatus** | `DRAFT`, `ACTIVE`, `CLOSED` |
| **EmploymentType** | `FULL_TIME`, `PART_TIME`, `CONTRACT`, `REMOTE` |
| **ExperienceLevel** | `JUNIOR`, `MID`, `SENIOR`, `LEAD` |
| **ApplicationStatus** | `APPLIED`, `SCREENING`, `SHORTLISTED`, `INTERVIEW`, `OFFERED`, `REJECTED` |
| **InterviewType** | `AI_SCREENING`, `TECHNICAL`, `BEHAVIORAL` |

---

## Phase 1: Authentication

All other APIs require a JWT token. Start here.

### 1.1 Register a Recruiter

```
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "recruiter@test.com",
  "password": "password123",
  "fullName": "John Recruiter",
  "role": "RECRUITER"
}
```

**Expected:** `201 Created` with `token`, `refreshToken`, and `user` object.

> Save the `token` value — you'll use it as `RECRUITER_TOKEN` for recruiter-only APIs.

### 1.2 Register a Candidate

```
POST /api/v1/auth/register
Content-Type: application/json
```

```json
{
  "email": "candidate@test.com",
  "password": "password123",
  "fullName": "Jane Candidate",
  "role": "CANDIDATE"
}
```

**Expected:** `201 Created` with token and user.

> Save the `token` value — you'll use it as `CANDIDATE_TOKEN` for candidate-only APIs.

### 1.3 Login (test with existing user)

```
POST /api/v1/auth/login
Content-Type: application/json
```

```json
{
  "email": "recruiter@test.com",
  "password": "password123"
}
```

**Expected:** `200 OK` with new token pair.

### 1.4 Refresh Token

```
POST /api/v1/auth/refresh
Content-Type: application/json
```

```json
{
  "refreshToken": "<paste refreshToken from login/register response>"
}
```

**Expected:** `200 OK` with new access token.

### Negative Tests

| Test | Expected |
|------|----------|
| Register with duplicate email | `409 Conflict` |
| Register with invalid email format | `400 Bad Request` |
| Register with password < 8 chars | `400 Bad Request` |
| Login with wrong password | `401 Unauthorized` |
| Login with non-existent email | `401 Unauthorized` |

---

## Phase 2: Job Management (as Recruiter)

> All requests below require: `Authorization: Bearer <RECRUITER_TOKEN>`

### 2.1 Create a Job

```
POST /api/v1/jobs
Authorization: Bearer <RECRUITER_TOKEN>
Content-Type: application/json
```

```json
{
  "title": "Senior Java Developer",
  "description": "We are looking for an experienced Java developer to build microservices using Spring Boot, PostgreSQL, and RabbitMQ.",
  "companyName": "TechCorp Inc",
  "location": "San Francisco, CA",
  "employmentType": "FULL_TIME",
  "experienceLevel": "SENIOR",
  "mustHaveSkills": "Java, Spring Boot, PostgreSQL, REST APIs",
  "niceToHaveSkills": "Docker, Kubernetes, Redis, RabbitMQ",
  "salaryMin": 120000,
  "salaryMax": 180000
}
```

**Expected:** `201 Created` with job details including `id`.

> Save the `id` — you'll need it as `JOB_ID`.

> Behind the scenes: The app generates a vector embedding for this job using OpenAI. This enables AI-powered candidate matching later.

### 2.2 Create Another Job (for variety)

```
POST /api/v1/jobs
Authorization: Bearer <RECRUITER_TOKEN>
Content-Type: application/json
```

```json
{
  "title": "Frontend React Developer",
  "description": "Build modern web applications using React, TypeScript, and Next.js.",
  "companyName": "TechCorp Inc",
  "location": "Remote",
  "employmentType": "REMOTE",
  "experienceLevel": "MID",
  "mustHaveSkills": "React, TypeScript, HTML, CSS",
  "niceToHaveSkills": "Next.js, GraphQL, Tailwind CSS",
  "salaryMin": 90000,
  "salaryMax": 140000
}
```

### 2.3 List All Jobs

```
GET /api/v1/jobs
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with paginated list of jobs.

**Optional filters:**

```
GET /api/v1/jobs?status=ACTIVE
GET /api/v1/jobs?keyword=Java
GET /api/v1/jobs?page=0&size=10
```

### 2.4 Get Job by ID

```
GET /api/v1/jobs/{JOB_ID}
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with full job details.

### 2.5 Update a Job

```
PUT /api/v1/jobs/{JOB_ID}
Authorization: Bearer <RECRUITER_TOKEN>
Content-Type: application/json
```

```json
{
  "title": "Senior Java Developer",
  "description": "Updated: We need a Java developer with microservices experience.",
  "status": "ACTIVE"
}
```

**Expected:** `200 OK` with updated job.

### 2.6 Close a Job

```
DELETE /api/v1/jobs/{JOB_ID}
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` — job status changes to `CLOSED`.

> Note: This doesn't delete the job, just closes it.

### Negative Tests

| Test | Expected |
|------|----------|
| Create job with CANDIDATE_TOKEN | `403 Forbidden` |
| Create job without `title` | `400 Bad Request` |
| Get job with non-existent ID | `404 Not Found` |

---

## Phase 3: Candidate Profile (as Candidate)

> All requests below require: `Authorization: Bearer <CANDIDATE_TOKEN>`

### 3.1 Get My Profile

```
GET /api/v1/candidates/me
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK` with candidate profile (may have null fields initially).

### 3.2 Update My Profile

```
PUT /api/v1/candidates/me
Authorization: Bearer <CANDIDATE_TOKEN>
Content-Type: application/json
```

```json
{
  "phone": "+1-555-0100",
  "linkedinUrl": "https://linkedin.com/in/janecandidate",
  "yearsOfExperience": 5,
  "currentTitle": "Full Stack Developer"
}
```

**Expected:** `200 OK` with updated profile.

### Negative Tests

| Test | Expected |
|------|----------|
| Access `/candidates/me` with RECRUITER_TOKEN | `403 Forbidden` |

---

## Phase 4: Resume Upload & AI Parsing (as Candidate)

> All requests below require: `Authorization: Bearer <CANDIDATE_TOKEN>`

### 4.1 Upload a Resume

```
POST /api/v1/resumes/upload
Authorization: Bearer <CANDIDATE_TOKEN>
Content-Type: multipart/form-data
```

**Form field:** `file` = select a `.pdf` or `.docx` file (max 10MB)

**cURL example:**

```bash
curl -X POST http://localhost:8080/api/v1/resumes/upload \
  -H "Authorization: Bearer <CANDIDATE_TOKEN>" \
  -F "file=@/path/to/resume.pdf"
```

**In Postman:**
1. Select `POST` and enter the URL
2. Go to `Body` > `form-data`
3. Key: `file` (change type dropdown to **File**)
4. Value: select your PDF/DOCX file

**Expected:** `201 Created` with resume details including `id` and `parseStatus: "PENDING"`.

> Save the `id` — you'll need it as `RESUME_ID`.

> Behind the scenes: A `ResumeParseEvent` is sent to RabbitMQ. The consumer calls OpenAI to extract skills, education, experience from the resume text. This happens asynchronously.

### 4.2 Check Resume Parse Status

```
GET /api/v1/resumes/{RESUME_ID}/status
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK`

```json
{
  "success": true,
  "data": {
    "status": "COMPLETED"
  }
}
```

> Poll this endpoint until status changes from `PENDING` to `COMPLETED`. It may take 5-15 seconds depending on OpenAI response time.

**Possible statuses:** `PENDING` → `COMPLETED` or `FAILED`

### 4.3 Get Resume Details (after parsing completes)

```
GET /api/v1/resumes/{RESUME_ID}
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK` with full parsed data — AI-extracted skills, education, parsed JSON, and AI score.

### 4.4 List My Resumes

```
GET /api/v1/resumes/my
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK` with list of all uploaded resumes.

### Negative Tests

| Test | Expected |
|------|----------|
| Upload resume with RECRUITER_TOKEN | `403 Forbidden` |
| Upload non-PDF/DOCX file | May fail or have empty parsed data |
| Get resume with non-existent ID | `404 Not Found` |

---

## Phase 5: Apply to a Job (as Candidate)

> Requires: Resume is uploaded (Phase 4) and a job exists (Phase 2).
> Make sure the job is `ACTIVE` (create a new one if you closed the previous one).

### 5.1 Apply to a Job

```
POST /api/v1/applications/apply
Authorization: Bearer <CANDIDATE_TOKEN>
Content-Type: application/json
```

```json
{
  "jobId": 1,
  "resumeId": 1
}
```

> Replace with your actual `JOB_ID` and `RESUME_ID`.

**Expected:** `201 Created` with application details including `id`.

> Save the `id` — you'll need it as `APPLICATION_ID`.

> Behind the scenes:
> - `ApplicationScreenEvent` → RabbitMQ → AI screens the candidate (pass/fail)
> - `CandidateScoreEvent` → RabbitMQ → AI scores the match
> - These update the application's `aiMatchScore` and `aiScreeningNotes` asynchronously.

### 5.2 Get My Applications (as Candidate)

```
GET /api/v1/applications/my
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK` with list of candidate's applications. Check `aiMatchScore` and `aiScreeningNotes` after a few seconds — they should be populated by the async AI processing.

### 5.3 Get Application by ID

```
GET /api/v1/applications/{APPLICATION_ID}
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK` with full application details.

### Negative Tests

| Test | Expected |
|------|----------|
| Apply to same job twice | Error (duplicate constraint) |
| Apply with non-existent jobId | `404 Not Found` |
| Apply with RECRUITER_TOKEN | `403 Forbidden` |

---

## Phase 6: Manage Applications (as Recruiter)

> All requests below require: `Authorization: Bearer <RECRUITER_TOKEN>`

### 6.1 List All Applications

```
GET /api/v1/applications
Authorization: Bearer <RECRUITER_TOKEN>
```

**Optional filter:**

```
GET /api/v1/applications?status=APPLIED&page=0&size=20
```

### 6.2 Get Applications for a Specific Job

```
GET /api/v1/applications/job/{JOB_ID}
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with list of all applications for that job.

### 6.3 Update Application Status

```
PATCH /api/v1/applications/{APPLICATION_ID}/status
Authorization: Bearer <RECRUITER_TOKEN>
Content-Type: application/json
```

```json
{
  "status": "SHORTLISTED"
}
```

**Valid status progression:** `APPLIED` → `SCREENING` → `SHORTLISTED` → `INTERVIEW` → `OFFERED` / `REJECTED`

### Negative Tests

| Test | Expected |
|------|----------|
| List applications with CANDIDATE_TOKEN | `403 Forbidden` |
| Update status with CANDIDATE_TOKEN | `403 Forbidden` |

---

## Phase 7: AI Interview (any authenticated user)

> Requires: An application exists (Phase 5).
> Note: Currently no role restriction on interview endpoints.

### 7.1 Start an Interview

```
POST /api/v1/interviews/start
Authorization: Bearer <RECRUITER_TOKEN>
Content-Type: application/json
```

```json
{
  "applicationId": 1,
  "interviewType": "TECHNICAL"
}
```

> Replace `applicationId` with your actual `APPLICATION_ID`.
> Valid types: `AI_SCREENING`, `TECHNICAL`, `BEHAVIORAL`

**Expected:** `201 Created` with interview details including `id` and a list of AI-generated `questions`.

> Save the `id` as `INTERVIEW_ID`.
> Save question `id` values — you'll need them to submit answers.

> Behind the scenes: OpenAI generates tailored interview questions based on the job description and interview type.

### 7.2 Get Interview Details

```
GET /api/v1/interviews/{INTERVIEW_ID}
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with interview status, questions, and scores (if completed).

### 7.3 Get Interview Questions

```
GET /api/v1/interviews/{INTERVIEW_ID}/questions
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with list of questions. Each has `id`, `questionText`, `category`, `difficulty`, `orderIndex`.

### 7.4 Submit an Answer (repeat for each question)

```
POST /api/v1/interviews/{INTERVIEW_ID}/answer
Authorization: Bearer <CANDIDATE_TOKEN>
Content-Type: application/json
```

```json
{
  "questionId": 1,
  "answerText": "I have 5 years of experience with Java and Spring Boot. I've built microservices architectures handling thousands of requests per second using Spring WebFlux and reactive programming patterns. I'm proficient with JPA/Hibernate for database operations and have experience with PostgreSQL optimization including query tuning and indexing strategies."
}
```

> Replace `questionId` with the actual question ID from step 7.3.
> Give a realistic, detailed answer for best AI evaluation results.
> Repeat this call for each question.

**Expected:** `200 OK` with:

```json
{
  "success": true,
  "data": {
    "questionId": 1,
    "score": null,
    "feedback": "Answer submitted. AI evaluation in progress..."
  }
}
```

> The score comes back as `null` initially because evaluation happens **asynchronously** via RabbitMQ. You can check the result later in step 7.6.

### 7.5 Complete the Interview

> Call this **after submitting all answers**.

```
POST /api/v1/interviews/{INTERVIEW_ID}/complete
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with:
- `overallScore` — numeric score out of 100
- `recommendation` — AI's hiring recommendation
- `questionScores` — per-question score and feedback

### 7.6 Get Interview Result

```
GET /api/v1/interviews/{INTERVIEW_ID}/result
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with the complete evaluation including per-question scores and overall recommendation.

### Negative Tests

| Test | Expected |
|------|----------|
| Start interview with non-existent applicationId | `404 Not Found` |
| Start with invalid interviewType | `400 Bad Request` |
| Submit answer with non-existent questionId | `404 Not Found` |

---

## Phase 8: AI Matching

### 8.1 Get Top Candidates for a Job (as Recruiter)

```
GET /api/v1/match/job/{JOB_ID}?limit=10
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with ranked list of candidates sorted by vector similarity score.

> This uses pgvector cosine similarity to match job embeddings against resume embeddings. Requires that both job and resume embeddings have been generated (happens automatically after creation).

### 8.2 Get Recommended Jobs for a Candidate

```
GET /api/v1/match/candidate/{CANDIDATE_ID}?limit=10
Authorization: Bearer <CANDIDATE_TOKEN>
```

> Replace `CANDIDATE_ID` with the candidate's ID (from profile or registration response).

**Expected:** `200 OK` with list of jobs ranked by relevance to the candidate's resume.

### 8.3 Get AI Match Explanation for an Application

```
GET /api/v1/match/explain/{APPLICATION_ID}
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with detailed AI-generated explanation of why the candidate matches (or doesn't match) the job, including highlights and similarity score.

### Negative Tests

| Test | Expected |
|------|----------|
| Match for job without embedding | Error: "Job embedding not yet generated" |
| Match for non-existent job | `404 Not Found` |

---

## Phase 9: Dashboard & Analytics

### 9.1 Dashboard Stats (as Recruiter)

```
GET /api/v1/dashboard/stats
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with aggregated stats (total jobs, candidates, applications, etc).

### 9.2 Recruiter Pipeline Dashboard

```
GET /api/v1/dashboard/recruiter
Authorization: Bearer <RECRUITER_TOKEN>
```

**Expected:** `200 OK` with recruiter-specific pipeline data (applications by status, recent activity).

### 9.3 Candidate Dashboard

```
GET /api/v1/dashboard/candidate
Authorization: Bearer <CANDIDATE_TOKEN>
```

**Expected:** `200 OK` with candidate-specific data (application statuses, interview results).

### Negative Tests

| Test | Expected |
|------|----------|
| Recruiter dashboard with CANDIDATE_TOKEN | `403 Forbidden` |
| Candidate dashboard with RECRUITER_TOKEN | `403 Forbidden` |

---

## Phase 10: Health & Monitoring (No Auth Required)

### 10.1 Health Check

```
GET /actuator/health
```

**Expected:** `200 OK` with `"status": "UP"` and component health (db, redis, rabbit).

### 10.2 App Info

```
GET /actuator/info
```

### 10.3 Prometheus Metrics

```
GET /actuator/prometheus
```

**Expected:** Prometheus-formatted metrics including HTTP request counts, JVM stats, and circuit breaker states.

---

## Complete Testing Sequence (Cheat Sheet)

```
PHASE 1 — AUTH
  1.1  Register recruiter         POST /api/v1/auth/register     → save RECRUITER_TOKEN
  1.2  Register candidate         POST /api/v1/auth/register     → save CANDIDATE_TOKEN
  1.3  Login                      POST /api/v1/auth/login
  1.4  Refresh token              POST /api/v1/auth/refresh

PHASE 2 — JOBS (recruiter)
  2.1  Create job                 POST /api/v1/jobs              → save JOB_ID
  2.2  Create another job         POST /api/v1/jobs
  2.3  List jobs                  GET  /api/v1/jobs
  2.4  Get job by ID              GET  /api/v1/jobs/{id}
  2.5  Update job                 PUT  /api/v1/jobs/{id}
  2.6  Close job                  DELETE /api/v1/jobs/{id}

PHASE 3 — CANDIDATE PROFILE (candidate)
  3.1  Get profile                GET  /api/v1/candidates/me
  3.2  Update profile             PUT  /api/v1/candidates/me

PHASE 4 — RESUME (candidate)
  4.1  Upload resume              POST /api/v1/resumes/upload    → save RESUME_ID
  4.2  Check parse status         GET  /api/v1/resumes/{id}/status (poll until COMPLETED)
  4.3  Get resume details         GET  /api/v1/resumes/{id}
  4.4  List my resumes            GET  /api/v1/resumes/my

PHASE 5 — APPLY (candidate)
  5.1  Apply to job               POST /api/v1/applications/apply → save APPLICATION_ID
  5.2  Get my applications        GET  /api/v1/applications/my
  5.3  Get application by ID      GET  /api/v1/applications/{id}

PHASE 6 — MANAGE APPLICATIONS (recruiter)
  6.1  List all applications      GET  /api/v1/applications
  6.2  Applications for a job     GET  /api/v1/applications/job/{jobId}
  6.3  Update status              PATCH /api/v1/applications/{id}/status

PHASE 7 — INTERVIEW
  7.1  Start interview            POST /api/v1/interviews/start  → save INTERVIEW_ID
  7.2  Get interview              GET  /api/v1/interviews/{id}
  7.3  Get questions              GET  /api/v1/interviews/{id}/questions
  7.4  Submit answer (per Q)      POST /api/v1/interviews/{id}/answer
  7.5  Complete interview         POST /api/v1/interviews/{id}/complete
  7.6  Get result                 GET  /api/v1/interviews/{id}/result

PHASE 8 — AI MATCHING
  8.1  Top candidates for job     GET  /api/v1/match/job/{jobId}
  8.2  Recommended jobs           GET  /api/v1/match/candidate/{candidateId}
  8.3  Match explanation          GET  /api/v1/match/explain/{applicationId}

PHASE 9 — DASHBOARD
  9.1  Stats                      GET  /api/v1/dashboard/stats
  9.2  Recruiter dashboard        GET  /api/v1/dashboard/recruiter
  9.3  Candidate dashboard        GET  /api/v1/dashboard/candidate

PHASE 10 — HEALTH (no auth)
  10.1 Health check               GET  /actuator/health
  10.2 App info                   GET  /actuator/info
  10.3 Prometheus metrics         GET  /actuator/prometheus
```

---

## Token Usage Reminder

| Token | Used For |
|-------|----------|
| `RECRUITER_TOKEN` | Create/update/close jobs, list applications, update application status, recruiter dashboard, start interviews, view match results |
| `CANDIDATE_TOKEN` | Update profile, upload resume, apply to jobs, view my applications, submit interview answers, candidate dashboard |
| **No token** | Auth endpoints (`/api/v1/auth/**`), health check, swagger UI |

---

## Tips

- **Swagger UI** at `http://localhost:8080/swagger-ui.html` lets you test APIs with a built-in UI. Click "Authorize" and paste your Bearer token.
- **AI processing is async** — after uploading a resume or submitting an application, poll the status endpoints for a few seconds before expecting AI results.
- **RabbitMQ Management UI** at `http://localhost:15672` (login: `hireai`/`hireai_secret`) — monitor queues and messages in real-time.
- If an API returns `403 Forbidden`, you're using the wrong role's token.
- If an API returns `401 Unauthorized`, your token has expired — use the refresh endpoint.
