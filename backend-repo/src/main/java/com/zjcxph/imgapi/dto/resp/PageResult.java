package com.zjcxph.imgapi.dto.resp;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "分页响应结果")
public class PageResult<T> {

    @Schema(description = "数据列表")
    private List<T> list;

    @Schema(description = "总记录数", example = "100")
    private long total;

    @Schema(description = "当前页码", example = "1")
    private int page;

    @Schema(description = "每页大小", example = "10")
    private int size;

    @Schema(description = "总页数", example = "10")
    private int totalPages;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "下一页游标时间；与 nextCursorId 成对使用")
    private String nextCursorAccessTime;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "下一页游标日志 ID；与 nextCursorAccessTime 成对使用")
    private Long nextCursorId;

    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) total / size);
        return PageResult.<T>builder()
                .list(list)
                .total(total)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .build();
    }

    public boolean hasNext() {
        return page < totalPages;
    }

    public boolean hasPrevious() {
        return page > 1;
    }

    public PageResult<T> withNextCursor(String accessTime, Long id) {
        this.nextCursorAccessTime = accessTime;
        this.nextCursorId = id;
        return this;
    }
}
