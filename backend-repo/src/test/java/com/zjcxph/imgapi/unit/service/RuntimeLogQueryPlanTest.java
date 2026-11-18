package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.RuntimeLogQueryPlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 运行日志检索计划：把校验与 SQL 拼装从 JDBC 执行里拆出来，便于在无数据库环境下固定边界。
 */
class RuntimeLogQueryPlanTest {

    private static final Instant TO = Instant.parse("2026-11-19T00:00:00Z");
    private static final Instant FROM = TO.minusSeconds(3600);

    @Test
    @DisplayName("正文关键词转义 LIKE 通配符，避免用户输入变成通配符")
    void keywordEscapesLikeWildcards() {
        var plan = RuntimeLogQueryPlan.search(FROM, TO, null, null, "100%_\\x", null, null, null, null, null, 50);

        assertThat(plan.sql()).contains("message_summary ILIKE ? ESCAPE '\\'");
        assertThat(plan.args()).contains("%100\\%\\_\\\\x%");
    }

    @Test
    @DisplayName("正文关键词仅允许24小时窗口，长窗口必须改用编号定位")
    void keywordRequiresShortWindow() {
        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(TO.minus(Duration.ofHours(24).plusSeconds(1)), TO,
                null, null, "timeout", null, null, null, null, null, 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("24");

        // 恰好24小时仍允许，避免前端默认窗口被自己拒绝。
        assertThat(RuntimeLogQueryPlan.search(TO.minus(Duration.ofHours(24)), TO,
                null, null, "timeout", null, null, null, null, null, 50).sql()).contains("ILIKE");
    }

    @Test
    @DisplayName("最低级别展开为级别集合，且与精确级别互斥")
    void minLevelExpandsToLevelSet() {
        var plan = RuntimeLogQueryPlan.search(FROM, TO, null, "WARN", null, null, null, null, null, null, 50);
        assertThat(plan.sql()).contains("level IN (?,?)");
        assertThat(plan.args()).contains("WARN", "ERROR");

        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(FROM, TO, "WARN", "ERROR",
                null, null, null, null, null, null, 50))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("级别");
    }

    @Test
    @DisplayName("非法级别、过长条件、越界分页与游标半对均拒绝")
    void rejectsInvalidFilters() {
        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(FROM, TO, "FATAL", null,
                null, null, null, null, null, null, 50))
                .isInstanceOf(BusinessException.class).hasMessageContaining("级别");
        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(FROM, TO, null, null,
                "x".repeat(65), null, null, null, null, null, 50))
                .isInstanceOf(BusinessException.class).hasMessageContaining("关键词");
        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(FROM, TO, null, null,
                null, null, null, null, null, null, 201))
                .isInstanceOf(BusinessException.class).hasMessageContaining("分页");
        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(FROM, TO, null, null,
                null, null, null, null, TO, null, 50))
                .isInstanceOf(BusinessException.class).hasMessageContaining("分页");
        assertThatThrownBy(() -> RuntimeLogQueryPlan.search(FROM, TO, null, null,
                null, null, null, null, null, UUID.randomUUID(), 50))
                .isInstanceOf(BusinessException.class).hasMessageContaining("分页");
    }

    @Test
    @DisplayName("模块条件按包含匹配，兼容只记得类名的场景")
    void loggerMatchesPartially() {
        var plan = RuntimeLogQueryPlan.search(FROM, TO, null, null, null, null, null, "RecordService", null, null, 50);
        assertThat(plan.sql()).contains("logger_name ILIKE ? ESCAPE '\\'");
        assertThat(plan.args()).contains("%RecordService%");
    }

    @Test
    @DisplayName("时间范围默认最近24小时且不超过31天")
    void rangeDefaultsAndLimits() {
        Instant[] range = RuntimeLogQueryPlan.range(null, TO);
        assertThat(Duration.between(range[0], range[1])).isEqualTo(Duration.ofHours(24));

        assertThatThrownBy(() -> RuntimeLogQueryPlan.range(TO.minus(Duration.ofDays(32)), TO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("31");
        assertThatThrownBy(() -> RuntimeLogQueryPlan.range(TO, TO))
                .isInstanceOf(BusinessException.class).hasMessageContaining("31");
    }

    @Test
    @DisplayName("趋势分桶超过48小时改用天，控制点数")
    void bucketSwitchesToDayForLongRanges() {
        assertThat(RuntimeLogQueryPlan.bucket(Duration.ofHours(48))).isEqualTo("hour");
        assertThat(RuntimeLogQueryPlan.bucket(Duration.ofHours(49))).isEqualTo("day");
    }
}
