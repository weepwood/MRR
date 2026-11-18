package com.zjcxph.imgapi.repository;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

/**
 * 受控浏览的数据访问层。
 *
 * <p>只执行调用方组装好的只读 SELECT，不做任何表名或列名的补全：
 * SQL 结构由 {@code DatabaseBrowseService} 依注册表白名单生成进来。</p>
 *
 * <p>使用专用 {@link JdbcTemplate} 收口语句超时。未新建连接池：生产尚未定义独立只读账号，
 * 新建池需要部署侧新增配置，列为后续优化项（ADR-0007）。</p>
 */
@Repository
public class DatabaseBrowseRepository {

    private static final int QUERY_TIMEOUT_SECONDS = 3;

    private final JdbcTemplate browseTemplate;

    public DatabaseBrowseRepository(DataSource dataSource) {
        this.browseTemplate = new JdbcTemplate(dataSource);
        this.browseTemplate.setQueryTimeout(QUERY_TIMEOUT_SECONDS);
    }

    public List<Map<String, Object>> query(String sql, List<Object> args) {
        return browseTemplate.queryForList(sql, args.toArray());
    }

    /**
     * 读取行数估算。绝不退化为 {@code count(*)}。
     *
     * <p>优先用 {@code pg_class.reltuples}（ANALYZE 的产物）。{@code reltuples = -1}
     * 表示这张表从未被 ANALYZE 过，此时退回统计收集器的 {@code n_live_tup}。</p>
     *
     * <p>不要用 {@code relpages = 0} 判断「空表」：{@code pg_class.relpages} 只在
     * VACUUM/ANALYZE 时更新，新建表插入数据后仍是 0。实测 {@code flyway_schema_history}
     * 44 行、{@code mr_auth_role} 4 行，两张表都是 {@code reltuples=-1, relpages=0}，
     * 按 relpages 推断会把它们显示成「0 行」。</p>
     */
    public Long estimateRows(String schema, String table) {
        try {
            List<Map<String, Object>> rows = browseTemplate.queryForList("""
                    SELECT c.reltuples::bigint AS tuples,
                           COALESCE(s.n_live_tup, 0) AS live_tuples,
                           CASE WHEN s.relid IS NULL THEN 0 ELSE 1 END AS has_stats
                    FROM pg_class c
                    JOIN pg_namespace n ON n.oid = c.relnamespace
                    LEFT JOIN pg_stat_user_tables s ON s.relid = c.oid
                    WHERE n.nspname = ? AND c.relname = ?
                    """, schema, table);
            if (rows.isEmpty()) {
                return null;
            }
            Map<String, Object> row = rows.get(0);
            Number tuples = (Number) row.get("tuples");
            if (tuples != null && tuples.longValue() >= 0) {
                return tuples.longValue();
            }
            Number liveTuples = (Number) row.get("live_tuples");
            Number hasStats = (Number) row.get("has_stats");
            if (hasStats != null && hasStats.intValue() == 1 && liveTuples != null) {
                return Math.max(liveTuples.longValue(), 0L);
            }
            return null;
        } catch (DataAccessException e) {
            return null;
        }
    }
}
