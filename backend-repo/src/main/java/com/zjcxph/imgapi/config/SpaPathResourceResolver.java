package com.zjcxph.imgapi.config;

import org.springframework.core.io.Resource;
import org.springframework.web.servlet.resource.PathResourceResolver;

import java.io.IOException;
import java.util.List;

/**
 * Resolves real static resources first and falls back to the bundled Vue entry
 * only for browser routes. API, actuator and documentation paths must keep
 * their normal 404 semantics instead of accidentally returning index.html.
 */
final class SpaPathResourceResolver extends PathResourceResolver {

    private static final List<String> RESERVED_PATHS = List.of(
            "api",
            "actuator",
            "swagger-ui",
            "v3/api-docs",
            "api-docs",
            "docs",
            "webjars",
            "error"
    );

    @Override
    protected Resource getResource(String resourcePath, Resource location) throws IOException {
        String normalizedPath = normalize(resourcePath);
        if (!normalizedPath.isEmpty()) {
            Resource requestedResource = super.getResource(normalizedPath, location);
            if (requestedResource != null) {
                return requestedResource;
            }
        }

        if (!isSpaRoute(normalizedPath)) {
            return null;
        }
        return super.getResource("index.html", location);
    }

    static boolean isSpaRoute(String resourcePath) {
        String normalizedPath = normalize(resourcePath);
        if (normalizedPath.contains(".") || normalizedPath.contains("\\")) {
            return false;
        }
        return RESERVED_PATHS.stream().noneMatch(path -> isSameOrChild(normalizedPath, path));
    }

    private static boolean isSameOrChild(String resourcePath, String reservedPath) {
        return resourcePath.equals(reservedPath) || resourcePath.startsWith(reservedPath + "/");
    }

    private static String normalize(String resourcePath) {
        if (resourcePath == null) {
            return "";
        }
        String normalized = resourcePath.strip();
        while (normalized.startsWith("/")) {
            normalized = normalized.substring(1);
        }
        return normalized;
    }
}
