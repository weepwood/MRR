package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.PatientUpdateRequest;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.PatientService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者信息编辑接口。
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patient Management", description = "患者信息管理接口")
@RequirePermissions({"record:read"})
public class PatientEditController {

    private final PatientService patientService;

    public PatientEditController(PatientService patientService) {
        this.patientService = patientService;
    }

    @Operation(summary = "根据 ID 更新患者信息")
    @PutMapping("/{id}")
    @RequirePermissions({"record:edit"})
    public Result<Patient> updatePatient(
            @PathVariable Integer id,
            @Valid @RequestBody PatientUpdateRequest request
    ) {
        try {
            Patient updated = patientService.update(id, request);
            return updated == null ? Result.fail("未找到患者记录") : Result.success("患者信息保存成功", updated);
        } catch (IllegalArgumentException exception) {
            return Result.fail(exception.getMessage());
        }
    }
}
