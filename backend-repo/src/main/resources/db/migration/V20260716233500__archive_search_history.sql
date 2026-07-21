CREATE TABLE IF NOT EXISTS app.mr_archive_search_history (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    bah VARCHAR(64),
    sjh VARCHAR(64),
    success BOOLEAN NOT NULL,
    image_count INTEGER NOT NULL DEFAULT 0 CHECK (image_count >= 0),
    failure_reason VARCHAR(1000),
    favorite BOOLEAN NOT NULL DEFAULT FALSE,
    searched_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_archive_search_history_code CHECK (NULLIF(BTRIM(COALESCE(bah, '')), '') IS NOT NULL OR NULLIF(BTRIM(COALESCE(sjh, '')), '') IS NOT NULL)
);

CREATE INDEX IF NOT EXISTS idx_archive_search_history_user_time
    ON app.mr_archive_search_history (user_id, searched_at DESC, id DESC);
CREATE INDEX IF NOT EXISTS idx_archive_search_history_user_favorite
    ON app.mr_archive_search_history (user_id, favorite, searched_at DESC);
