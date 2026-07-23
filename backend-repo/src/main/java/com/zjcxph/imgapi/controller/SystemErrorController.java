package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.SystemErrorStatusRequest;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.dto.resp.SystemErrorOverviewDTO;
import com.zjcxph.imgapi.entity.SystemErrorEvent;
import com.zjcxph.imgapi.service.SystemErrorEventService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.PaginationUtils;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/system-errors")
@RequirePermissions(Permissions.SYSTEM_ERROR_READ)
public class SystemErrorController {

    private static final int MAX_PAGE_SIZE = 200;

    private final SystemErrorEventService service;

    public SystemErrorController(SystemErrorEventService service) {
        this.service = service;
    }

    @GetMapping
    public Result<PageResult<SystemErrorEvent>> search(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String module
    ) {
        PaginationUtils.validatePageParams(page, size);
        int safeSize = Math.min(size, MAX_PAGE_SIZE);
        List<SystemErrorEvent> list = service.search(keyword, level, status, module, page, safeSize);
        long total = service.count(keyword, level, status, module);
        return Result.<PageResult<SystemErrorEvent>>success()
                .data(PageResult.of(list, total, page, safeSize));
    }

    @GetMapping("/overview")
    public Result<SystemErrorOverviewDTO> overview() {
        return Result.<SystemErrorOverviewDTO>success().data(service.overview());
    }

    @GetMapping("/{id}")
    public Result<SystemErrorEvent> detail(@PathVariable long id) {
        SystemErrorEvent event = service.findById(id);
        if (event == null) {
            return Result.fail("运行错误事件不存在");
        }
        return Result.<SystemErrorEvent>success().data(event);
    }

    @PostMapping("/{id}/status")
    @RequirePermissions(Permissions.SYSTEM_ERROR_MANAGE)
    public Result<Void> updateStatus(
            @PathVariable long id,
            @Valid @RequestBody SystemErrorStatusRequest request
    ) {
        AuthSession currentUser = AuthContext.getCurrentUser();
        String username = currentUser == null ? "system" : currentUser.getUsername();
        if (!service.updateStatus(id, request.getStatus(), username)) {
            return Result.fail("运行错误事件不存在");
        }
        return Result.success("处理状态已更新");
    }
}
