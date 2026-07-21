ALTER TABLE app.mr_archive_search_history
    ADD COLUMN IF NOT EXISTS query_count INTEGER NOT NULL DEFAULT 1 CHECK (query_count >= 0);
