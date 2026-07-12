package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditCountDTO {
    private String label;
    private long count;
}
