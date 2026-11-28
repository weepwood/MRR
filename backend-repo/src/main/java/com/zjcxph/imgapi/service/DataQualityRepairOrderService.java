package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import com.zjcxph.imgapi.security.MedicalAccess;
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
    private final MedicalAccess medicalAccess;

    public DataQualityRepairOrderService(JdbcTemplate jdbc, DataQualityService quality,
            DeploymentReadinessService readiness, MaintenanceModeService maintenance,
            @Value("${app.data-quality.repair-enabled:false}") boolean enabled, MedicalAccess medicalAccess) {
        this.jdbc = jdbc;
        this.quality = quality;
        this.readiness = readiness;
        this.maintenance = maintenance;
        this.enabled = enabled;
        this.medicalAccess = medicalAccess;
    }

    public record Order(long id, long issueId, Integer scanId, long targetArchiveId,
                        String status, long proposedBy, Long approvedBy, Long reopenedFrom, String entityType, String entityId) {}
    // Fixed allowlist; table identifiers never originate from an HTTP parameter.
    private enum Scope {
        SCAN("mr_scan","SCAN_ARCHIVE_LINK_MISSING_ESTIMATED","app.mr_scan","uploadflag"),
        BOX("mr_archive_box_record","BOX_ARCHIVE_LINK_MISSING","app.mr_archive_box_record","CASE WHEN status='NORMAL' THEN 1 ELSE 0 END");
        final String type, check, table, active;
        Scope(String type,String check,String table,String active) {
            this.type=type;this.check=check;this.table=table;this.active=active;
        }
        static Scope from(Object type,Object check) {
            for(var scope:values()) if(scope.type.equals(type) && scope.check.equals(check)) return scope;
            throw conflict("仅支持成功检查中的扫描或正常装箱缺失关联");
        }
        static Scope of(Order order) {
            for(var scope:values()) if(scope.type.equals(order.entityType())) return scope;
            throw conflict("不支持的工单类型");
        }
        long parse(Object raw) {
            try {
                long id=Long.parseLong(String.valueOf(raw));
                if(id<=0 || (this==SCAN && id>Integer.MAX_VALUE)) throw new NumberFormatException();
                return id;
            } catch(NumberFormatException invalid) { throw conflict("无效关联记录"); }
        }
    }
    private record Source(Long archiveId, String fingerprint, String revision, String bah, String sjh, Integer active) {}
    private record Candidate(long archiveId, String fingerprint) {}
    private record State(Order order, String sourceFingerprint, String targetFingerprint, String appliedRevision, boolean fresh) {}
    public boolean enabled() { return enabled; }

    public record Workbench(boolean enabled,long actorId,List<Order> orders) {}
    public record Event(long id,long actorId,String action,Long beforeArchiveId,Long afterArchiveId,String createdAt) {}

    @Transactional(readOnly = true, timeout = 5)
    public Workbench list(Long archiveId, Long issueId, Long before, long actor) {
        if (actor<=0 || (archiveId!=null && archiveId<=0) || (issueId!=null && issueId<=0) || (before!=null && before<=0))
            throw new BusinessException(400,"无效查询参数");
        StringBuilder sql=new StringBuilder("SELECT id,issue_id,scan_id,target_archive_id,status,proposed_by,approved_by,reopened_from,entity_type,entity_id FROM app.mrr_data_quality_repair_order WHERE true");
        java.util.ArrayList<Object> args=new java.util.ArrayList<>();
        if (archiveId!=null) { sql.append(" AND target_archive_id=?"); args.add(archiveId); }
        if (issueId!=null) {
            var rows=jdbc.queryForList("SELECT entity_type,check_code,entity_id FROM app.mrr_data_quality_issue WHERE id=?",issueId);
            Scope scope=null; long entity=0;
            if (!rows.isEmpty()) {
                var row=rows.getFirst();
                try { scope=Scope.from(row.get("entity_type"),row.get("check_code")); entity=scope.parse(row.get("entity_id")); }
                catch(BusinessException invalid) { scope=null; }
            }
            if(scope!=null) {
                sql.append(" AND (issue_id=? OR (entity_type=? AND entity_id=?))");
                args.add(issueId);args.add(scope.type);args.add(entity);
            } else { sql.append(" AND issue_id=?"); args.add(issueId); }
        }
        if (before!=null) { sql.append(" AND id<?"); args.add(before); }
        sql.append(" ORDER BY id DESC LIMIT 50");
        List<Order> orders=jdbc.query(sql.toString(),(rs,n)->new Order(rs.getLong(1),rs.getLong(2),rs.getObject(3,Integer.class),
                rs.getLong(4),rs.getString(5),rs.getLong(6),rs.getObject(7,Long.class),rs.getObject(8,Long.class),rs.getString(9),rs.getString(10)),args.toArray());
        return new Workbench(enabled && !readiness.isReadOnly() && !maintenance.isEnabled(),actor,orders);
    }

    @Transactional(readOnly = true, timeout = 5)
    public List<Event> events(long id, long after) {
        get(id);
        return jdbc.query("""
                SELECT id,actor_id,action,before_archive_id,after_archive_id,created_at::text
                FROM app.mrr_data_quality_repair_event WHERE order_id=? AND id>?
                ORDER BY id LIMIT 100
                """,(rs,n)->new Event(rs.getLong(1),rs.getLong(2),rs.getString(3),
                rs.getObject(4,Long.class),rs.getObject(5,Long.class),rs.getString(6)),id,Math.max(0,after));
    }

    @Transactional(readOnly = true, timeout = 5)
    public Order get(long id) { return state(id, false).order(); }

    @Transactional(timeout = 5)
    public Order propose(long issueId, long actor) {
        guard(actor);
        var issue = quality.getIssue(issueId);
        if (!"SUCCESS".equals(issue.get("runStatus"))) throw conflict("仅支持成功检查中的缺失关联");
        Scope scope=Scope.from(issue.get("entityType"),issue.get("checkCode"));
        long entity=scope.parse(issue.get("entityId"));
        lockArchive();
        var source=source(scope,entity);
        var preview = quality.previewRepair(issueId);
        if (!Boolean.TRUE.equals(preview.get("deterministic"))) throw conflict("不存在确定性修复候选");
        var candidate = uniqueCandidate(source);
        if (source.archiveId() != null) throw conflict("记录已有主档关联");
        Long prior = jdbc.query("""
                SELECT id FROM app.mrr_data_quality_repair_order
                WHERE entity_type=? AND entity_id=? AND status IN ('PROPOSED','APPROVED','APPLIED','VERIFIED')
                FOR UPDATE NOWAIT
                """,(rs,n)->rs.getLong(1),scope.type,entity).stream().findFirst().orElse(null);
        if (prior != null) {
            // A fresh successful run must have started after verification, not merely completed later.
            // Reopen only a real recurrence (the current source was locked and confirmed unlinked above).
            boolean recurrent = Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM app.mrr_data_quality_repair_order o
                      JOIN app.mrr_data_quality_issue i ON i.id=?
                      JOIN app.mrr_data_quality_run r ON r.id=i.run_id
                      WHERE o.id=? AND o.status='VERIFIED' AND o.issue_id<>i.id
                        AND i.entity_type=o.entity_type AND i.entity_id=CAST(o.entity_id AS text)
                        AND i.check_code=?
                        AND r.status='SUCCESS' AND r.started_at>o.updated_at
                        AND r.completed_at IS NOT NULL AND i.detected_at>=r.started_at)
                    """,Boolean.class,issueId,prior,scope.check));
            if (!recurrent) throw conflict("该记录已有有效修复工单，需在复验后重新成功检查");
            change(prior,"SUPERSEDED");
            // This event records an order transition, not a modification of the medical link.
            audit(prior,actor,"SUPERSEDED",null,null);
        }
        Long id = jdbc.queryForObject("""
                INSERT INTO app.mrr_data_quality_repair_order(issue_id,scan_id,target_archive_id,source_fingerprint,target_fingerprint,status,proposed_by,reopened_from,entity_type,entity_id)
                VALUES (?,?,?,?,?,'PROPOSED',?,?,?,?) RETURNING id
                """, Long.class, issueId, scope==Scope.SCAN ? (int)entity : null, candidate.archiveId(), source.fingerprint(), candidate.fingerprint(), actor,prior,scope.type,entity);
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
        Scope scope=Scope.of(o); long entity=scope.parse(o.entityId());
        int updated = jdbc.update("UPDATE "+scope.table+" SET archive_id=? WHERE id=? AND archive_id IS NULL", o.targetArchiveId(), entity);
        if (updated != 1) throw conflict("关联执行冲突");
        var after = source(scope,entity);
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
        Scope scope=Scope.of(o); long entity=scope.parse(o.entityId());
        Source row = source(scope,entity);
        if (!Objects.equals(row.archiveId(), o.targetArchiveId()) || !row.fingerprint().equals(s.sourceFingerprint())
                || !row.revision().equals(s.appliedRevision()) || !target(o.targetArchiveId()).fingerprint().equals(s.targetFingerprint()))
            throw conflict("关联事实已变化，禁止补偿");
        // Existing trigger re-resolves a NULL link. Never bypass it or create an archive as a side effect.
        if (row.sjh() != null && !row.sjh().isBlank()) throw conflict("现有上架号触发器不允许安全补偿");
        Long resolved = jdbc.queryForObject("SELECT app.resolve_archive_id(?,?,false)", Long.class, row.bah(), row.sjh());
        if (resolved != null) throw conflict("现有触发器将重新关联，禁止补偿");
        if (jdbc.update("UPDATE "+scope.table+" SET archive_id=NULL WHERE id=? AND archive_id=?", entity, o.targetArchiveId()) != 1)
            throw conflict("补偿冲突");
        Source after = source(scope,entity);
        if (after.archiveId() != null || !after.fingerprint().equals(s.sourceFingerprint())) throw conflict("补偿结果不一致");
        change(id, "COMPENSATED");
        audit(id, actor, "COMPENSATED", o.targetArchiveId(), null);
        return get(id);
    }

    private void guard(long actor) {
        if (actor <= 0) throw new BusinessException(401,"无效操作者");
        if (!enabled) throw new BusinessException(503,"修复工单已关闭");
        if (readiness.isReadOnly() || maintenance.isEnabled()) throw new BusinessException(503,"系统处于只读模式");
        medicalAccess.requireWrite(actor,"system:manage","record:manage");
    }

    private void lockArchive() {
        // Short fail-fast lock also protects against newly inserted matching archives.
        jdbc.execute("LOCK TABLE app.mr_archive IN SHARE MODE NOWAIT");
    }

    private Source source(Scope scope,long entity) {
        return jdbc.query("""
                SELECT archive_id,encode(sha256(convert_to((to_jsonb(s)-'archive_id')::text,'UTF8')),'hex') AS fingerprint,
                       s.xmin::text AS revision,bah,sjh,%s AS active
                FROM %s s WHERE id=? FOR UPDATE NOWAIT
                """.formatted(scope.active,scope.table), (rs,n) -> new Source(rs.getObject("archive_id",Long.class),rs.getString("fingerprint"),
                rs.getString("revision"),rs.getString("bah"),rs.getString("sjh"),rs.getObject("active",Integer.class)),entity)
                .stream().findFirst().orElseThrow(() -> conflict("关联记录不存在"));
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
        Scope scope=Scope.of(o);
        Source row=source(scope,scope.parse(o.entityId()));
        if (!row.fingerprint().equals(s.sourceFingerprint()) || !Objects.equals(row.archiveId(),applied?o.targetArchiveId():null)
                || (applied && !row.revision().equals(s.appliedRevision()))) throw conflict("关联记录已变化");
        Candidate c=uniqueCandidate(row);
        if (c.archiveId()!=o.targetArchiveId() || !c.fingerprint().equals(s.targetFingerprint())) throw conflict("目标主档已变化");
    }

    private State state(long id, boolean lock) {
        return jdbc.query("""
                SELECT *, approved_at > clock_timestamp()-interval '24 hours' AS fresh
                FROM app.mrr_data_quality_repair_order WHERE id=?
                """+(lock?" FOR UPDATE NOWAIT":""),(rs,n)->new State(
                new Order(rs.getLong("id"),rs.getLong("issue_id"),rs.getObject("scan_id",Integer.class),rs.getLong("target_archive_id"),
                        rs.getString("status"),rs.getLong("proposed_by"),rs.getObject("approved_by",Long.class),rs.getObject("reopened_from",Long.class),rs.getString("entity_type"),rs.getString("entity_id")),
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
