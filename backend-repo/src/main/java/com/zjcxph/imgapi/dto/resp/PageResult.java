package com.zjcxph.imgapi.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 统一分页响应结果
 *
 * @param <T> 数据类型
 */
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

    /**
     * 创建分页结果
     */
    public static <T> PageResult<T> of(List<T> list, long total, int page, int size) {
        int totalPages = calculateTotalPages(total, size);
        return PageResult.<T>builder()
                .list(list)
                .total(total)
                .page(page)
                .size(size)
                .totalPages(totalPages)
                .build();
    }

    /**
     * 计算总页数
     */
    public static int calculateTotalPages(long total, int size) {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }

    /**
     * 判断是否有下一页
     */
    public boolean hasNext() {
        return page < totalPages;
    }

    /**
     * 判断是否有上一页
     */
    public boolean hasPrevious() {
        return page > 1;
    }
}
