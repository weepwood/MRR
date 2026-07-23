package com.zjcxph.imgapi.utils;

import com.zjcxph.imgapi.common.Permissions;

import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 只处理少量、明确的上级权限继承关系。
 */
public final class PermissionResolver {

    private static final Map<String, Set<String>> HIERARCHY = Map.of(
            Permissions.RECORD_MANAGE, Set.of(
                    Permissions.RECORD_MANAGE,
                    Permissions.RECORD_EDIT,
                    Permissions.RECORD_READ,
                    Permissions.RECORD_DOWNLOAD,
                    Permissions.RECORD_PDF_EXPORT
            ),
            Permissions.RECORD_EDIT, Set.of(Permissions.RECORD_EDIT, Permissions.RECORD_READ),
            Permissions.RECORD_DOWNLOAD, Set.of(Permissions.RECORD_DOWNLOAD, Permissions.RECORD_READ),
            Permissions.RECORD_PDF_EXPORT, Set.of(Permissions.RECORD_PDF_EXPORT, Permissions.RECORD_READ),
            Permissions.RECORD_READ, Set.of(Permissions.RECORD_READ),
            Permissions.ROLE_MANAGE, Set.of(Permissions.ROLE_MANAGE, Permissions.ROLE_READ),
            Permissions.ROLE_READ, Set.of(Permissions.ROLE_READ),
            Permissions.SYSTEM_MANAGE, Set.of(Permissions.SYSTEM_MANAGE, Permissions.SYSTEM_READ),
            Permissions.SYSTEM_READ, Set.of(Permissions.SYSTEM_READ)
    );

    private PermissionResolver() {
    }

    public static Set<String> resolve(Collection<String> permissions) {
        Set<String> resolved = new HashSet<>();
        if (permissions == null) {
            return resolved;
        }

        for (String permission : permissions) {
            if (permission == null || permission.isBlank()) {
                continue;
            }
            String normalized = permission.trim();
            resolved.addAll(HIERARCHY.getOrDefault(normalized, Set.of(normalized)));
        }
        return resolved;
    }

    public static boolean hasPermission(Collection<String> permissions, String target) {
        return target != null
                && !target.isBlank()
                && resolve(permissions).contains(target.trim());
    }
}
