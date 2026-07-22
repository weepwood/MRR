package com.zjcxph.imgapi.unit.security;

import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("Controller API 访问策略完整性测试")
class ControllerAccessPolicyTest {

    private static final String CONTROLLER_PACKAGE = "com.zjcxph.imgapi.controller";
    private static final Set<RequestMethod> MUTATING_METHODS = EnumSet.of(
            RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH, RequestMethod.DELETE);

    @Test
    @DisplayName("每个 API Handler 必须明确属于公开、仅登录或指定权限之一")
    void everyApiHandlerHasExplicitAccessPolicy() throws Exception {
        List<String> violations = new ArrayList<>();
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));

        for (BeanDefinition candidate : scanner.findCandidateComponents(CONTROLLER_PACKAGE)) {
            if (candidate.getBeanClassName() == null) continue;
            Class<?> controllerClass = ClassUtils.forName(
                    candidate.getBeanClassName(), getClass().getClassLoader());
            inspectController(controllerClass, violations);
        }

        assertThat(violations)
                .as("发现未分类或冲突的 API 访问策略：%n%s", String.join("%n", violations))
                .isEmpty();
    }

    @Test
    @DisplayName("用户注册接口同时绕过登录和权限拦截器")
    void registrationEndpointRemainsPublic() {
        assertThat(ApiAccessPolicy.isAuthenticationExcluded(ApiAccessPolicy.REGISTRATION_PATH)).isTrue();
        assertThat(ApiAccessPolicy.isPublicApiPath(ApiAccessPolicy.REGISTRATION_PATH)).isTrue();
    }

    private void inspectController(Class<?> controllerClass, List<String> violations) {
        RequestMapping classMapping = AnnotatedElementUtils.findMergedAnnotation(controllerClass, RequestMapping.class);
        List<String> classPaths = mappingPaths(classMapping);

        for (Method method : controllerClass.getDeclaredMethods()) {
            RequestMapping methodMapping = AnnotatedElementUtils.findMergedAnnotation(method, RequestMapping.class);
            if (methodMapping == null) continue;

            List<String> paths = combinePaths(classPaths, mappingPaths(methodMapping));
            boolean allPublic = paths.stream().allMatch(ApiAccessPolicy::isPublicApiPath);
            boolean anyPublic = paths.stream().anyMatch(ApiAccessPolicy::isPublicApiPath);
            String handler = controllerClass.getSimpleName() + "#" + method.getName() + " " + paths;

            if (anyPublic && !allPublic) {
                violations.add(handler + " 同一 Handler 混合了公开和受保护路径");
                continue;
            }

            RequirePermissions methodPermissions = AnnotatedElementUtils.findMergedAnnotation(method, RequirePermissions.class);
            boolean methodAuthenticatedOnly = hasAuthenticatedOnly(method);
            if (allPublic) {
                if (methodPermissions != null || methodAuthenticatedOnly) {
                    violations.add(handler + " 已进入公开路径清单，不应再声明方法级认证策略");
                }
                continue;
            }

            List<RequestMethod> httpMethods = mappingMethods(methodMapping);
            boolean mutating = httpMethods.stream().anyMatch(MUTATING_METHODS::contains);
            boolean allCoveredByOverride = mutating && httpMethods.stream()
                    .filter(MUTATING_METHODS::contains)
                    .allMatch(httpMethod -> paths.stream().allMatch(path ->
                            ApiAccessPolicy.hasPermissionOverride(httpMethod.name(), path)));

            if (mutating && methodPermissions == null && !methodAuthenticatedOnly && !allCoveredByOverride) {
                violations.add(handler + " 是写请求，必须在方法级声明权限或受控权限覆盖");
                continue;
            }

            AccessDeclaration declaration = resolveDeclaration(
                    controllerClass, methodPermissions, methodAuthenticatedOnly);
            if (!declaration.declared() && !allCoveredByOverride) {
                violations.add(handler + " 未声明 @AuthenticatedOnly 或 @RequirePermissions");
            } else if (declaration.conflicting()) {
                violations.add(handler + " 同时声明了 @AuthenticatedOnly 和 @RequirePermissions");
            } else if (declaration.emptyPermissions()) {
                violations.add(handler + " 的 @RequirePermissions 不能为空");
            }
        }
    }

    private AccessDeclaration resolveDeclaration(Class<?> controllerClass,
                                                   RequirePermissions methodPermissions,
                                                   boolean methodAuthenticatedOnly) {
        if (methodPermissions != null || methodAuthenticatedOnly) {
            return new AccessDeclaration(methodPermissions, methodAuthenticatedOnly);
        }
        RequirePermissions classPermissions = AnnotatedElementUtils.findMergedAnnotation(
                controllerClass, RequirePermissions.class);
        return new AccessDeclaration(classPermissions, hasAuthenticatedOnly(controllerClass));
    }

    private boolean hasAuthenticatedOnly(java.lang.reflect.AnnotatedElement element) {
        return AnnotatedElementUtils.findMergedAnnotation(element, AuthenticatedOnly.class) != null;
    }

    private List<String> mappingPaths(RequestMapping mapping) {
        if (mapping == null) return List.of("");
        String[] paths = mapping.path().length > 0 ? mapping.path() : mapping.value();
        return paths.length == 0 ? List.of("") : Arrays.asList(paths);
    }

    private List<RequestMethod> mappingMethods(RequestMapping mapping) {
        if (mapping == null || mapping.method().length == 0) return List.of();
        return Arrays.asList(mapping.method());
    }

    private List<String> combinePaths(List<String> classPaths, List<String> methodPaths) {
        List<String> combined = new ArrayList<>();
        for (String classPath : classPaths) {
            for (String methodPath : methodPaths) {
                combined.add(combinePath(classPath, methodPath));
            }
        }
        return combined;
    }

    private String combinePath(String classPath, String methodPath) {
        String left = classPath == null ? "" : classPath.trim();
        String right = methodPath == null ? "" : methodPath.trim();
        String combined = ("/" + left + "/" + right).replaceAll("/{2,}", "/");
        if (combined.length() > 1 && combined.endsWith("/")) {
            return combined.substring(0, combined.length() - 1);
        }
        return combined;
    }

    private record AccessDeclaration(RequirePermissions permissions, boolean authenticatedOnly) {
        private boolean declared() { return permissions != null || authenticatedOnly; }
        private boolean conflicting() { return permissions != null && authenticatedOnly; }
        private boolean emptyPermissions() { return permissions != null && permissions.value().length == 0; }
    }
}
