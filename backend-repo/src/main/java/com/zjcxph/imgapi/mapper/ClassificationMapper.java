package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.ocr.ClassificationEngine.*;
import lombok.Data;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ClassificationMapper {
    @Data class TypeState { private Integer id; private Long archiveId; private Integer btype; private long typeVersion; private Integer uploadflag; }
    @Data class Suggestion {
        private Long id; private Integer scanId; private String jobId; private Integer originalType; private long originalTypeVersion;
        private Integer suggestedType; private Double ruleScore; private Double ocrConfidence; private String reviewStatus;
        private String ocrContentHash; private String ocrResultVersion; private String ruleSetVersion; private String lexiconVersion;
        private boolean autoEligible; private String reasonCode; private String matchedRules; private String excludedRules;
        private String autoStatus;
    }
    @Data class LexiconRow { private String term; private String aliases; private String commonOcrErrors; }
    @Select("SELECT id,archive_id,btype,type_version,uploadflag FROM app.mr_scan WHERE id=#{id} FOR UPDATE") TypeState lockScan(Integer id);
    @Select("SELECT id,archive_id,btype,type_version,uploadflag FROM app.mr_scan WHERE id=#{id}") TypeState scan(Integer id);
    @Update("UPDATE app.mr_scan SET btype=#{type},type_version=type_version+1 WHERE id=#{id} AND type_version=#{version} AND uploadflag<>0")
    int change(@Param("id") Integer id,@Param("type") Integer type,@Param("version") long version);
    @Insert("""
        INSERT INTO app.mr_image_type_audit(scan_id,archive_id,old_type,suggested_type,new_type,operation,
          job_id,rule_set_version,rule_score,ocr_confidence,operator_user_id,operator_username,request_id,
          type_version,ocr_result_version,execution_mode,batch_id,policy_checksum,reverts_audit_id)
        VALUES(#{scan.id},#{scan.archiveId},#{scan.btype},#{suggestion.suggestedType,jdbcType=INTEGER},#{type},#{operation},
          #{suggestion.jobId,jdbcType=VARCHAR},#{suggestion.ruleSetVersion,jdbcType=VARCHAR},#{suggestion.ruleScore,jdbcType=NUMERIC},
          #{suggestion.ocrConfidence,jdbcType=NUMERIC},#{actor.id},#{actor.username},#{request,jdbcType=VARCHAR},
          #{version},#{suggestion.ocrResultVersion,jdbcType=VARCHAR},#{mode},#{batch,jdbcType=VARCHAR},#{policy,jdbcType=VARCHAR},#{reverts,jdbcType=BIGINT})
        """) void audit(@Param("scan") TypeState scan,@Param("type") int type,@Param("operation") String operation,
                         @Param("suggestion") Suggestion suggestion,@Param("actor") com.zjcxph.imgapi.common.AuthSession actor,
                         @Param("request") String request,@Param("version") long version,@Param("mode") String mode,
                         @Param("batch") String batch,@Param("policy") String policy,@Param("reverts") Long reverts);
    @Select("""
        SELECT e.id,e.target_type,e.keyword,e.match_scope,e.match_mode,e.weight,e.negative,e.required,e.priority,e.enabled
        FROM app.mr_classification_rule_entry e JOIN app.mr_classification_rule_set s ON s.id=e.rule_set_id
        WHERE s.version=#{version} ORDER BY e.priority,e.id LIMIT 1001
        """) List<Rule> rules(String version);
    @Select("""
        SELECT e.term,e.aliases::text,e.common_ocr_errors::text FROM app.mr_ocr_lexicon_entry e
        JOIN app.mr_ocr_lexicon_set s ON s.id=e.lexicon_set_id WHERE s.version=#{version} AND e.enabled
        ORDER BY e.id LIMIT 1001
        """) List<LexiconRow> lexicon(String version);
    @Insert("""
        INSERT INTO app.mr_image_classification_suggestion(scan_id,job_id,original_type,original_type_version,
          suggested_type,rule_score,ocr_confidence,matched_rules,excluded_rules,rule_set_version,lexicon_version,
          ocr_content_hash,ocr_result_version,auto_eligible,reason_code)
        VALUES(#{scanId},#{jobId},#{originalType},#{originalTypeVersion},#{suggestedType},#{ruleScore},#{ocrConfidence},
          CAST(#{matchedRules} AS jsonb),CAST(#{excludedRules} AS jsonb),#{ruleSetVersion},#{lexiconVersion},
          #{ocrContentHash},#{ocrResultVersion},#{autoEligible},#{reasonCode}) ON CONFLICT(job_id,scan_id) DO NOTHING
        """) void suggest(Suggestion suggestion);
    @Select("SELECT * FROM app.mr_image_classification_suggestion WHERE id=#{id} FOR UPDATE") Suggestion lockSuggestion(Long id);
    @Select("SELECT * FROM app.mr_image_classification_suggestion WHERE job_id=#{job} AND id>#{after} ORDER BY id LIMIT 50")
    List<Suggestion> suggestions(@Param("job") String job,@Param("after") long after);
    @Update("UPDATE app.mr_image_classification_suggestion SET review_status=#{status},final_type=#{type},reviewed_by=#{actor},reviewed_at=NOW(),updated_at=NOW() WHERE id=#{id} AND review_status='PENDING'")
    int reviewed(@Param("id") Long id,@Param("status") String status,@Param("type") Integer type,@Param("actor") String actor);
    @Update("UPDATE app.mr_image_classification_suggestion SET review_status='SUPERSEDED',updated_at=NOW() WHERE scan_id=#{scan} AND review_status='PENDING' AND ocr_result_version IS DISTINCT FROM #{version}")
    void supersede(@Param("scan") Integer scan,@Param("version") String version);
    @Update("UPDATE app.mr_smart_classification_job_item SET suggested_type=#{type},rule_score=#{score},ocr_confidence=#{confidence},status=#{status} WHERE job_id=#{job} AND scan_id=#{scan}")
    void classified(@Param("job") String job,@Param("scan") Integer scan,@Param("type") Integer type,@Param("score") double score,@Param("confidence") double confidence,@Param("status") String status);
}
