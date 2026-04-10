package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class IdCardQueryRequest {
    @NotBlank(message = "身份证号不能为空")
    @Pattern(regexp = "^\\d{15}(\\d{2}[0-9Xx])?$", message = "身份证号格式不正确")
    private String idCard;
}