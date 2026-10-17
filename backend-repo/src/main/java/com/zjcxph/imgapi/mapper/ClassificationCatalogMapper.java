package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.ocr.ClassificationEngine.*;
import org.apache.ibatis.annotations.*;
import java.util.List;

@Mapper
public interface ClassificationCatalogMapper {
    record Version(String version,String name,String status) {}
    @Select("SELECT version,name,status FROM app.mr_classification_rule_set ORDER BY created_at DESC,id DESC LIMIT 50") List<Version> versions();
    @Select("SELECT version,name,status FROM app.mr_classification_rule_set WHERE version=#{version} FOR UPDATE") Version lock(String version);
    @Select("SELECT version,name,status FROM app.mr_classification_rule_set WHERE version=#{version}") Version version(String version);
    @Update("UPDATE app.mr_classification_rule_set SET name=#{name},updated_at=NOW() WHERE version=#{version} AND status='DRAFT'")
    void rename(@Param("version") String version,@Param("name") String name);
    @Select(value="INSERT INTO app.mr_classification_rule_set(name,version,created_by,created_by_id) VALUES(#{name},#{version},#{actor},#{actorId}) RETURNING id",affectData=true)
    Long createRules(@Param("name") String name,@Param("version") String version,@Param("actor") String actor,@Param("actorId") Long actorId);
    @Select(value="INSERT INTO app.mr_ocr_lexicon_set(name,version,created_by,created_by_id) VALUES(#{name},#{version},#{actor},#{actorId}) RETURNING id",affectData=true)
    Long createLexicon(@Param("name") String name,@Param("version") String version,@Param("actor") String actor,@Param("actorId") Long actorId);
    @Insert("""
        INSERT INTO app.mr_classification_rule_entry(rule_set_id,target_type,keyword,match_scope,match_mode,weight,negative,required,priority,enabled)
        VALUES(#{set},#{r.targetType},#{r.keyword},#{r.matchScope},#{r.matchMode},#{r.weight},#{r.negative},#{r.required},#{r.priority},#{r.enabled})
        """) void rule(@Param("set") Long set,@Param("r") Rule rule);
    @Insert("INSERT INTO app.mr_ocr_lexicon_entry(lexicon_set_id,term,aliases,common_ocr_errors) VALUES(#{set},#{term},CAST(#{aliases} AS jsonb),CAST(#{errors} AS jsonb))")
    void lexicon(@Param("set") Long set,@Param("term") String term,@Param("aliases") String aliases,@Param("errors") String errors);
    @Delete("DELETE FROM app.mr_classification_rule_entry WHERE rule_set_id=(SELECT id FROM app.mr_classification_rule_set WHERE version=#{version} AND status='DRAFT')") void clearRules(String version);
    @Delete("DELETE FROM app.mr_ocr_lexicon_entry WHERE lexicon_set_id=(SELECT id FROM app.mr_ocr_lexicon_set WHERE version=#{version} AND status='DRAFT')") void clearLexicon(String version);
    @Select("SELECT id FROM app.mr_classification_rule_set WHERE version=#{version}") Long ruleId(String version);
    @Select("SELECT id FROM app.mr_ocr_lexicon_set WHERE version=#{version}") Long lexiconId(String version);
    @Select("SELECT pg_try_advisory_xact_lock(1804519)") boolean publicationLock();
    @Update("UPDATE app.mr_classification_rule_set SET status='ARCHIVED',updated_at=NOW() WHERE status='PUBLISHED'") void archiveRules();
    @Update("UPDATE app.mr_ocr_lexicon_set SET status='ARCHIVED',updated_at=NOW() WHERE status='PUBLISHED'") void archiveLexicon();
    @Update("UPDATE app.mr_classification_rule_set SET status='PUBLISHED',published_by=#{actor},published_at=NOW(),updated_at=NOW() WHERE version=#{version} AND status='DRAFT'")
    int publishRules(@Param("version") String version,@Param("actor") String actor);
    @Update("UPDATE app.mr_ocr_lexicon_set SET status='PUBLISHED',published_by=#{actor},published_at=NOW(),updated_at=NOW() WHERE version=#{version} AND status='DRAFT'")
    int publishLexicon(@Param("version") String version,@Param("actor") String actor);
}
