package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.ArchiveSearchHistoryRequest;
import com.zjcxph.imgapi.dto.resp.ArchiveSearchHistoryResponse;
import com.zjcxph.imgapi.service.ArchiveSearchHistoryService;
import com.zjcxph.imgapi.utils.AuthContext;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/archive-search-history")
@Tag(name = "Archive Search History", description = "住院病案搜索记录与收藏")
@RequirePermissions({"record:read"})
public class ArchiveSearchHistoryController {

    private final ArchiveSearchHistoryService service;

    public ArchiveSearchHistoryController(ArchiveSearchHistoryService service) {
        this.service = service;
    }

    @Operation(summary = "获取当前用户的病案搜索记录")
    @GetMapping
    public Result<List<ArchiveSearchHistoryResponse>> list() {
        return Result.success(service.list(currentUserId()).stream().map(ArchiveSearchHistoryResponse::from).toList());
    }

    @Operation(summary = "保存病案搜索记录")
    @PostMapping
    @RequirePermissions({"record:read"})
    public Result<ArchiveSearchHistoryResponse> create(@RequestBody ArchiveSearchHistoryRequest request) {
        try {
            return Result.success(ArchiveSearchHistoryResponse.from(service.create(currentUserId(), request)));
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }

    @Operation(summary = "更新病案收藏状态")
    @PutMapping("/{id}/favorite")
    @RequirePermissions({"record:read"})
    public Result<String> updateFavorite(@PathVariable Long id, @RequestBody ArchiveSearchHistoryRequest history) {
        if (id == null || history == null) {
            return Result.fail("收藏记录不能为空");
        }
        return service.updateFavorite(currentUserId(), id, history.isFavorite())
                ? Result.success("收藏状态已更新")
                : Result.fail("搜索记录不存在");
    }

    private Long currentUserId() {
        AuthSession session = AuthContext.getCurrentUser();
        if (session == null || session.getId() == null) {
            throw new IllegalStateException("未登录或会话已失效");
        }
        return session.getId();
    }

}
