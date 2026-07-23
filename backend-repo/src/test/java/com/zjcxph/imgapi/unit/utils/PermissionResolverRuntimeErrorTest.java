package com.zjcxph.imgapi.unit.utils;

import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.utils.PermissionResolver;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

class PermissionResolverRuntimeErrorTest {

    @Test
    void systemManageShouldIncludeRuntimeErrorManagement() {
        Set<String> resolved = PermissionResolver.resolve(Set.of(Permissions.SYSTEM_MANAGE));

        assertTrue(resolved.contains(Permissions.SYSTEM_ERROR_MANAGE));
        assertTrue(resolved.contains(Permissions.SYSTEM_ERROR_READ));
    }

    @Test
    void runtimeErrorManageShouldIncludeReadPermission() {
        Set<String> resolved = PermissionResolver.resolve(Set.of(Permissions.SYSTEM_ERROR_MANAGE));

        assertTrue(resolved.contains(Permissions.SYSTEM_ERROR_READ));
    }
}
