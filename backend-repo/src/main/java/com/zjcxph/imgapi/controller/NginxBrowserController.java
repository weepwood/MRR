package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.NginxBrowserPageDTO;
import com.zjcxph.imgapi.service.NginxBrowserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Nginx 静态资源只读文件浏览器。
 */
@RestController
@RequestMapping("/api/v1/nginx-browser")
@Tag(name = "Nginx Browser", description = "Nginx 静态资源目录与文件只读浏览接口")
@RequirePermissions({"record:read"})
public class NginxBrowserController {

    private static final Logger logger = LoggerFactory.getLogger(NginxBrowserController.class);

    private final NginxBrowserService nginxBrowserService;

    public NginxBrowserController(NginxBrowserService nginxBrowserService) {
        this.nginxBrowserService = nginxBrowserService;
    }

    @Operation(summary = "获取已配置的 Nginx 图片服务器")
    @GetMapping("/servers")
    public Result<List<NginxBrowserPageDTO.Server>> listServers() {
        try {
            return Result.success(nginxBrowserService.listServers());
        } catch (IllegalStateException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @Operation(summary = "浏览 Nginx 静态资源目录")
    @GetMapping
    public Result<NginxBrowserPageDTO> browse(
            @RequestParam(defaultValue = "default") String server,
            @RequestParam(defaultValue = "") String path,
            @RequestParam(defaultValue = "0") int offset,
            @RequestParam(defaultValue = "200") int limit
    ) {
        try {
            return Result.success(nginxBrowserService.browse(server, path, offset, limit));
        } catch (IllegalArgumentException | IllegalStateException | NginxBrowserService.NginxBrowserException exception) {
            return Result.fail(exception.getMessage());
        } catch (Exception exception) {
            logger.error("浏览 Nginx 目录失败：server={}, path={}", server, path, exception);
            return Result.fail("读取 Nginx 目录失败");
        }
    }

    @Operation(summary = "代理读取 Nginx 静态文件")
    @GetMapping("/file")
    public ResponseEntity<StreamingResponseBody> openFile(
            @RequestParam(defaultValue = "default") String server,
            @RequestParam String path
    ) {
        try {
            NginxBrowserService.RemoteFile remoteFile = nginxBrowserService.openFile(server, path);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(parseMediaType(remoteFile.contentType()));
            headers.setContentDisposition(ContentDisposition.inline()
                    .filename(remoteFile.fileName(), StandardCharsets.UTF_8)
                    .build());
            if (remoteFile.contentLength() >= 0) {
                headers.setContentLength(remoteFile.contentLength());
            }
            if (remoteFile.etag() != null && !remoteFile.etag().isBlank()) {
                headers.set(HttpHeaders.ETAG, remoteFile.etag());
            }
            if (remoteFile.lastModified() != null && !remoteFile.lastModified().isBlank()) {
                headers.set(HttpHeaders.LAST_MODIFIED, remoteFile.lastModified());
            }

            StreamingResponseBody body = output -> {
                try (remoteFile.inputStream()) {
                    remoteFile.inputStream().transferTo(output);
                }
            };
            return new ResponseEntity<>(body, headers, HttpStatus.OK);
        } catch (IllegalArgumentException exception) {
            return error(HttpStatus.BAD_REQUEST, exception.getMessage());
        } catch (IllegalStateException exception) {
            return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage());
        } catch (NginxBrowserService.NginxBrowserException exception) {
            logger.warn("读取 Nginx 文件失败：server={}, path={}, reason={}", server, path, exception.getMessage());
            return error(HttpStatus.BAD_GATEWAY, exception.getMessage());
        } catch (Exception exception) {
            logger.error("代理读取 Nginx 文件失败：server={}, path={}", server, path, exception);
            return error(HttpStatus.INTERNAL_SERVER_ERROR, "读取 Nginx 文件失败");
        }
    }

    private ResponseEntity<StreamingResponseBody> error(HttpStatus status, String message) {
        byte[] bytes = (message == null || message.isBlank() ? status.getReasonPhrase() : message)
                .getBytes(StandardCharsets.UTF_8);
        StreamingResponseBody body = output -> output.write(bytes);
        return ResponseEntity.status(status)
                .contentType(new MediaType("text", "plain", StandardCharsets.UTF_8))
                .contentLength(bytes.length)
                .body(body);
    }

    private MediaType parseMediaType(String value) {
        try {
            return value == null || value.isBlank()
                    ? MediaType.APPLICATION_OCTET_STREAM
                    : MediaType.parseMediaType(value);
        } catch (IllegalArgumentException exception) {
            return MediaType.APPLICATION_OCTET_STREAM;
        }
    }
}
