package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Explicit, single-record repair; existing preview is not write authorization. */
@Service
public class DataQualityRepairOrderService {
    private final JdbcTemplate jdbc;
    private final DataQualityService quality;
    private final DeploymentReadinessService readiness;
    private final MaintenanceModeService maintenance;
    private final boolean enabled;

    public DataQualityRepairOrderService(JdbcTemplate jdbc, DataQualityService quality,
            DeploymentReadinessService readiness, MaintenanceModeService maintenance,
            @Value("${app.data-quality.repair-enabled:false}") boolean enabled) {
        this.jdbc = jdbc;
        this.quality = quality;
        this.readiness = readiness;
        this.maintenance = maintenance;
        this.enabled = enabled;
    }

    public record Order(long id, long issueId, int scanId, long targetArchiveId,
                        String status, long proposedBy, Long approvedBy) {}
    private record Source(Long archiveId, String fingerprint, String revision, String bah, String sjh, Integer active) {}
    private record Candidate(long archiveId, String fingerprint) {}
    private record State(Order order, String sourceFingerprint, String targetFingerprint, String appliedRevision, boolean fresh) {}
    public boolean enabled() { return enabled; }

    @Transactional(readOnly = true, timeout = 5)
    public Order get(long id) { return state(id, false).order(); }

