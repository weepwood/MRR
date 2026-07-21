package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 档案装箱记录创建/更新请求。
 */
@Data
public class ArchiveBoxRecordRequest {

    @Size(max = 64, message = "病案号长度不能超过 64 个字符")
    private String bah;

    @Size(max = 64, message = "上架号长度不能超过 64 个字符")
    private String sjh;

    @Size(max = 64, message = "箱号长度不能超过 64 个字符")
    private String boxNo;

    @Size(max = 64, message = "原计划箱号长度不能超过 64 个字符")
    private String expectedBoxNo;

    @Size(max = 32, message = "状态长度不能超过 32 个字符")
    private String status;

    @Size(max = 1000, message = "备注长度不能超过 1000 个字符")
    private String remark;
}
