-- Users
CREATE INDEX idx_users_email ON users (email);

-- Jobs
CREATE INDEX idx_jobs_status ON jobs (status);
CREATE INDEX idx_jobs_recruiter_id ON jobs (recruiter_id);

-- Applications
CREATE INDEX idx_applications_status ON applications (status);
CREATE INDEX idx_applications_job_id ON applications (job_id);
CREATE INDEX idx_applications_candidate_id ON applications (candidate_id);

-- Resumes
CREATE INDEX idx_resumes_parse_status ON resumes (parse_status);
CREATE INDEX idx_resumes_candidate_id ON resumes (candidate_id);

-- Vector similarity indexes (IVFFlat)
CREATE INDEX idx_resumes_embedding ON resumes USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
CREATE INDEX idx_jobs_embedding ON jobs USING ivfflat (embedding vector_cosine_ops) WITH (lists = 100);
