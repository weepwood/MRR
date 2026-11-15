package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.config.LogRetentionProperties;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.service.impl.SystemSettingServiceImpl;
import com.zjcxph.imgapi.setting.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImageDirectoryRoutingSettingTest {
    private final SystemSettingService service = mock(SystemSettingService.class);
    private final SettingRegistry registry = new SettingRegistry(new LogRetentionProperties());
    private final SystemSettingController controller = new SystemSettingController(service, registry,
            new RuntimeSettingStore(mock(SystemSettingMapper.class), registry));

    @Test
    void schemaDoesNotDisguiseReadFailureAsBuiltInConfiguration() {
        when(service.getSetting(ImageDirectoryRoutingRules.KEY)).thenThrow(new IllegalStateException("synthetic"));
        assertThatThrownBy(controller::getSettingsSchema).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void schemaReturnsRawOverrideEvenWhenItIsBrokenSoAdministratorCanRestore() {
        when(service.getSetting(ImageDirectoryRoutingRules.KEY)).thenReturn("broken");
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) controller.getSettingsSchema().getData().get("items");
        assertThat(items.stream().filter(item -> ImageDirectoryRoutingRules.KEY.equals(item.get("key"))).findFirst().orElseThrow())
                .containsEntry("overrideValue", "broken").containsEntry("effectiveValue", "broken");
    }

    @Test
    void missingTargetAndInvalidBatchAreRejectedBeforeAnyDatabaseMutation() {
        var mapper = mock(SystemSettingMapper.class);
        var settings = new SystemSettingServiceImpl(mapper, mock(DeveloperModeService.class), mock(DeveloperApiAccessService.class),
                registry, new ImageProperties());
        String validSyntax = "[{\"type\":\"exact\",\"value\":\"2026.06.05\",\"node\":\"ba01\"}]";
        assertThatThrownBy(() -> settings.setSetting(ImageDirectoryRoutingRules.KEY, validSyntax, "synthetic-admin"))
                .hasMessageContaining("目标节点未配置");
        assertThatThrownBy(() -> settings.saveSettings(Map.of(ImageDirectoryRoutingRules.KEY, "[{}]", "systemName", "synthetic"), "synthetic-admin"))
                .isInstanceOf(com.zjcxph.imgapi.exception.BusinessException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void overlongBlankOverrideIsMarkedInvalidEvenIfEffectiveValueUsesDefault() {
        when(service.getSetting(ImageDirectoryRoutingRules.KEY)).thenReturn(" ".repeat(32769));
        @SuppressWarnings("unchecked")
        var items = (List<Map<String, Object>>) controller.getSettingsSchema().getData().get("items");
        assertThat(items.stream().filter(item -> ImageDirectoryRoutingRules.KEY.equals(item.get("key"))).findFirst().orElseThrow())
                .containsEntry("valid", false);
    }

    @Test
    void realSettingHandlersDenyAnonymousAndReadonlyWrites() throws Exception {
        var authorization = new AuthorizationInterceptor();
        for (String action : List.of("setSetting", "deleteSetting")) {
            String method = action.equals("setSetting") ? "PUT" : "DELETE";
            var javaMethod = action.equals("setSetting")
                    ? SystemSettingController.class.getMethod(action, String.class, Map.class)
                    : SystemSettingController.class.getMethod(action, String.class);
            var handler = new HandlerMethod(controller, javaMethod);
            var request = new MockHttpServletRequest(method, "/api/v1/settings/" + ImageDirectoryRoutingRules.KEY);
            var response = new MockHttpServletResponse();
            assertThat(authorization.preHandle(request, response, handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(401);
            var session = new AuthSession();
            session.setUsername("synthetic-admin"); session.setRoleCode("OPERATOR"); session.setPermissions(List.of("system:read"));
            request.setAttribute(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, session);
            response = new MockHttpServletResponse();
            assertThat(authorization.preHandle(request, response, handler)).isFalse();
            assertThat(response.getStatus()).isEqualTo(403);
            session.setPermissions(List.of("system:manage"));
            assertThat(authorization.preHandle(request, new MockHttpServletResponse(), handler)).isTrue();
        }
        verifyNoInteractions(service);
    }
}
