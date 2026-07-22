ALTER TABLE mr_auth_user
    ADD COLUMN IF NOT EXISTS contact_info varchar(200),
    ADD COLUMN IF NOT EXISTS apply_remark varchar(500),
    ADD COLUMN IF NOT EXISTS applied_at timestamp,
    ADD COLUMN IF NOT EXISTS reviewed_at timestamp,
    ADD COLUMN IF NOT EXISTS reviewed_by bigint,
    ADD COLUMN IF NOT EXISTS reject_reason varchar(500);

UPDATE mr_auth_user
SET status = lower(trim(status));

UPDATE mr_auth_user
SET status = 'disabled'
WHERE status NOT IN ('pending', 'active', 'rejected', 'disabled');

ALTER TABLE mr_auth_user
    DROP CONSTRAINT IF EXISTS chk_mr_auth_user_status;

ALTER TABLE mr_auth_user
    ADD CONSTRAINT chk_mr_auth_user_status
        CHECK (status IN ('pending', 'active', 'rejected', 'disabled'));

CREATE INDEX IF NOT EXISTS idx_mr_auth_user_status_applied_at
    ON mr_auth_user (status, applied_at DESC);

COMMENT ON COLUMN mr_auth_user.contact_info IS '注册申请人联系方式';
COMMENT ON COLUMN mr_auth_user.apply_remark IS '注册申请说明';
COMMENT ON COLUMN mr_auth_user.applied_at IS '自主注册申请时间';
COMMENT ON COLUMN mr_auth_user.reviewed_at IS '管理员审核时间';
COMMENT ON COLUMN mr_auth_user.reviewed_by IS '执行审核的管理员用户 ID';
COMMENT ON COLUMN mr_auth_user.reject_reason IS '注册申请拒绝原因';
