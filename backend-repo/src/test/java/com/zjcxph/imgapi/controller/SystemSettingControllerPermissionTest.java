package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
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
        assertPermission("getSettingsSchema", new Class<?>[0], "system:read");
    }

    @Test
    void writeEndpointsShouldRequireSystemManage() throws Exception {
        assertPermission("saveSettings", new Class<?>[]{Map.class}, "system:manage");
        assertPermission("setSetting", new Class<?>[]{String.class, Map.class}, "system:manage");
        assertPermission("deleteSetting", new Class<?>[]{String.class}, "system:manage");
    }

    /**
     * 全局外观与导航外链需要被全体登录用户同步，
     * 因此只要求登录会话，不得要求 system:read（否则普通医生账号无法同步）。
     */
    @Test
    void uiSettingsEndpointShouldRequireAuthenticationOnly() throws Exception {
        Method method = SystemSettingController.class.getDeclaredMethod("getUiSettings");

        assertThat(method.getAnnotation(AuthenticatedOnly.class))
                .as("getUiSettings 必须声明 AuthenticatedOnly")
                .isNotNull();
        assertThat(method.getAnnotation(RequirePermissions.class))
                .as("getUiSettings 不得要求业务权限")
                .isNull();
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
