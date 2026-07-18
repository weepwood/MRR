package com.zjcxph.imgapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ExternalArchiveTicketRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveSessionResponse;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveTicketResponse;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.security.ExternalArchiveGrant;
import com.zjcxph.imgapi.service.ExternalArchiveAccessService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.utils.IpUtil;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
public class ExternalArchiveController {

    private static final Logger logger = LoggerFactory.getLogger(ExternalArchiveController.class);

    private final ObjectMapper objectMapper;
    private final ExternalArchiveAccessService externalArchiveAccessService;
    private final ImageUrlService imageUrlService;

    public ExternalArchiveController(
            ObjectMapper objectMapper,
            ExternalArchiveAccessService externalArchiveAccessService,
            ImageUrlService imageUrlService
    ) {
        this.objectMapper = objectMapper;
        this.externalArchiveAccessService = externalArchiveAccessService;
        this.imageUrlService = imageUrlService;
    }

    @PostMapping("/api/v1/integration/archive/tickets")
    public Result<ExternalArchiveTicketResponse> createTicket(
            @RequestHeader(value = "X-MRR-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-MRR-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-MRR-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-MRR-Signature", required = false) String signature,
            @RequestBody String rawBody,
            HttpServletRequest servletRequest
    ) {
        ExternalArchiveTicketRequest request;
        try {
            request = objectMapper.readValue(rawBody, ExternalArchiveTicketRequest.class);
        } catch (Exception exception) {
            throw new BusinessException(400, "请求体不是有效的 JSON");
        }

        String clientIp = IpUtil.getClientIp(servletRequest);
        ExternalArchiveAccessService.IssuedTicket issued = externalArchiveAccessService.createTicket(
                clientId,
                timestamp,
                nonce,
                signature,
                servletRequest.getMethod(),
                servletRequest.getRequestURI(),
                rawBody,
                clientIp,
                request
        );
        String launchUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/archive/external")
                .queryParam("ticket", issued.token())
                .build(true)
                .toUriString();
        logger.info(
                "External archive ticket issued: clientId={}, externalUserId={}, archives={}, ip={}",
                issued.grant().clientId(), issued.grant().externalUserId(),
                issued.grant().cases().size(), clientIp
        );
        externalArchiveAccessService.recordAudit(
                issued.grant(), null, null, "TICKET_CREATE", null,
                clientIp, servletRequest.getHeader("User-Agent"), requestId(servletRequest),
                "SUCCESS", "archives=" + issued.grant().cases().size()
        );
        return Result.success(new ExternalArchiveTicketResponse(
                issued.token(), launchUrl, issued.expiresIn(), issued.grant().cases().size()
        ));
    }

    @PostMapping("/api/v1/external/archive/session")
    public Result<ExternalArchiveSessionResponse> exchangeTicket(
            @RequestBody(required = false) Map<String, String> body,
            HttpServletRequest request,
            HttpServletResponse response
    ) {
        String clientIp = IpUtil.getClientIp(request);
        String ticket = body == null ? null : body.get("ticket");
        ExternalArchiveAccessService.IssuedSession issued =
                externalArchiveAccessService.consumeTicket(ticket, clientIp);
        ResponseCookie cookie = ResponseCookie.from(
                        ExternalArchiveAccessService.SESSION_COOKIE_NAME,
                        issued.token()
                )
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/api/v1/external/archive")
                .maxAge(Duration.ofSeconds(issued.expiresIn()))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        response.setHeader("Referrer-Policy", "no-referrer");
        ExternalArchiveGrant grant = issued.grant();
        externalArchiveAccessService.recordAudit(
                grant, null, null, "SESSION_CREATE", null,
                clientIp, request.getHeader("User-Agent"), requestId(request),
                "SUCCESS", "expiresIn=" + issued.expiresIn()
        );
        return Result.success(new ExternalArchiveSessionResponse(
                grant.clientId(),
                grant.externalUserId(),
                grant.allowDownload(),
                issued.expiresIn(),
                grant.cases()
        ));
    }

