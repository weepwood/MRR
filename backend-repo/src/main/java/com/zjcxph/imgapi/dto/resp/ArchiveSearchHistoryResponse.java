package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.entity.ArchiveSearchHistory;
import java.time.LocalDateTime;

public record ArchiveSearchHistoryResponse(
        Long id, Long userId, String bah, String sjh, boolean success,
        Integer imageCount, Integer queryCount, String failureReason, boolean favorite,
        LocalDateTime searchedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static ArchiveSearchHistoryResponse from(ArchiveSearchHistory history) {
        return new ArchiveSearchHistoryResponse(history.getId(), history.getUserId(), history.getBah(),
                history.getSjh(), history.isSuccess(), history.getImageCount(), history.getQueryCount(),
                history.getFailureReason(), history.isFavorite(), history.getSearchedAt(),
                history.getCreatedAt(), history.getUpdatedAt());
    }
}
