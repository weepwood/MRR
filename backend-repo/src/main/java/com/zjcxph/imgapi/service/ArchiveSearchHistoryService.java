package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.ArchiveSearchHistoryRequest;
import com.zjcxph.imgapi.entity.ArchiveSearchHistory;
import com.zjcxph.imgapi.mapper.ArchiveSearchHistoryMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class ArchiveSearchHistoryService {
    private final ArchiveSearchHistoryMapper mapper;

    public ArchiveSearchHistoryService(ArchiveSearchHistoryMapper mapper) {
        this.mapper = mapper;
    }

    public List<ArchiveSearchHistory> list(Long userId) {
        return mapper.findByUserId(requireOwner(userId));
    }

    public ArchiveSearchHistory create(Long userId, ArchiveSearchHistoryRequest request) {
        if (request == null || (isBlank(request.getBah()) && isBlank(request.getSjh()))) {
            throw new IllegalArgumentException("病案号和上架号不能同时为空");
        }
        ArchiveSearchHistory history = new ArchiveSearchHistory();
        history.setUserId(requireOwner(userId));
        history.setBah(request.getBah());
        history.setSjh(request.getSjh());
        history.setSuccess(request.isSuccess());
        history.setImageCount(Math.max(0, request.getImageCount() == null ? 0 : request.getImageCount()));
        history.setQueryCount(request.isSuccess() ? Math.max(1, request.getQueryCount() == null ? 1 : request.getQueryCount()) : 0);
        history.setFailureReason(request.getFailureReason());
        history.setFavorite(request.isFavorite());
        history.setSearchedAt(request.getSearchedAt() == null ? LocalDateTime.now() : request.getSearchedAt());
        mapper.insert(history);
        return history;
    }

    public boolean updateFavorite(Long userId, Long id, boolean favorite) {
        return mapper.updateFavorite(id, requireOwner(userId), favorite) > 0;
    }

    private Long requireOwner(Long userId) {
        if (userId == null) { throw new IllegalStateException("未登录或会话已失效"); }
        return userId;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
