package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.ArchiveAccessAttributes;
import com.zjcxph.imgapi.service.ImageContentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;


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
        bindAuditTarget(content.auditTarget());

        var request = RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes
                ? attributes.getRequest() : null;
        return ImageContentResponse.create(request, content, () -> {}, () -> {});
    }

    /** 让逐图访问审计与档案袋列表审计使用同一口径的目标，便于在影像访问审计中按病案检索。 */
    private static void bindAuditTarget(String auditTarget) {
        if (auditTarget != null
                && RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            attributes.getRequest().setAttribute(ArchiveAccessAttributes.AUDIT_TARGET, auditTarget);
        }
    }
}
