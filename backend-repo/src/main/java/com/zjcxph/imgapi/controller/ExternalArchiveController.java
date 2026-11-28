package com.zjcxph.imgapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ExternalArchiveTicketRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveSessionResponse;
import com.zjcxph.imgapi.dto.resp.ExternalArchiveTicketResponse;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.security.ExternalArchiveGrant;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ExternalArchiveAccessService;
import com.zjcxph.imgapi.service.ImageContentService;
import com.zjcxph.imgapi.utils.IpUtil;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

@RestController
public class ExternalArchiveController {

    private static final Logger logger = LoggerFactory.getLogger(ExternalArchiveController.class);

    private final ObjectMapper objectMapper;
    private final ExternalArchiveAccessService externalArchiveAccessService;
    private final ArchiveExportService archiveExportService;
    private final ImageContentService imageContentService;

    public ExternalArchiveController(
            ObjectMapper objectMapper,
            ExternalArchiveAccessService externalArchiveAccessService,
            ArchiveExportService archiveExportService,
            ImageContentService imageContentService
    ) {
        this.objectMapper = objectMapper;
        this.externalArchiveAccessService = externalArchiveAccessService;
        this.archiveExportService = archiveExportService;
        this.imageContentService = imageContentService;
    }

    @PostMapping("/api/v1/integration/archive/tickets")
    public Result<ExternalArchiveTicketResponse> createTicket(
            @RequestHeader(value = "X-MRR-Client-Id", required = false) String clientId,
            @RequestHeader(value = "X-MRR-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-MRR-Nonce", required = false) String nonce,
            @RequestHeader(value = "X-MRR-Signature", required = false) String signature,
            @RequestBody(required = false) String rawBody,
            HttpServletRequest servletRequest
    ) {
        if (!StringUtils.hasText(rawBody)) {
            throw new BusinessException(400, "请求体不能为空");
        }
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
                "External archive ticket issued: archives={}", issued.grant().cases().size()
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
        externalArchiveAccessService.requireAuditIntent(grant,null,null,"SESSION_CONTEXT",null,IpUtil.getClientIp(request));
        externalArchiveAccessService.recordContextOutcome(grant,IpUtil.getClientIp(request),requestId(request));
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
        if(!grant.allows(bah,sjh))throw new BusinessException(403,"当前外部会话无权访问该病案");
        externalArchiveAccessService.requireAuditIntent(grant,bah,sjh,"ARCHIVE_VIEW",null,IpUtil.getClientIp(request));
        List<BAHDataResponseDTO> images;
        try { images = externalArchiveAccessService.loadImages(grant, bah, sjh); }
        catch (RuntimeException failure) {
            auditPreparationFailure(grant,bah,sjh,"ARCHIVE_VIEW",null,request,failure);
            throw failure;
        }
        String clientIp = IpUtil.getClientIp(request);
        logger.info(
                "External archive viewed: images={}", images.size()
        );
        externalArchiveAccessService.recordAudit(
                grant, bah, sjh, "ARCHIVE_VIEW", null,
                clientIp, request.getHeader("User-Agent"), requestId(request),
                "SUCCESS", "images=" + images.size()
        );
        return Result.success(images);
    }

