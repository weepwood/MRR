package com.zjcxph.imgapi.mapper;

import com.zjcxph.imgapi.dto.resp.SystemErrorOverviewDTO;
import com.zjcxph.imgapi.entity.SystemErrorEvent;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

public interface SystemErrorEventMapper {

    @Insert("""
            INSERT INTO system_error_event (
                error_id, fingerprint, level, module, logger_name, exception_type,
                message_summary, stack_trace, request_id, thread_name,
                first_seen_at, last_seen_at, occurrence_count, status
            ) VALUES (
                #{errorId}, #{fingerprint}, #{level}, #{module}, #{loggerName}, #{exceptionType},
                #{messageSummary}, #{stackTrace}, #{requestId}, #{threadName},
                #{firstSeenAt}, #{lastSeenAt}, 1, 'OPEN'
            )
            ON CONFLICT (fingerprint) DO UPDATE SET
                level = EXCLUDED.level,
                module = EXCLUDED.module,
                logger_name = EXCLUDED.logger_name,
                exception_type = EXCLUDED.exception_type,
                message_summary = EXCLUDED.message_summary,
                stack_trace = EXCLUDED.stack_trace,
                request_id = EXCLUDED.request_id,
                thread_name = EXCLUDED.thread_name,
                last_seen_at = EXCLUDED.last_seen_at,
                occurrence_count = system_error_event.occurrence_count + 1,
                status = CASE
                    WHEN system_error_event.status = 'RESOLVED' THEN 'OPEN'
                    ELSE system_error_event.status
                END,
                resolved_at = CASE
                    WHEN system_error_event.status = 'RESOLVED' THEN NULL
                    ELSE system_error_event.resolved_at
                END
            """)
    int upsert(SystemErrorEvent event);

    @Select("""
            SELECT id,
                   error_id AS errorId,
                   fingerprint,
                   level,
                   module,
                   logger_name AS loggerName,
                   exception_type AS exceptionType,
                   message_summary AS messageSummary,
                   stack_trace AS stackTrace,
                   request_id AS requestId,
                   thread_name AS threadName,
                   first_seen_at AS firstSeenAt,
                   last_seen_at AS lastSeenAt,
                   occurrence_count AS occurrenceCount,
                   status,
                   acknowledged_by AS acknowledgedBy,
                   resolved_at AS resolvedAt
            FROM system_error_event
            WHERE (#{level} IS NULL OR level = #{level})
              AND (#{status} IS NULL OR status = #{status})
              AND (#{module} IS NULL OR module ILIKE '%' || #{module} || '%')
              AND (#{keyword} IS NULL OR (
                    error_id ILIKE '%' || #{keyword} || '%'
                    OR logger_name ILIKE '%' || #{keyword} || '%'
                    OR COALESCE(exception_type, '') ILIKE '%' || #{keyword} || '%'
                    OR message_summary ILIKE '%' || #{keyword} || '%'
                    OR COALESCE(request_id, '') ILIKE '%' || #{keyword} || '%'
              ))
            ORDER BY last_seen_at DESC, id DESC
            LIMIT #{limit} OFFSET #{offset}
            """)
    List<SystemErrorEvent> search(
            @Param("keyword") String keyword,
            @Param("level") String level,
            @Param("status") String status,
            @Param("module") String module,
            @Param("limit") int limit,
            @Param("offset") int offset
    );

    @Select("""
            SELECT COUNT(*)
            FROM system_error_event
            WHERE (#{level} IS NULL OR level = #{level})
              AND (#{status} IS NULL OR status = #{status})
              AND (#{module} IS NULL OR module ILIKE '%' || #{module} || '%')
              AND (#{keyword} IS NULL OR (
                    error_id ILIKE '%' || #{keyword} || '%'
                    OR logger_name ILIKE '%' || #{keyword} || '%'
                    OR COALESCE(exception_type, '') ILIKE '%' || #{keyword} || '%'
                    OR message_summary ILIKE '%' || #{keyword} || '%'
                    OR COALESCE(request_id, '') ILIKE '%' || #{keyword} || '%'
              ))
            """)
    long count(
            @Param("keyword") String keyword,
            @Param("level") String level,
            @Param("status") String status,
            @Param("module") String module
    );

    @Select("""
            SELECT id,
                   error_id AS errorId,
                   fingerprint,
                   level,
                   module,
                   logger_name AS loggerName,
                   exception_type AS exceptionType,
                   message_summary AS messageSummary,
                   stack_trace AS stackTrace,
                   request_id AS requestId,
                   thread_name AS threadName,
                   first_seen_at AS firstSeenAt,
                   last_seen_at AS lastSeenAt,
                   occurrence_count AS occurrenceCount,
                   status,
                   acknowledged_by AS acknowledgedBy,
                   resolved_at AS resolvedAt
            FROM system_error_event
            WHERE id = #{id}
            """)
    SystemErrorEvent findById(@Param("id") long id);

    @Select("""
            SELECT COUNT(*) AS "totalGroups",
                   COALESCE(SUM(occurrence_count), 0) AS "totalOccurrences",
                   COUNT(*) FILTER (WHERE status = 'OPEN') AS "openGroups",
                   COUNT(*) FILTER (WHERE status = 'ACKNOWLEDGED') AS "acknowledgedGroups",
                   COUNT(*) FILTER (WHERE status = 'RESOLVED') AS "resolvedGroups",
                   COUNT(*) FILTER (WHERE level = 'ERROR') AS "errorGroups",
                   COUNT(*) FILTER (WHERE level = 'WARN') AS "warnGroups",
                   COALESCE(SUM(occurrence_count) FILTER (WHERE last_seen_at >= NOW() - INTERVAL '24 hours'), 0)
                       AS "recentOccurrences"
            FROM system_error_event
            """)
    SystemErrorOverviewDTO overview();

    @Update("""
            UPDATE system_error_event
            SET status = #{status},
                acknowledged_by = #{username},
                resolved_at = CASE WHEN #{status} = 'RESOLVED' THEN NOW() ELSE NULL END
            WHERE id = #{id}
            """)
    int updateStatus(
            @Param("id") long id,
            @Param("status") String status,
            @Param("username") String username
    );
}
