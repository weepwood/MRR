package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SystemSettingControllerPermissionTest {

    @Test
    void readEndpointsShouldRequireSystemRead() throws Exception {
        assertPermission("getAllSettings", new Class<?>[0], "system:read");
        assertPermission("getSetting", new Class<?>[]{String.class}, "system:read");
    }

    @Test
    void writeEndpointsShouldRequireSystemManage() throws Exception {
        assertPermission("saveSettings", new Class<?>[]{Map.class}, "system:manage");
        assertPermission("setSetting", new Class<?>[]{String.class, Map.class}, "system:manage");
        assertPermission("deleteSetting", new Class<?>[]{String.class}, "system:manage");
    }

    private void assertPermission(String methodName, Class<?>[] parameterTypes, String expected) throws Exception {
        Method method = SystemSettingController.class.getDeclaredMethod(methodName, parameterTypes);
        RequirePermissions annotation = method.getAnnotation(RequirePermissions.class);

        assertThat(annotation)
                .as("%s must declare an explicit permission", methodName)
                .isNotNull();
        assertThat(annotation.value()).containsExactly(expected);
    }
}
