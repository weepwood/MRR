package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;

@Service
public class QualityCaseReviewService {
    private final JdbcTemplate jdbc;
    private final DeploymentReadinessService readiness;
    private final MaintenanceModeService maintenance;
    private final boolean enabled;

    public QualityCaseReviewService(JdbcTemplate jdbc,DeploymentReadinessService readiness,
            MaintenanceModeService maintenance,@Value("${app.data-quality.repair-enabled:false}") boolean enabled) {
        this.jdbc=jdbc;this.readiness=readiness;this.maintenance=maintenance;this.enabled=enabled;
    }

    private record Evidence(long issue,long run,String type,long entity,String code,boolean latest) {}
    public record Review(String id,String entityType,String entityId,String checkCode,String issueId,String runId,
                         String status,int revision,String actorId,String updatedAt) {}
    public record Event(int revision,String issueId,String runId,String status,String reason,String actorId,String occurredAt) {}
    public record Context(boolean enabled,boolean latest,boolean reviewedThisSample,Review review,List<Event> events) {}
    public record Page(boolean enabled,List<Review> reviews) {}

    private boolean writable() { return enabled && !readiness.isReadOnly() && !maintenance.isEnabled(); }

    private Evidence evidence(long issue) {
        if(issue<=0) throw new BusinessException(400,"无效异常");
        var rows=jdbc.query("""
                SELECT i.id,i.run_id,i.entity_type,i.entity_id,i.check_code,
                       (r.status='SUCCESS' AND r.completed_at IS NOT NULL
                        AND r.id=(SELECT max(id) FROM app.mrr_data_quality_run WHERE status='SUCCESS' AND completed_at IS NOT NULL)) AS latest
                FROM app.mrr_data_quality_issue i JOIN app.mrr_data_quality_run r ON r.id=i.run_id
                WHERE i.id=?
                """,(rs,n)-> {
            String type=rs.getString(3),code=rs.getString(5);
            long entity;
            try { entity=Long.parseLong(rs.getString(4)); }
            catch(NumberFormatException invalid) { throw new BusinessException(409,"异常缺少稳定记录标识"); }
            if(entity<=0 || !("mr_scan".equals(type) && "SCAN_ARCHIVE_LINK_MISSING_ESTIMATED".equals(code) && entity<=Integer.MAX_VALUE
                    || "mr_archive_box_record".equals(type) && "BOX_ARCHIVE_LINK_MISSING".equals(code)))
                throw new BusinessException(409,"该规则尚未开放异常决策");
            return new Evidence(rs.getLong(1),rs.getLong(2),type,entity,code,rs.getBoolean(6));
        },issue);
        return rows.stream().findFirst().orElseThrow(()->new BusinessException(404,"异常不存在或样本已清理"));
    }

    private static Review review(ResultSet rs,int n) throws SQLException {
        return new Review(rs.getString("id"),rs.getString("entity_type"),rs.getString("entity_id"),rs.getString("check_code"),
                rs.getString("issue_id"),rs.getString("run_id"),rs.getString("status"),rs.getInt("revision"),rs.getString("actor_id"),rs.getString("updated_at"));
    }
    private Review find(Evidence e) {
        return jdbc.query("SELECT * FROM app.mrr_quality_case_review WHERE entity_type=? AND entity_id=? AND check_code=?",
                QualityCaseReviewService::review,e.type(),e.entity(),e.code()).stream().findFirst().orElse(null);
    }

    @Transactional(readOnly=true,timeout=5)
    public Context context(long issue) {
        var e=evidence(issue);var row=find(e);
        return new Context(writable(),e.latest(),row!=null && Objects.equals(row.issueId(),Long.toString(issue)),row,
                row==null?List.of():readEvents(Long.parseLong(row.id()),0));
    }

    @Transactional(readOnly=true,timeout=5)
    public Page list(Long before) {
        if(before!=null && before<=0) throw new BusinessException(400,"无效历史游标");
        return new Page(writable(),before==null
                ? jdbc.query("SELECT * FROM app.mrr_quality_case_review ORDER BY id DESC LIMIT 50",QualityCaseReviewService::review)
                : jdbc.query("SELECT * FROM app.mrr_quality_case_review WHERE id<? ORDER BY id DESC LIMIT 50",QualityCaseReviewService::review,before));
    }

    @Transactional(readOnly=true,timeout=5)
    public List<Event> events(long id,int before) {
        if(id<=0 || before<0) throw new BusinessException(400,"无效历史参数");
        return readEvents(id,before);
    }
    private List<Event> readEvents(long id,int before) {
        return jdbc.query("""
                SELECT revision,issue_id,run_id,status,reason,actor_id,occurred_at::text
                FROM app.mrr_quality_case_review_event WHERE case_id=? AND (?=0 OR revision<?)
                ORDER BY revision DESC LIMIT 50
                """,(rs,n)->new Event(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getString(7)),id,before,before);
    }

    @Transactional(timeout=5)
    public Review decide(long issue,int expectedRevision,String action,long actor) {
        if(actor<=0) throw new BusinessException(401,"无效操作者");
        if(!writable()) throw new BusinessException(503,"异常决策写入已关闭或系统只读");
        if(expectedRevision<0 || action==null) throw new BusinessException(400,"无效决策参数");
        String status,reason;
        switch(action) {
            case "CONFIRM" -> {status="CONFIRMED";reason="CONFIRMED_EVIDENCE";}
            case "IGNORE" -> {status="IGNORED";reason="DEFERRED_REVIEW";}
            case "REOPEN" -> {status="OPEN";reason="REOPEN_FOR_REVIEW";}
            default -> throw new BusinessException(400,"无效决策动作");
        }
        var e=evidence(issue);
        if(!e.latest()) throw conflict("仅允许对最新已完成成功检查的样本记录决策");
        var prior=find(e);
        if(prior==null && "OPEN".equals(status)) throw conflict("尚无历史决策");
        if(prior!=null && prior.status().equals(status) && prior.issueId().equals(Long.toString(issue))) throw conflict("该样本已记录相同决策");
        // Version check and append are atomic. The evidence remains historical, never write medical facts.
        var ids=jdbc.query("""
                INSERT INTO app.mrr_quality_case_review(entity_type,entity_id,check_code,issue_id,run_id,status,revision,actor_id)
                SELECT ?,?,?,?,?,?,1,? WHERE ?=0
                ON CONFLICT(entity_type,entity_id,check_code) DO NOTHING RETURNING id
                """,(rs,n)->rs.getLong(1),e.type(),e.entity(),e.code(),e.issue(),e.run(),status,actor,expectedRevision);
        if(ids.isEmpty() && expectedRevision>0) {
            ids=jdbc.query("""
                    UPDATE app.mrr_quality_case_review SET issue_id=?,run_id=?,status=?,revision=revision+1,actor_id=?,updated_at=clock_timestamp()
                    WHERE entity_type=? AND entity_id=? AND check_code=? AND revision=? AND run_id<=? AND revision<2147483647
                    RETURNING id
                    """,(rs,n)->rs.getLong(1),e.issue(),e.run(),status,actor,e.type(),e.entity(),e.code(),expectedRevision,e.run());
        }
        if(ids.size()!=1) throw conflict("决策已变化，请刷新后重试");
        var row=find(e);
        jdbc.update("""
                INSERT INTO app.mrr_quality_case_review_event(case_id,revision,issue_id,run_id,status,reason,actor_id)
                VALUES (?,?,?,?,?,?,?)
                """,ids.getFirst(),row.revision(),issue,e.run(),status,reason,actor);
        return row;
    }
    private static BusinessException conflict(String message) { return new BusinessException(409,message); }
}