    @GetMapping("/api/v1/external/archive/context")
    public Result<ExternalArchiveSessionResponse> context(HttpServletRequest request) {
        ExternalArchiveGrant grant = requireGrant(request);
        int expiresIn = Math.max(0, (int) ((grant.expiresAt() - System.currentTimeMillis()) / 1000L));
        return Result.success(new ExternalArchiveSessionResponse(
                grant.clientId(), grant.externalUserId(), grant.allowDownload(), expiresIn, grant.cases()
        ));
    }

    @GetMapping("/api/v1/external/archive/images")
    public Result<List<BAHDataResponseDTO>> images(
            @RequestParam String bah,
            @RequestParam(required = false) String sjh,
            HttpServletRequest request
    ) {
        ExternalArchiveGrant grant = requireGrant(request);
        List<BAHDataResponseDTO> images = externalArchiveAccessService.loadImages(grant, bah, sjh);
        String clientIp = IpUtil.getClientIp(request);
        logger.info(
                "External archive viewed: clientId={}, externalUserId={}, bah={}, sjh={}, images={}, ip={}",
                grant.clientId(), grant.externalUserId(), bah, sjh, images.size(), clientIp
        );
        externalArchiveAccessService.recordAudit(
                grant, bah, sjh, "ARCHIVE_VIEW", null,
                clientIp, request.getHeader("User-Agent"), requestId(request),
                "SUCCESS", "images=" + images.size()
        );
        return Result.success(images);
    }

    @GetMapping("/api/v1/external/archive/image/{id}")
    public ResponseEntity<Void> image(
            @PathVariable Integer id,
            HttpServletRequest request
    ) {
        ExternalArchiveGrant grant = requireGrant(request);
        Scan scan = externalArchiveAccessService.requireImage(grant, id);
        String location = imageUrlService.buildPreferredImageUrl(scan);
        if (!StringUtils.hasText(location)) {
            throw new BusinessException(404, "无法构造影像地址");
        }
        externalArchiveAccessService.recordAudit(
                grant, scan.getBah(), scan.getSjh(), "IMAGE_VIEW", id,
                IpUtil.getClientIp(request), request.getHeader("User-Agent"), requestId(request),
                "SUCCESS", null
        );
        return ResponseEntity.status(302)
                .location(URI.create(location))
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=300")
                .header("Referrer-Policy", "no-referrer")
                .build();
    }

    @PostMapping("/api/v1/external/archive/logout")
    public Result<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        String sessionToken = extractSessionToken(request);
        ExternalArchiveGrant grant = null;
        if (StringUtils.hasText(sessionToken)) {
            try {
                grant = externalArchiveAccessService.requireSession(sessionToken);
            } catch (BusinessException ignored) {
                // Cookie 仍需清理。
            }
            externalArchiveAccessService.revokeSession(sessionToken);
        }
        ResponseCookie cookie = ResponseCookie.from(ExternalArchiveAccessService.SESSION_COOKIE_NAME, "")
                .httpOnly(true)
                .secure(isSecureRequest(request))
                .sameSite("Lax")
                .path("/api/v1/external/archive")
                .maxAge(Duration.ZERO)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        externalArchiveAccessService.recordAudit(
                grant, null, null, "SESSION_LOGOUT", null,
                IpUtil.getClientIp(request), request.getHeader("User-Agent"), requestId(request),
                "SUCCESS", null
        );
        return Result.success("外部影像会话已退出");
    }

    private ExternalArchiveGrant requireGrant(HttpServletRequest request) {
        return externalArchiveAccessService.requireSession(extractSessionToken(request));
    }

    private String extractSessionToken(HttpServletRequest request) {
        return request.getCookies() == null
                ? null
                : Arrays.stream(request.getCookies())
                .filter(cookie -> ExternalArchiveAccessService.SESSION_COOKIE_NAME.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private String requestId(HttpServletRequest request) {
        return request.getHeader("X-Request-Id");
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}
