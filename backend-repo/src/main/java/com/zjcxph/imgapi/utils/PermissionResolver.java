package com.zjcxph.imgapi.utils;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class PermissionResolver {

    private static final Map<String, Set<String>> HIERARCHY = new HashMap<>();

    static {
        HIERARCHY.put("record:manage", Set.of(
                "record:manage",
                "record:edit",
                "record:read",
                "record:download",
                "record:pdf:export"
        ));
        HIERARCHY.put("record:edit", Set.of("record:edit", "record:read"));
        HIERARCHY.put("record:download", Set.of("record:download", "record:read"));
        HIERARCHY.put("record:pdf:export", Set.of("record:pdf:export", "record:read"));
        HIERARCHY.put("record:read", Set.of("record:read"));
        HIERARCHY.put("role:manage", Set.of("role:manage", "role:read"));
        HIERARCHY.put("role:read", Set.of("role:read"));
        HIERARCHY.put("system:manage", Set.of("system:manage", "system:read"));
        HIERARCHY.put("system:read", Set.of("system:read"));
    }

    public static Set<String> resolve(Collection<String> permissions) {
        Set<String> resolved = new HashSet<>();
        if (permissions == null || permissions.isEmpty()) {
            return resolved;
        }
        for (String perm : permissions) {
            if (perm != null && HIERARCHY.containsKey(perm.trim())) {
                resolved.addAll(HIERARCHY.get(perm.trim()));
            } else if (perm != null) {
                resolved.add(perm.trim());
            }
        }
        return resolved;
    }

    public static boolean hasPermission(Collection<String> permissions, String target) {
        if (target == null || target.isEmpty()) {
            return false;
        }
        return resolve(permissions).contains(target);
    }
}
