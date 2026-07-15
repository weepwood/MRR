-- Keep the already-applied V202607141800 migration immutable.
-- Data quality checks are now manual-only, so new rows should default to manual.
ALTER TABLE IF EXISTS mrr_data_quality_run
    ALTER COLUMN triggered_by SET DEFAULT 'manual';
