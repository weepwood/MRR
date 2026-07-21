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
@Schema(description = "游标分页响应结果")
public class CursorPageResult<T> {

    @Schema(description = "数据列表")
    private List<T> list;

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Schema(description = "下一页起始 ID；为空表示没有下一页")
    private Long nextCursorId;

    @Schema(description = "是否还有下一页")
    private boolean hasMore;

    @Schema(description = "本次请求的页大小")
    private int size;

    public static <T> CursorPageResult<T> of(List<T> list, Long nextCursorId, boolean hasMore, int size) {
        return CursorPageResult.<T>builder()
                .list(list)
                .nextCursorId(nextCursorId)
                .hasMore(hasMore)
                .size(size)
                .build();
    }
}
