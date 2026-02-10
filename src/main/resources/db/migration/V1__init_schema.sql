-- =============================================
-- V1: Core Schema
-- =============================================

-- 1. Users
CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    email       VARCHAR(255) UNIQUE NOT NULL,
    password    VARCHAR(255) NOT NULL,
    full_name   VARCHAR(255),
    role        VARCHAR(50),
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    updated_at  TIMESTAMP NOT NULL DEFAULT now()
);

-- 2. Jobs
CREATE TABLE jobs (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(255) NOT NULL,
    description         TEXT,
    company_name        VARCHAR(255),
    location            VARCHAR(255),
    employment_type     VARCHAR(50),
    experience_level    VARCHAR(50),
    must_have_skills    TEXT,
    nice_to_have_skills TEXT,
    salary_min          DECIMAL(12,2),
    salary_max          DECIMAL(12,2),
    status              VARCHAR(50) DEFAULT 'DRAFT',
    recruiter_id        BIGINT REFERENCES users(id),
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

-- 3. Candidates
CREATE TABLE candidates (
    id                    BIGSERIAL PRIMARY KEY,
    user_id               BIGINT REFERENCES users(id),
    phone                 VARCHAR(50),
    linkedin_url          VARCHAR(500),
    years_of_experience   INTEGER,
    current_title         VARCHAR(255),
    created_at            TIMESTAMP NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP NOT NULL DEFAULT now()
);

-- 4. Resumes
CREATE TABLE resumes (
    id                  BIGSERIAL PRIMARY KEY,
    candidate_id        BIGINT REFERENCES candidates(id),
    file_name           VARCHAR(255),
    file_path           VARCHAR(500),
    raw_text            TEXT,
    parsed_data         JSONB,
    skills              JSONB,
    experience_summary  TEXT,
    education           JSONB,
    ai_score            DECIMAL(5,2),
    parse_status        VARCHAR(50) DEFAULT 'PENDING',
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now()
);

-- 5. Applications
CREATE TABLE applications (
    id                  BIGSERIAL PRIMARY KEY,
    job_id              BIGINT REFERENCES jobs(id),
    candidate_id        BIGINT REFERENCES candidates(id),
    resume_id           BIGINT REFERENCES resumes(id),
    status              VARCHAR(50) DEFAULT 'APPLIED',
    ai_match_score      DECIMAL(5,2),
    ai_screening_notes  TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    updated_at          TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (job_id, candidate_id)
);

-- 6. Interviews
CREATE TABLE interviews (
    id                  BIGSERIAL PRIMARY KEY,
    application_id      BIGINT REFERENCES applications(id),
    interview_type      VARCHAR(50),
    status              VARCHAR(50) DEFAULT 'PENDING',
    overall_score       DECIMAL(5,2),
    ai_recommendation   TEXT,
    created_at          TIMESTAMP NOT NULL DEFAULT now(),
    completed_at        TIMESTAMP
);

-- 7. Interview Questions
CREATE TABLE interview_questions (
    id              BIGSERIAL PRIMARY KEY,
    interview_id    BIGINT REFERENCES interviews(id),
    question_text   TEXT,
    category        VARCHAR(50),
    difficulty      VARCHAR(50),
    order_index     INTEGER
);

-- 8. Interview Responses
CREATE TABLE interview_responses (
    id              BIGSERIAL PRIMARY KEY,
    question_id     BIGINT REFERENCES interview_questions(id),
    answer_text     TEXT,
    ai_score        DECIMAL(5,2),
    ai_feedback     TEXT,
    answered_at     TIMESTAMP
);
