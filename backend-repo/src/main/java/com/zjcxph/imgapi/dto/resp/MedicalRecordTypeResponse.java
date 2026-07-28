package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.domain.MedicalRecordType;

public record MedicalRecordTypeResponse(int value, String label) {

    public static MedicalRecordTypeResponse from(MedicalRecordType type) {
        return new MedicalRecordTypeResponse(type.getCode(), type.getLabel());
    }
}