    @GetMapping("/api/v1/external/archive/download")
    public ResponseEntity<StreamingResponseBody> download(
            @RequestParam String bah,
            @RequestParam(required = false) String sjh,
            HttpServletRequest request
    ) {
        ExternalArchiveGrant grant = requireGrant(request);
        String normalizedBah = MedicalRecordCodeUtils.normalizeOrEmpty(bah);
        String normalizedSjh = MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
        if (!grant.allowDownload()) {
            throw new BusinessException(403, "当前外部会话未授予批量下载权限");
        }
        if (!grant.allows(normalizedBah, normalizedSjh)) {
            throw new BusinessException(403, "当前外部会话无权下载该病案");
        }

        externalArchiveAccessService.requireAuditIntent(grant,normalizedBah,normalizedSjh,"ARCHIVE_DOWNLOAD",null,IpUtil.getClientIp(request));
        ArchiveExportService.BatchZipExport export;
        try {
            export=archiveExportService.prepareExternalArchive(normalizedBah, normalizedSjh);
            if (export.itemCount() == 0) throw new BusinessException(404, "未找到匹配档案的图片");
        } catch (RuntimeException failure) {
            auditPreparationFailure(grant,normalizedBah,normalizedSjh,"ARCHIVE_DOWNLOAD",null,request,failure);
            throw failure;
        }

        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String currentRequestId = requestId(request);
        StreamingResponseBody body = outputStream -> {
            try {
                archiveExportService.writeBatchZip(export, outputStream);
                // SUCCESS 仅表示服务器完成输出流写入，不能证明客户端已将文件保存到磁盘。
                externalArchiveAccessService.recordAudit(
                        grant, normalizedBah, normalizedSjh, "ARCHIVE_DOWNLOAD", null,
                        clientIp, userAgent, currentRequestId, "SUCCESS", "images=" + export.itemCount());
            } catch (IOException | RuntimeException exception) {
                externalArchiveAccessService.recordAudit(
                        grant, normalizedBah, normalizedSjh, "ARCHIVE_DOWNLOAD", null,
                        clientIp, userAgent, currentRequestId, "FAILED", "images=" + export.itemCount());
                throw exception;
            }
        };
        String archiveCode = normalizedBah + (normalizedSjh.isEmpty() ? "" : "-" + normalizedSjh);
        String fileName = archiveCode + ".zip";
        String contentDisposition = ContentDisposition.attachment()
                .filename(fileName, StandardCharsets.UTF_8)
                .build()
                .toString();

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body);
    }

    @GetMapping("/api/v1/external/archive/image/{id}")
    public ResponseEntity<StreamingResponseBody> image(
            @PathVariable Integer id,
            HttpServletRequest request
    ) {
        ExternalArchiveGrant grant = requireGrant(request);
        externalArchiveAccessService.requireAuditIntent(grant,null,null,"IMAGE_VIEW",id,IpUtil.getClientIp(request));
        ImageContentService.ScopedImage scoped;
        try { scoped = imageContentService.openExternal(id, grant); }
        catch (RuntimeException failure) {
            auditPreparationFailure(grant,null,null,"IMAGE_VIEW",id,request,failure);
            throw failure;
        }
        ImageContentService.ImageContent content = scoped.content();
        String clientIp = IpUtil.getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        String currentRequestId = requestId(request);

        return ImageContentResponse.create(request, content,
                () -> externalArchiveAccessService.recordAudit(
                        grant, scoped.bah(), scoped.sjh(), "IMAGE_VIEW", id,
                        clientIp, userAgent, currentRequestId, "SUCCESS", null),
                () -> externalArchiveAccessService.recordAudit(
                        grant, scoped.bah(), scoped.sjh(), "IMAGE_VIEW", id,
                        clientIp, userAgent, currentRequestId, "FAILED", null));
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
            externalArchiveAccessService.auditRevocationIntent(grant,IpUtil.getClientIp(request));
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

    private void auditPreparationFailure(ExternalArchiveGrant grant,String bah,String sjh,String action,
                                         Integer imageId,HttpServletRequest request,RuntimeException failure) {
        String result=failure instanceof BusinessException business && business.getCode()==403?"DENIED":"FAILED";
        externalArchiveAccessService.recordAudit(grant,bah,sjh,action,imageId,IpUtil.getClientIp(request),
                request.getHeader("User-Agent"),requestId(request),result,"PREPARATION_FAILED");
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
        return request.getAttribute("requestId") instanceof String id ? id : null;
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return "https".equalsIgnoreCase(forwardedProto);
    }
}
