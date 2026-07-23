package com.zjcxph.imgapi.service;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 管理运维人员主动开启的维护只读模式。
 *
 * <p>状态持久化到系统设置表，同时保留内存快照，避免写请求拦截器每次访问数据库。</p>
 */
@Service
public class MaintenanceModeService {

    static final String KEY_ENABLED = "operationsMaintenanceEnabled";
    static final String KEY_REASON = "operationsMaintenanceReason";
    static final String KEY_UPDATED_AT = "operationsMaintenanceUpdatedAt";
    static final String KEY_UPDATED_BY = "operationsMaintenanceUpdatedBy";

    private static final int MAX_REASON_LENGTH = 300;

    private final SystemSettingService systemSettingService;
    private volatile Snapshot snapshot = Snapshot.disabled();

    public MaintenanceModeService(SystemSettingService systemSettingService) {
        this.systemSettingService = systemSettingService;
    }

    @PostConstruct
    public void initialize() {
        snapshot = loadSnapshot();
    }

    public boolean isEnabled() {
        return snapshot.enabled();
    }

    public Map<String, Object> getStatus() {
        return snapshot.toMap();
    }

    public synchronized Map<String, Object> enable(String reason, String actor) {
        String normalizedReason = normalizeReason(reason);
        String normalizedActor = normalizeActor(actor);
        Snapshot next = new Snapshot(true, normalizedReason, Instant.now().toString(), normalizedActor);
        persist(next);
        snapshot = next;
        return next.toMap();
    }

    public synchronized Map<String, Object> disable(String actor) {
        Snapshot next = new Snapshot(false, "", Instant.now().toString(), normalizeActor(actor));
        persist(next);
        snapshot = next;
        return next.toMap();
    }

    private Snapshot loadSnapshot() {
        try {
            boolean enabled = Boolean.parseBoolean(systemSettingService.getSetting(KEY_ENABLED));
            String reason = defaultString(systemSettingService.getSetting(KEY_REASON));
            String updatedAt = defaultString(systemSettingService.getSetting(KEY_UPDATED_AT));
            String updatedBy = defaultString(systemSettingService.getSetting(KEY_UPDATED_BY));
            return new Snapshot(enabled, reason, updatedAt, updatedBy);
        } catch (Exception ignored) {
            return Snapshot.disabled();
        }
    }

    private void persist(Snapshot value) {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put(KEY_ENABLED, Boolean.toString(value.enabled()));
        settings.put(KEY_REASON, value.reason());
        settings.put(KEY_UPDATED_AT, value.updatedAt());
        settings.put(KEY_UPDATED_BY, value.updatedBy());
        systemSettingService.saveSettings(settings, value.updatedBy());
    }

    private String normalizeReason(String reason) {
        String normalized = StringUtils.hasText(reason) ? reason.trim() : "计划维护";
        if (normalized.length() > MAX_REASON_LENGTH) {
            return normalized.substring(0, MAX_REASON_LENGTH);
        }
        return normalized;
    }

    private String normalizeActor(String actor) {
        return StringUtils.hasText(actor) ? actor.trim() : "unknown";
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private record Snapshot(boolean enabled, String reason, String updatedAt, String updatedBy) {

        static Snapshot disabled() {
            return new Snapshot(false, "", "", "");
        }

        Map<String, Object> toMap() {
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("enabled", enabled);
            result.put("reason", reason);
            result.put("updatedAt", updatedAt);
            result.put("updatedBy", updatedBy);
            return Map.copyOf(result);
        }
    }
}
