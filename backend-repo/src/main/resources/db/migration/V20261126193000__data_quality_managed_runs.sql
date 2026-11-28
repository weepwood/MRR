-- Existing observations remain unchanged. Only new managed runs compete for one durable slot.
ALTER TABLE app.mrr_data_quality_run
    ADD COLUMN task_managed boolean NOT NULL DEFAULT false,
    ADD COLUMN deadline_at timestamptz,
    ADD COLUMN claimed_at timestamptz,
    ADD COLUMN requested_by bigint,
    ADD COLUMN cancelled_by bigint,
    ADD CONSTRAINT ck_quality_managed_deadline CHECK (NOT task_managed OR deadline_at IS NOT NULL);

CREATE UNIQUE INDEX uq_quality_one_active_managed_run
    ON app.mrr_data_quality_run ((true))
    WHERE task_managed AND status IN ('PENDING','RUNNING','CANCEL_REQUESTED');
