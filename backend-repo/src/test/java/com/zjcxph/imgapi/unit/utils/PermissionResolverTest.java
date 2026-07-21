package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.utils.PermissionResolver;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PermissionResolver 权限层级解析测试")
class PermissionResolverTest {

    @Test
    @DisplayName("resolve — record:manage 展开为管理、编辑、查看与导出权限")
    void resolve_recordManage() {
        Set<String> result = PermissionResolver.resolve(List.of("record:manage"));
        assertThat(result).containsExactlyInAnyOrder(
                "record:manage",
                "record:edit",
                "record:read",
                "record:download",
                "record:pdf:export"
        );
    }

    @Test
    @DisplayName("resolve — record:edit 展开为 edit+read")
    void resolve_recordEdit() {
        Set<String> result = PermissionResolver.resolve(List.of("record:edit"));
        assertThat(result).containsExactlyInAnyOrder("record:edit", "record:read");
    }

    @Test
    @DisplayName("resolve — record:download 同时允许读取病案")
    void resolve_recordDownload() {
        Set<String> result = PermissionResolver.resolve(List.of("record:download"));
        assertThat(result).containsExactlyInAnyOrder("record:download", "record:read");
    }

    @Test
    @DisplayName("resolve — record:pdf:export 同时允许读取病案")
    void resolve_recordPdfExport() {
        Set<String> result = PermissionResolver.resolve(List.of("record:pdf:export"));
        assertThat(result).containsExactlyInAnyOrder("record:pdf:export", "record:read");
    }

    @Test
    @DisplayName("resolve — record:read 无子权限")
    void resolve_recordRead() {
        Set<String> result = PermissionResolver.resolve(List.of("record:read"));
        assertThat(result).containsExactly("record:read");
    }

    @Test
    @DisplayName("resolve — role:manage 展开为 manage+read")
    void resolve_roleManage() {
        Set<String> result = PermissionResolver.resolve(List.of("role:manage"));
        assertThat(result).containsExactlyInAnyOrder("role:manage", "role:read");
    }

    @Test
    @DisplayName("resolve — 无层级权限原样保留")
    void resolve_nonHierarchical() {
        Set<String> result = PermissionResolver.resolve(List.of("statistics:read", "log:read"));
        assertThat(result).containsExactlyInAnyOrder("statistics:read", "log:read");
    }

    @Test
    @DisplayName("resolve — 混合层级和非层级权限")
    void resolve_mixed() {
        Set<String> result = PermissionResolver.resolve(Arrays.asList("record:manage", "statistics:read", "role:read"));
        assertThat(result).containsExactlyInAnyOrder(
                "record:manage", "record:edit", "record:read", "record:download", "record:pdf:export",
                "statistics:read", "role:read");
    }

    @Test
    @DisplayName("resolve — null 入参返回空集")
    void resolve_null() {
        assertThat(PermissionResolver.resolve(null)).isEmpty();
    }

    @Test
    @DisplayName("resolve — 空列表返回空集")
    void resolve_empty() {
        assertThat(PermissionResolver.resolve(Collections.emptyList())).isEmpty();
    }

    @Test
    @DisplayName("resolve — 去重：重复权限只保留一份")
    void resolve_deduplication() {
        Set<String> result = PermissionResolver.resolve(Arrays.asList("record:read", "record:read", "record:edit"));
        assertThat(result).containsExactlyInAnyOrder("record:read", "record:edit");
    }

    @Test
    @DisplayName("resolve — trim 空白：前后空格被去除")
    void resolve_trimWhitespace() {
        Set<String> result = PermissionResolver.resolve(List.of("  record:read  "));
        assertThat(result).containsExactly("record:read");
    }

    @Test
    @DisplayName("hasPermission — record:manage 拥有者拥有 record:read")
    void hasPermission_manageImpliesRead() {
        assertThat(PermissionResolver.hasPermission(List.of("record:manage"), "record:read")).isTrue();
    }

    @Test
    @DisplayName("hasPermission — record:manage 拥有者拥有 ZIP/PDF 导出权限")
    void hasPermission_manageImpliesExports() {
        assertThat(PermissionResolver.hasPermission(List.of("record:manage"), "record:download")).isTrue();
        assertThat(PermissionResolver.hasPermission(List.of("record:manage"), "record:pdf:export")).isTrue();
    }

    @Test
    @DisplayName("hasPermission — record:read 不能下载或导出 PDF")
    void hasPermission_readDoesNotImplyExports() {
        assertThat(PermissionResolver.hasPermission(List.of("record:read"), "record:download")).isFalse();
        assertThat(PermissionResolver.hasPermission(List.of("record:read"), "record:pdf:export")).isFalse();
    }

    @Test
    @DisplayName("hasPermission — record:read 拥有者不拥有 record:manage")
    void hasPermission_readDoesNotImplyManage() {
        assertThat(PermissionResolver.hasPermission(List.of("record:read"), "record:manage")).isFalse();
    }

    @Test
    @DisplayName("hasPermission — null 目标返回 false")
    void hasPermission_nullTarget() {
        assertThat(PermissionResolver.hasPermission(List.of("record:read"), null)).isFalse();
    }

    @Test
    @DisplayName("hasPermission — 空目标返回 false")
    void hasPermission_emptyTarget() {
        assertThat(PermissionResolver.hasPermission(List.of("record:read"), "")).isFalse();
    }

    @Test
    @DisplayName("hasPermission — null 权限列表返回 false")
    void hasPermission_nullPermissions() {
        assertThat(PermissionResolver.hasPermission(null, "record:read")).isFalse();
    }
}
