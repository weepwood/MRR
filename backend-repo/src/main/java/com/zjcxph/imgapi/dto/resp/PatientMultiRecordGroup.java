package com.zjcxph.imgapi.dto.resp;

import java.time.LocalDate;
import java.util.List;

public record PatientMultiRecordGroup(
        String matchType,
        String confidence,
        String patientName,
        String maskedIdCard,
        long recordCount,
        long archiveCount,
        List<String> archiveNumbers,
        LocalDate firstAdmissionDate,
        LocalDate lastAdmissionDate
) {
}
