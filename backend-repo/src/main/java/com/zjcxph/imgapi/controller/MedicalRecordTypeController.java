package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.domain.MedicalRecordType;
import com.zjcxph.imgapi.dto.resp.MedicalRecordTypeResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/medical-record-types")
@Tag(name = "Medical Record Type", description = "统一病案影像类型字典")
@RequirePermissions({"record:read"})
public class MedicalRecordTypeController {

    @GetMapping
    @Operation(summary = "获取完整病案影像类型字典")
    public Result<List<MedicalRecordTypeResponse>> list() {
        List<MedicalRecordTypeResponse> values = MedicalRecordType.orderedValues().stream()
                .map(MedicalRecordTypeResponse::from)
                .toList();
        return Result.successWithData(values);
    }
}
