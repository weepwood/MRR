package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.service.DocumentationAccessService;
import com.zjcxph.imgapi.service.DocumentationAccessService.AccessDecision;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DocumentationAccessServiceTest {

    private final DocumentationAccessService service = new DocumentationAccessService();

    @Test
    void allowsAuthenticatedUsersToReadUserGuide() {
        AuthSession session = sessionWithPermissions(List.of("record:read"));

        assertEquals(AccessDecision.ALLOWED, service.authorizeSession(session, "/docs/"));
        assertEquals(AccessDecision.ALLOWED, service.authorizeSession(session, "/docs/images.html"));
    }

    @Test
    void requiresSystemPermissionForInternalDocsAndOpenApi() {
        AuthSession normalUser = sessionWithPermissions(List.of("record:read"));
        AuthSession operator = sessionWithPermissions(List.of("system:read"));

        assertEquals(AccessDecision.FORBIDDEN, service.authorizeSession(normalUser, "/docs/internal/maintenance/"));
        assertEquals(AccessDecision.FORBIDDEN, service.authorizeSession(normalUser, "/api-docs/"));
        assertEquals(AccessDecision.FORBIDDEN, service.authorizeSession(normalUser, "/swagger-ui.html"));
        assertEquals(AccessDecision.FORBIDDEN, service.authorizeSession(normalUser, "/v3/api-docs.yaml"));
        assertEquals(AccessDecision.ALLOWED, service.authorizeSession(operator, "/docs/internal/maintenance/"));
        assertEquals(AccessDecision.ALLOWED, service.authorizeSession(operator, "/api-docs/"));
        assertEquals(AccessDecision.ALLOWED, service.authorizeSession(operator, "/swagger-ui.html"));
        assertEquals(AccessDecision.ALLOWED, service.authorizeSession(operator, "/v3/api-docs.yaml"));
    }

    @Test
    void rejectsUnknownOrPrefixLookalikeTargets() {
        AuthSession session = sessionWithPermissions(List.of("system:read"));

        assertEquals(AccessDecision.INVALID_TARGET, service.authorizeSession(session, "/admin"));
        assertEquals(AccessDecision.INVALID_TARGET, service.authorizeSession(session, "/docs-private"));
    }

    private AuthSession sessionWithPermissions(List<String> permissions) {
        AuthSession session = new AuthSession();
        session.setUsername("tester");
        session.setStatus("active");
        session.setRoleCode("USER");
        session.setPermissions(permissions);
        return session;
    }
}
