package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.utils.PaginationUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;

/**
 * 患者管理 Controller
 * 提供患者信息的分页查询、按病案号/身份证号查询等功能。
 */
@RestController
@RequestMapping("/api/v1/patients")
@Tag(name = "Patient Management", description = "患者信息管理接口")
@RequirePermissions({"record:read"})
public class PatientController {

    private final SearchMapper searchMapper;

    public PatientController(SearchMapper searchMapper) {
        this.searchMapper = searchMapper;
    }

    @Operation(summary = "分页查询患者列表（支持关键字搜索）")
    @GetMapping
    public Result<PageResult<Patient>> listPatients(
            @Parameter(description = "页码", example = "1")
            @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小", example = "20")
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "关键字（匹配病案号/姓名/身份证号/科室）")
            @RequestParam(required = false) String keyword
    ) {
        PaginationUtils.validatePageParams(page, size);
        String normalizedKeyword = keyword != null ? keyword.trim() : null;
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }
        int offset = (page - 1) * size;
        List<Patient> patients = searchMapper.findAllPaginated(offset, size, normalizedKeyword);
        int total = searchMapper.countAll(normalizedKeyword);
        return Result.<PageResult<Patient>>success().data(PageResult.of(patients, total, page, size));
    }

    @Operation(summary = "根据病案号查询患者信息")
    @GetMapping("/bah/{bah}")
    public Result<List<Patient>> getByBah(@PathVariable String bah) {
        List<Patient> patients = searchMapper.findPatientByBah(bah);
        return Result.<List<Patient>>success().data(patients);
    }

    @Operation(summary = "根据身份证号查询患者信息")
    @GetMapping("/idcard/{idCard}")
    public Result<List<Patient>> getByIdCard(@PathVariable String idCard) {
        List<Patient> patients = searchMapper.findBAHByIDCard(idCard);
        return Result.<List<Patient>>success().data(patients);
    }

    @Operation(summary = "导出患者列表为 Excel")
    @GetMapping("/export/excel")
    public void exportExcel(
            @Parameter(description = "关键字")
            @RequestParam(required = false) String keyword,
            HttpServletResponse response
    ) throws IOException {
        String normalizedKeyword = keyword != null ? keyword.trim() : null;
        if (normalizedKeyword != null && normalizedKeyword.isEmpty()) {
            normalizedKeyword = null;
        }

        List<Patient> patients = searchMapper.findAllPaginated(0, 10000, normalizedKeyword);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("患者列表");
            Row header = sheet.createRow(0);
            header.createCell(0).setCellValue("ID");
            header.createCell(1).setCellValue("病案号");
            header.createCell(2).setCellValue("姓名");
            header.createCell(3).setCellValue("身份证号");
            header.createCell(4).setCellValue("科室");
            header.createCell(5).setCellValue("入院时间");

            for (int i = 0; i < patients.size(); i++) {
                Patient p = patients.get(i);
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(p.getId() != null ? p.getId() : 0);
                row.createCell(1).setCellValue(p.getBah() != null ? p.getBah() : "");
                row.createCell(2).setCellValue(p.getName() != null ? p.getName() : "");
                row.createCell(3).setCellValue(p.getIdCard() != null ? p.getIdCard() : "");
                row.createCell(4).setCellValue(p.getDepartment() != null ? p.getDepartment() : "");
                row.createCell(5).setCellValue(p.getAdmissiontime() != null ? p.getAdmissiontime() : "");
            }

            for (int i = 0; i < 6; i++) {
                sheet.autoSizeColumn(i);
            }

            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=patients.xlsx");
            workbook.write(response.getOutputStream());
        }
    }
}
