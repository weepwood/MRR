package com.zjcxph.imgapi.dto.resp;

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
@Schema(description = "OSS 文件资源管理器目录响应")
public class OssBrowserPageDTO {

    @Schema(description = "OSS 客户端是否已配置")
    private boolean configured;

    @Schema(description = "Bucket 名称")
    private String bucket;

    @Schema(description = "OSS Endpoint")
    private String endpoint;

    @Schema(description = "OSS Region")
    private String region;

    @Schema(description = "允许浏览的根前缀")
    private String rootPrefix;

    @Schema(description = "当前目录前缀")
    private String prefix;

    @Schema(description = "当前页目录和文件")
    private List<OssBrowserEntryDTO> entries;

    @Schema(description = "下一页 continuation token")
    private String nextContinuationToken;

    @Schema(description = "是否仍有下一页")
    private boolean truncated;

    @Schema(description = "本次最大返回数量")
    private int maxKeys;

    @Schema(description = "当前页目录数量")
    private int loadedDirectories;

    @Schema(description = "当前页文件数量")
    private int loadedFiles;

    @Schema(description = "当前页文件总字节数，不代表 Bucket 总大小")
    private long loadedBytes;
}
