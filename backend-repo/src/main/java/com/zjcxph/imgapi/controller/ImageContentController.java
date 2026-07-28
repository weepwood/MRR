package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.service.ImageContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/img/content")
@Tag(name = "Image Content Gateway", description = "按影像 ID 提供受控流式内容")
@RequirePermissions({"record:read"})
public class ImageContentController {

    private final ImageContentService imageContentService;

    public ImageContentController(ImageContentService imageContentService) {
        this.imageContentService = imageContentService;
    }

    @Operation(summary = "按影像 ID 读取受控影像内容")
    @GetMapping("/{id}")
    public ResponseEntity<StreamingResponseBody> getContent(
            @PathVariable
            @Parameter(description = "有效扫描记录 ID", example = "1")
            Integer id) {
        ImageContentService.ImageContent content = imageContentService.open(id);

        StreamingResponseBody body = outputStream -> {
            try (content) {
                content.inputStream().transferTo(outputStream);
            }
        };

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(content.mediaType());
        headers.setContentDisposition(ContentDisposition.inline()
                .filename(content.filename(), StandardCharsets.UTF_8)
                .build());
        headers.setCacheControl("private, no-store");
        headers.setPragma("no-cache");
        headers.set("X-Content-Type-Options", "nosniff");
        headers.set("Referrer-Policy", "no-referrer");
        if (content.contentLength() != null && content.contentLength() > 0) {
            headers.setContentLength(content.contentLength());
        }

        return ResponseEntity.ok()
                .headers(headers)
                .body(body);
    }
}
