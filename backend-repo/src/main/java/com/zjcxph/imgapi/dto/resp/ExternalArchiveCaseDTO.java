package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExternalArchiveCaseDTO {
    private String bah;
    private String sjh;
    private String patientName;
    private String department;
    private String admissionTime;
}
