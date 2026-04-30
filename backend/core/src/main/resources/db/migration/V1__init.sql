-- Extensions
CREATE EXTENSION IF NOT EXISTS vector;

-- Note: the Category enum lives in Kotlin (com.jobhunter.core.domain.Category).
-- Postgres-side validation isn't worth the Hibernate friction with enum arrays;
-- categories are stored as JSONB and validated at the service boundary.

-- job_source
CREATE TABLE job_source (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_run_at TIMESTAMPTZ,
    last_run_status VARCHAR(20),
    last_run_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- job_posting
CREATE TABLE job_posting (
    id BIGSERIAL PRIMARY KEY,
    source_id BIGINT NOT NULL REFERENCES job_source(id),
    external_id VARCHAR(255) NOT NULL,
    source_url TEXT,
    raw_text TEXT NOT NULL,
    raw_html TEXT,
    title VARCHAR(500),
    company VARCHAR(255),
    location VARCHAR(255),
    is_remote BOOLEAN,
    language VARCHAR(2),
    contact_email VARCHAR(255),
    apply_url TEXT,
    description TEXT,
    requirements TEXT,
    salary_text VARCHAR(255),
    categories JSONB NOT NULL DEFAULT '[]'::jsonb,
    posted_at TIMESTAMPTZ,
    captured_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (source_id, external_id)
);

CREATE INDEX idx_posting_categories ON job_posting USING GIN (categories jsonb_path_ops);
CREATE INDEX idx_posting_captured_at ON job_posting (captured_at DESC);
CREATE INDEX idx_posting_contact_email ON job_posting (contact_email)
    WHERE contact_email IS NOT NULL;

-- posting_embedding
CREATE TABLE posting_embedding (
    job_posting_id BIGINT PRIMARY KEY REFERENCES job_posting(id) ON DELETE CASCADE,
    embedding vector(1024) NOT NULL,
    model_name VARCHAR(100) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_posting_embedding_hnsw ON posting_embedding
    USING hnsw (embedding vector_cosine_ops)
    WITH (m = 16, ef_construction = 64);

-- cv
CREATE TABLE cv (
    id BIGSERIAL PRIMARY KEY,
    label VARCHAR(100) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    mime_type VARCHAR(100) NOT NULL,
    file_bytes BYTEA NOT NULL,
    parsed_text TEXT NOT NULL,
    embedding vector(1024) NOT NULL,
    structured_summary JSONB,
    is_active BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX cv_one_active ON cv (is_active) WHERE is_active = TRUE;

-- processing_queue
CREATE TABLE processing_queue (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    state VARCHAR(30) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error TEXT,
    next_attempt_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_queue_state_next ON processing_queue (state, next_attempt_at);

-- match
CREATE TABLE match (
    id BIGSERIAL PRIMARY KEY,
    job_posting_id BIGINT NOT NULL REFERENCES job_posting(id) ON DELETE CASCADE,
    cv_id BIGINT NOT NULL REFERENCES cv(id),
    cosine_similarity DOUBLE PRECISION NOT NULL,
    llm_score INT,
    llm_reasoning JSONB,
    state VARCHAR(30) NOT NULL,
    draft_subject VARCHAR(500),
    draft_body TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (job_posting_id, cv_id)
);

CREATE INDEX idx_match_state_score ON match (state, llm_score DESC NULLS LAST);

-- email_send_record
CREATE TABLE email_send_record (
    id BIGSERIAL PRIMARY KEY,
    match_id BIGINT NOT NULL UNIQUE REFERENCES match(id),
    cv_id BIGINT NOT NULL REFERENCES cv(id),
    to_address VARCHAR(255) NOT NULL,
    subject VARCHAR(500) NOT NULL,
    body TEXT NOT NULL,
    attachment_filename VARCHAR(255),
    sent_at TIMESTAMPTZ NOT NULL,
    smtp_message_id VARCHAR(255),
    status VARCHAR(20) NOT NULL,
    failure_reason TEXT
);
