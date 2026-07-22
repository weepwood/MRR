package com.zjcxph.imgapi.dto.resp;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "OSS 文件浏览条目")
public class OssBrowserEntryDTO {

    @Schema(description = "当前目录下显示名称")
    private String name;

    @Schema(description = "OSS Object Key 或目录前缀")
    private String key;

    @Schema(description = "是否为虚拟目录")
    private boolean directory;

    @Schema(description = "文件大小，目录固定为 0")
    private long size;

    @Schema(description = "最后修改时间，目录可能为空")
    private Instant lastModified;

    @Schema(description = "对象 ETag")
    private String etag;

    @Schema(description = "OSS 存储类型")
    private String storageClass;
}
