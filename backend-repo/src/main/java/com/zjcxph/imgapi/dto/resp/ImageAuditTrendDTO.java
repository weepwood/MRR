package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ImageAuditTrendDTO {
    private LocalDate date;
    private long count;
}
