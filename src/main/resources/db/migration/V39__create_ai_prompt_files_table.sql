CREATE TABLE IF NOT EXISTS ai_prompt_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_url TEXT NOT NULL,
    content TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_ai_prompt_files_created_at_desc
    ON ai_prompt_files (created_at DESC);

