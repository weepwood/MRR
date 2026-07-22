package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.AuthenticatedOnly;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.DocumentationAccessService;
import com.zjcxph.imgapi.service.DocumentationAccessService.AccessDecision;
import com.zjcxph.imgapi.utils.AuthContext;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/documentation")
@Tag(name = "Documentation", description = "文档中心访问会话")
public class DocumentationController {

    private final DocumentationAccessService documentationAccessService;

    public DocumentationController(DocumentationAccessService documentationAccessService) {
        this.documentationAccessService = documentationAccessService;
    }

    @PostMapping("/session")
    @AuthenticatedOnly
    @Operation(summary = "创建短期文档访问会话")
    public Result<Map<String, Object>> createSession(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response) {
        AuthSession session = AuthContext.getCurrentUser();
        String target = documentationAccessService.normalizeTarget(body == null ? null : body.get("target"));
        AccessDecision decision = documentationAccessService.authorizeSession(session, target);

        if (decision == AccessDecision.UNAUTHORIZED) {
            return Result.fail(401, "请先登录后访问文档");
        }
        if (decision == AccessDecision.FORBIDDEN) {
            return Result.fail(403, "当前账号没有访问内部文档的权限");
        }
        if (decision == AccessDecision.INVALID_TARGET) {
            return Result.fail(400, "不支持的文档地址");
        }

        String token = documentationAccessService.issueToken(session);
        boolean secure = request.isSecure()
                || "https".equalsIgnoreCase(request.getHeader("X-Forwarded-Proto"));
        ResponseCookie cookie = ResponseCookie.from(DocumentationAccessService.COOKIE_NAME, token)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofSeconds(DocumentationAccessService.SESSION_EXPIRE_SECONDS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("target", target);
        data.put("expiresIn", DocumentationAccessService.SESSION_EXPIRE_SECONDS);
        return Result.successWithData(data);
    }

    @GetMapping("/access")
    @Hidden
    public ResponseEntity<Void> checkAccess(
            HttpServletRequest request,
            @RequestHeader(value = "X-Original-URI", required = false) String originalUri) {
        String target = documentationAccessService.normalizeTarget(
                originalUri == null ? request.getRequestURI() : originalUri
        );
        String token = documentationAccessService.findAccessToken(request);
        AccessDecision decision = documentationAccessService.authorizeToken(token, target);

        return switch (decision) {
            case ALLOWED -> ResponseEntity.noContent().build();
            case FORBIDDEN -> ResponseEntity.status(HttpStatus.FORBIDDEN).build();
            case UNAUTHORIZED, INVALID_TARGET -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        };
    }
}
