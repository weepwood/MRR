package com.zjcxph.imgapi.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 统一分页请求参数
 */
@Data
@Schema(description = "分页请求参数")
public class PageRequest {

    @Schema(description = "页码，从1开始", example = "1", defaultValue = "1")
    private int page = 1;

    @Schema(description = "每页大小", example = "10", defaultValue = "10")
    private int size = 10;

    public PageRequest() {
    }

    public PageRequest(int page, int size) {
        this.page = page;
        this.size = size;
    }

    /**
     * 验证分页参数的合法性
     */
    public void validate() {
        if (page < 1) {
            throw new IllegalArgumentException("页码必须大于等于1");
        }
        if (size < 1 || size > 1000) {
            throw new IllegalArgumentException("每页大小必须在1-1000之间");
        }
    }
}
