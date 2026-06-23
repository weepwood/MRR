package com.zjcxph.imgapi.common;

import java.util.List;

public final class Permissions {

    private Permissions() {
    }

    public static final List<String> ALL_PERMISSIONS = List.of(
            "record:manage",
            "record:edit",
            "record:read",
            "search:read",
            "statistics:read",
            "user:manage",
            "role:manage",
            "role:read",
            "log:read",
            "system:read",
            "test:read"
    );
}