    @Transactional(timeout = 5)
    public Order propose(long issueId, long actor) {
        guard(actor);
        var issue = quality.getIssue(issueId);
        if (!"SUCCESS".equals(issue.get("runStatus")) || !"mr_scan".equals(issue.get("entityType"))
                || !"SCAN_ARCHIVE_LINK_MISSING_ESTIMATED".equals(issue.get("checkCode")))
            throw conflict("仅支持成功检查中的扫描缺失关联");
        int scanId;
        try { scanId = Integer.parseInt(String.valueOf(issue.get("entityId"))); }
        catch (NumberFormatException e) { throw conflict("无效扫描记录"); }
        lockArchive();
        var source = source(scanId);
        var preview = quality.previewRepair(issueId);
        if (!Boolean.TRUE.equals(preview.get("deterministic"))) throw conflict("不存在确定性修复候选");
        var candidate = uniqueCandidate(source);
        if (source.archiveId() != null) throw conflict("扫描已有主档关联");
        Long id = jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_repair_order(issue_id,scan_id,target_archive_id,source_fingerprint,target_fingerprint,status,proposed_by)
                VALUES (?,?,?,?,?,'PROPOSED',?) RETURNING id
                """, Long.class, issueId, scanId, candidate.archiveId(), source.fingerprint(), candidate.fingerprint(), actor);
        audit(id, actor, "PROPOSED", null, null);
        return get(id);
    }

    @Transactional(timeout = 5)
    public Order approve(long id, long actor) {
        guard(actor);
        State s = state(id, true);
        require(s, "PROPOSED");
        if (s.order().proposedBy() == actor) throw conflict("审批人与提交人必须不同");
        lockArchive();
        recheck(s, false);
        jdbc.update("""
                UPDATE app.mrr_data_quality_repair_order SET status='APPROVED',approved_by=?,approved_at=clock_timestamp(),updated_at=clock_timestamp()
                WHERE id=?
                """, actor, id);
        audit(id, actor, "APPROVED", null, null);
        return get(id);
    }

    @Transactional(timeout = 5)
    public Order apply(long id, long actor) {
        guard(actor);
        State s = state(id, true);
        require(s, "APPROVED");
        if (!s.fresh() || !Objects.equals(s.order().approvedBy(), actor)) throw conflict("审批已过期或执行者不是审批人");
        lockArchive();
        recheck(s, false);
        var o = s.order();
        int updated = jdbc.update("UPDATE app.mr_scan SET archive_id=? WHERE id=? AND archive_id IS NULL", o.targetArchiveId(), o.scanId());
        if (updated != 1) throw conflict("关联执行冲突");
        var after = source(o.scanId());
        if (!Objects.equals(after.archiveId(), o.targetArchiveId()) || !after.fingerprint().equals(s.sourceFingerprint()))
            throw conflict("实际执行结果与审批不一致");
        jdbc.update("UPDATE app.mrr_data_quality_repair_order SET status='APPLIED',applied_revision=?,updated_at=clock_timestamp() WHERE id=?", after.revision(), id);
        audit(id, actor, "APPLIED", null, o.targetArchiveId());
        return get(id);
    }

    @Transactional(timeout = 5)
    public Order verify(long id, long actor) {
        guard(actor);
        State s = state(id, true);
        require(s, "APPLIED");
        lockArchive();
        recheck(s, true);
        change(id, "VERIFIED");
        audit(id, actor, "VERIFIED", s.order().targetArchiveId(), s.order().targetArchiveId());
        return get(id);
    }

    @Transactional(timeout = 5)
    public Order cancel(long id, long actor) {
        guard(actor);
        State s = state(id, true);
        if (!List.of("PROPOSED","APPROVED").contains(s.order().status())) throw conflict("工单不可取消");
        if (s.order().proposedBy() != actor && !Objects.equals(s.order().approvedBy(), actor)) throw conflict("仅提交人或审批人可取消");
        change(id, "CANCELLED");
        audit(id, actor, "CANCELLED", null, null);
        return get(id);
    }

    @Transactional(timeout = 5)
    public Order compensate(long id, long actor) {
        guard(actor);
        State s = state(id, true);
        if (!List.of("APPLIED","VERIFIED").contains(s.order().status())) throw conflict("工单不可补偿");
        if (!Objects.equals(s.order().approvedBy(), actor)) throw conflict("仅原审批人可补偿");
        lockArchive();
        var o = s.order();
        Source row = source(o.scanId());
        if (!Objects.equals(row.archiveId(), o.targetArchiveId()) || !row.fingerprint().equals(s.sourceFingerprint())
                || !row.revision().equals(s.appliedRevision()) || !target(o.targetArchiveId()).fingerprint().equals(s.targetFingerprint()))
            throw conflict("关联事实已变化，禁止补偿");
        // Existing trigger re-resolves a NULL link. Never bypass it or create an archive as a side effect.
        if (row.sjh() != null && !row.sjh().isBlank()) throw conflict("现有上架号触发器不允许安全补偿");
        Long resolved = jdbc.queryForObject("SELECT app.resolve_archive_id(?,?,false)", Long.class, row.bah(), row.sjh());
        if (resolved != null) throw conflict("现有触发器将重新关联，禁止补偿");
        if (jdbc.update("UPDATE app.mr_scan SET archive_id=NULL WHERE id=? AND archive_id=?", o.scanId(), o.targetArchiveId()) != 1)
            throw conflict("补偿冲突");
        Source after = source(o.scanId());
        if (after.archiveId() != null || !after.fingerprint().equals(s.sourceFingerprint())) throw conflict("补偿结果不一致");
        change(id, "COMPENSATED");
        audit(id, actor, "COMPENSATED", o.targetArchiveId(), null);
        return get(id);
    }

    private void guard(long actor) {
        if (actor <= 0) throw new BusinessException(401,"无效操作者");
        if (!enabled) throw new BusinessException(503,"修复工单已关闭");
        if (readiness.isReadOnly() || maintenance.isEnabled()) throw new BusinessException(503,"系统处于只读模式");
    }

    private void lockArchive() {
        // Short fail-fast lock also protects against newly inserted matching archives.
        jdbc.execute("LOCK TABLE app.mr_archive IN SHARE MODE NOWAIT");
    }

    private Source source(int scanId) {
        return jdbc.query("""
                SELECT archive_id,encode(sha256(convert_to((to_jsonb(s)-'archive_id')::text,'UTF8')),'hex') AS fingerprint,
                       s.xmin::text AS revision,bah,sjh,uploadflag
                FROM app.mr_scan s WHERE id=? FOR UPDATE NOWAIT
                """, (rs,n) -> new Source(rs.getObject("archive_id",Long.class),rs.getString("fingerprint"),
                rs.getString("revision"),rs.getString("bah"),rs.getString("sjh"),rs.getObject("uploadflag",Integer.class)),scanId)
                .stream().findFirst().orElseThrow(() -> conflict("扫描记录不存在"));
    }

    private Candidate target(long archiveId) {
        return jdbc.query("""
                SELECT id,encode(sha256(convert_to(to_jsonb(a)::text,'UTF8')),'hex') AS fingerprint
                FROM app.mr_archive a WHERE id=?
                """,(rs,n)->new Candidate(rs.getLong("id"),rs.getString("fingerprint")),archiveId)
                .stream().findFirst().orElseThrow(()->conflict("目标主档不存在"));
    }

    private Candidate uniqueCandidate(Source row) {
        String bah = row.bah() == null ? "" : row.bah().trim();
        String sjh = row.sjh() == null ? "" : row.sjh().trim();
        if (row.active()==null || row.active()==0 || !bah.matches("[0-9]+") || (!sjh.isEmpty() && !sjh.matches("[0-9]+"))
                || (com.zjcxph.imgapi.utils.MedicalRecordCodeUtils.requiresSjhForBah(bah) && sjh.length()<4))
            throw conflict("记录或编号不满足安全关联规则");
        // Count the entire matching key domain up to two; never choose first among duplicates.
        List<Map<String,Object>> matches = sjh.isEmpty()
                ? jdbc.queryForList("SELECT id,bah FROM app.mr_archive WHERE app.numeric_code_key(bah)=app.numeric_code_key(?) LIMIT 2", bah)
                : jdbc.queryForList("SELECT id,bah FROM app.mr_archive WHERE app.numeric_code_key(sjh)=app.numeric_code_key(?) LIMIT 2", sjh);
        if (matches.size()!=1 || !com.zjcxph.imgapi.utils.MedicalRecordCodeUtils.toSearchTerm(bah).equals(
                com.zjcxph.imgapi.utils.MedicalRecordCodeUtils.toSearchTerm(Objects.toString(matches.getFirst().get("bah"),""))))
            throw conflict("候选主档不唯一或编号冲突");
        return target(((Number)matches.getFirst().get("id")).longValue());
    }

    private void recheck(State s, boolean applied) {
        var o=s.order();
        Source row=source(o.scanId());
        if (!row.fingerprint().equals(s.sourceFingerprint()) || !Objects.equals(row.archiveId(),applied?o.targetArchiveId():null)
                || (applied && !row.revision().equals(s.appliedRevision()))) throw conflict("扫描记录已变化");
        Candidate c=uniqueCandidate(row);
        if (c.archiveId()!=o.targetArchiveId() || !c.fingerprint().equals(s.targetFingerprint())) throw conflict("目标主档已变化");
    }

    private State state(long id, boolean lock) {
        return jdbc.query("""
                SELECT *, approved_at > clock_timestamp()-interval '24 hours' AS fresh
                FROM app.mrr_data_quality_repair_order WHERE id=?
                """+(lock?" FOR UPDATE NOWAIT":""),(rs,n)->new State(
                new Order(rs.getLong("id"),rs.getLong("issue_id"),rs.getInt("scan_id"),rs.getLong("target_archive_id"),
                        rs.getString("status"),rs.getLong("proposed_by"),rs.getObject("approved_by",Long.class)),
                rs.getString("source_fingerprint"),rs.getString("target_fingerprint"),rs.getString("applied_revision"),rs.getBoolean("fresh")),id)
                .stream().findFirst().orElseThrow(()->new BusinessException(404,"工单不存在"));
    }

    private void require(State s, String status) {
        if (!status.equals(s.order().status())) throw conflict("工单状态不允许当前操作");
    }

    private void change(long id, String status) {
        jdbc.update("UPDATE app.mrr_data_quality_repair_order SET status=?,updated_at=clock_timestamp() WHERE id=?",status,id);
    }

    private void audit(long id,long actor,String action,Long before,Long after) {
        jdbc.update("INSERT INTO app.mrr_data_quality_repair_event(order_id,actor_id,action,before_archive_id,after_archive_id) VALUES (?,?,?,?,?)",
                id,actor,action,before,after);
    }

    private static BusinessException conflict(String message) { return new BusinessException(409,message); }
}
