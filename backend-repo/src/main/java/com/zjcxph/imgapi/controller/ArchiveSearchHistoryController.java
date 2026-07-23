package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.entity.ArchiveSearchHistory;
import com.zjcxph.imgapi.mapper.ArchiveSearchHistoryMapper;
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

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/archive-search-history")
@Tag(name = "Archive Search History", description = "住院病案搜索记录与收藏")
@RequirePermissions({"record:read"})
public class ArchiveSearchHistoryController {

    private final ArchiveSearchHistoryMapper mapper;

    public ArchiveSearchHistoryController(ArchiveSearchHistoryMapper mapper) {
        this.mapper = mapper;
    }

    @Operation(summary = "获取当前用户的病案搜索记录")
    @GetMapping
    public Result<List<ArchiveSearchHistory>> list() {
        return Result.success(mapper.findByUserId(currentUserId()));
    }

    @Operation(summary = "保存病案搜索记录")
    @PostMapping
    @RequirePermissions({"record:read"})
    public Result<ArchiveSearchHistory> create(@RequestBody ArchiveSearchHistory history) {
        if (history == null || (isBlank(history.getBah()) && isBlank(history.getSjh()))) {
            return Result.fail("病案号和上架号不能同时为空");
        }
        history.setId(null);
        history.setUserId(currentUserId());
        history.setImageCount(Math.max(0, history.getImageCount() == null ? 0 : history.getImageCount()));
        history.setQueryCount(history.isSuccess() ? Math.max(1, history.getQueryCount() == null ? 1 : history.getQueryCount()) : 0);
        history.setSearchedAt(history.getSearchedAt() == null ? LocalDateTime.now() : history.getSearchedAt());
        mapper.insert(history);
        return Result.success(history);
    }

    @Operation(summary = "更新病案收藏状态")
    @PutMapping("/{id}/favorite")
    @RequirePermissions({"record:read"})
    public Result<String> updateFavorite(@PathVariable Long id, @RequestBody ArchiveSearchHistory history) {
        if (id == null || history == null) {
            return Result.fail("收藏记录不能为空");
        }
        return mapper.updateFavorite(id, currentUserId(), history.isFavorite()) > 0
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

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
