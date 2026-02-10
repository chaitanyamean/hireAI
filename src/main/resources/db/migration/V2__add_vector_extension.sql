CREATE EXTENSION IF NOT EXISTS vector;

ALTER TABLE resumes ADD COLUMN embedding vector(1536);
ALTER TABLE jobs ADD COLUMN embedding vector(1536);
