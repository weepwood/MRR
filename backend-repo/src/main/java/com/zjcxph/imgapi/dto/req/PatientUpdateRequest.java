package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 患者信息更新请求。
 */
@Data
public class PatientUpdateRequest {

    @NotBlank(message = "病案号不能为空")
    @Size(max = 2000, message = "病案号长度不能超过 2000 个字符")
    private String bah;

    @Size(max = 2000, message = "姓名长度不能超过 2000 个字符")
    private String name;

    @Size(max = 2000, message = "身份证号长度不能超过 2000 个字符")
    private String idCard;

    private LocalDate ruyuan;

    @Size(max = 64, message = "入院时间长度不能超过 64 个字符")
    private String admissiontime;

    @Size(max = 2000, message = "科室长度不能超过 2000 个字符")
    private String department;

    @Size(max = 2000, message = "病区长度不能超过 2000 个字符")
    private String bingqu;

    @Size(max = 2000, message = "床位长度不能超过 2000 个字符")
    private String chuangwei;
}
