package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

@Service
public class RuntimeLogQueryService {
    private final JdbcTemplate jdbc;
    public RuntimeLogQueryService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    public Map<String,Object> search(Instant from, Instant to, String level, String requestId, String errorId,
            String logger, Instant cursorTime, UUID cursorId, int size) {
        Instant end = to == null ? Instant.now() : to;
        Instant start = from == null ? end.minusSeconds(86400) : from;
        if (!start.isBefore(end) || end.getEpochSecond() - start.getEpochSecond() > 31L * 86400 || size < 1 || size > 200
                || (cursorTime == null) != (cursorId == null)) throw new BusinessException(400, "请选择31天内的时间范围和有效分页条件");
        if (level != null && !Set.of("TRACE", "DEBUG", "INFO", "WARN", "ERROR").contains(level)) throw new BusinessException(400, "日志级别无效");
        StringBuilder sql = new StringBuilder("SELECT occurred_at,event_id,level,logger_name,request_id,error_id,message_summary,stack_trace FROM app.runtime_log_event WHERE occurred_at>=? AND occurred_at<?");
        List<Object> args = new ArrayList<>(List.of(Timestamp.from(start), Timestamp.from(end)));
        for (var entry : new String[][]{{"level", level}, {"request_id", requestId}, {"error_id", errorId}, {"logger_name", logger}}) {
            if (entry[1] != null && !entry[1].isBlank()) {
                if (entry[1].length() > 255) throw new BusinessException(400, "筛选条件过长");
                sql.append(" AND ").append(entry[0]).append("=?"); args.add(entry[1]);
            }
        }
        if (cursorTime != null) { sql.append(" AND (occurred_at,event_id)<(?,?)"); args.add(Timestamp.from(cursorTime)); args.add(cursorId); }
        sql.append(" ORDER BY occurred_at DESC,event_id DESC LIMIT ?"); args.add(size + 1);
        List<Map<String, Object>> fetched = jdbc.query(sql.toString(), statement -> {
            statement.setQueryTimeout(3);
            for (int i = 0; i < args.size(); i++) statement.setObject(i + 1, args.get(i));
        }, (rs, n) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : List.of("occurred_at", "event_id", "level", "logger_name", "request_id", "error_id", "message_summary", "stack_trace")) row.put(key, key.equals("occurred_at") ? rs.getTimestamp(key).toInstant().toString() : rs.getString(key));
            return row;
        });
        boolean more = fetched.size() > size;
        List<Map<String, Object>> items = more ? fetched.subList(0, size) : fetched;
        return Map.of("list", items, "hasMore", more);
    }
}
