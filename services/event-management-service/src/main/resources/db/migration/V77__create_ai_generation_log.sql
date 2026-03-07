-- Story 10.16: AI generation cost monitoring log
CREATE TABLE ai_generation_log (
    id                UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    event_code        VARCHAR(20),
    type              VARCHAR(30) NOT NULL,   -- 'description', 'theme_image', 'abstract_analysis'
    input_hash        VARCHAR(64) NOT NULL,
    generated_at      TIMESTAMP   NOT NULL DEFAULT NOW(),
    tokens_used       INTEGER,
    was_accepted      BOOLEAN
);

CREATE INDEX idx_ai_generation_log_event_code ON ai_generation_log (event_code);
CREATE INDEX idx_ai_generation_log_generated_at ON ai_generation_log (generated_at);
