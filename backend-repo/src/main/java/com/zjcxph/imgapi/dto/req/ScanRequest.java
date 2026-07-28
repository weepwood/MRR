package com.zjcxph.imgapi.dto.req;

import com.zjcxph.imgapi.validation.ValidScanRequest;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
@ValidScanRequest
public class ScanRequest {

    @NotBlank(message = "病人序号不能为空")
    @Size(max = 64, message = "病人序号不能超过 64 个字符")
    private String brxh;

    @NotBlank(message = "病案号不能为空")
    @Size(max = 64, message = "病案号不能超过 64 个字符")
    private String bah;

    @Size(max = 64, message = "上架号不能超过 64 个字符")
    private String sjh;

    @NotBlank(message = "文件名不能为空")
    @Size(max = 255, message = "文件名不能超过 255 个字符")
    private String filename;

    @NotNull(message = "病案类型不能为空")
    @Min(value = 0, message = "病案类型不能小于 0")
    @Max(value = 15, message = "病案类型不能大于 15")
    private Integer btype;

    @NotNull(message = "页码不能为空")
    @Min(value = 0, message = "页码不能小于 0")
    @Max(value = 100000, message = "页码不能大于 100000")
    private Integer pages;

    @Size(max = 64, message = "操作员编号不能超过 64 个字符")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "操作员编号不能包含控制字符")
    private String openerNo;

    @Size(max = 32, message = "上传日期不能超过 32 个字符")
    @Pattern(regexp = "^[^\\p{Cntrl}]*$", message = "上传日期不能包含控制字符")
    private String uploadDate;

    @NotNull(message = "上传状态不能为空")
    @Min(value = 0, message = "上传状态只能是 0 或 1")
    @Max(value = 1, message = "上传状态只能是 0 或 1")
    private Integer uploadFlag;

    @NotBlank(message = "图片目录不能为空")
    @Size(max = 255, message = "图片目录不能超过 255 个字符")
    private String folder;

    public ScanRequest() {
    }

    public ScanRequest(String brxh, String bah, String filename, Integer btype, Integer pages,
                       String openerNo, String uploadDate, Integer uploadFlag, String folder) {
        this.brxh = brxh;
        this.bah = bah;
        this.filename = filename;
        this.btype = btype;
        this.pages = pages;
        this.openerNo = openerNo;
        this.uploadDate = uploadDate;
        this.uploadFlag = uploadFlag;
        this.folder = folder;
    }
}
