package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.IdCardQueryRequest;
import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.SearchService;
import com.zjcxph.imgapi.utils.IdCardUrlTokenUtil;
import com.zjcxph.imgapi.utils.IpUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "Search", description = "病案搜索与患者查询接口")
@RequirePermissions({"search:read"})
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("^\\d{15}(\\d{2}[0-9Xx])?$");

    private final SearchService searchService;

    @Value("${aes.secret.key}")
    private String secretKey;

    private static final String LEGACY_ID_CARD_QUERY_MESSAGE =
            "旧身份证查询接口已停用，请改用受控 POST 查询或外部 Ticket";

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/hello")
    public String hello(HttpServletRequest request) {
        return "hello world search, your IP is: " + IpUtil.getClientIp(request);
    }

    @Deprecated
    @Operation(summary = "旧版加密身份证查询（已停用）")
    @GetMapping("/getBAHByEncryptID")
    public Result<List<Patient>> getBAHByEncryptID() {
        return Result.fail(410, LEGACY_ID_CARD_QUERY_MESSAGE);
    }

    @Deprecated
    @Operation(summary = "旧版加密身份证查询（已停用）")
    @GetMapping("/getBAHByEncryptIDLegacy")
    public Result<List<Patient>> getBAHByEncryptIDLegacy() {
        return Result.fail(410, LEGACY_ID_CARD_QUERY_MESSAGE);
    }

    @Operation(summary = "通过身份证号查询全部影像档案，并生成 URL 安全令牌")
    @PostMapping("/archive-cases")
    @RequirePermissions({"search:read"})
    public Result<IdCardArchiveSearchResponse> getArchiveCasesByIdCard(
            @Valid @RequestBody IdCardQueryRequest request) {
        String idCard = request.getIdCard().trim();
        List<IdCardArchiveSearchResponse.ArchiveCase> cases = searchService.getArchiveCasesByID(idCard);
        String token = IdCardUrlTokenUtil.encrypt(idCard, secretKey);
        logger.info("Found {} archive cases by id-card", cases.size());
        return Result.success(new IdCardArchiveSearchResponse(
                token,
                IdCardUrlTokenUtil.mask(idCard),
                cases
        ));
    }

    @Operation(summary = "通过 URL 安全令牌恢复身份证影像档案查询")
    @GetMapping("/archive-cases")
    public Result<IdCardArchiveSearchResponse> getArchiveCasesByToken(@RequestParam("id") String token) {
        try {
            String idCard = IdCardUrlTokenUtil.decrypt(token, secretKey);
            if (!ID_CARD_PATTERN.matcher(idCard).matches()) {
                return Result.fail("身份证查询参数无效");
            }
            List<IdCardArchiveSearchResponse.ArchiveCase> cases = searchService.getArchiveCasesByID(idCard);
            logger.info("Restored {} archive cases from id-card URL token", cases.size());
            return Result.success(new IdCardArchiveSearchResponse(
                    token,
                    IdCardUrlTokenUtil.mask(idCard),
                    cases
            ));
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid id-card URL token: {}", e.getMessage());
            return Result.fail("身份证查询链接无效或已被篡改");
        }
    }

    @Operation(summary = "根据病案号查询患者信息")
    @GetMapping("/patient/{bah}")
    public Result<List<Patient>> getPatientByBah(@PathVariable String bah) {
        if (bah == null || bah.isEmpty()) {
            return Result.fail("病案号不能为空");
        }
        List<Patient> patients = searchService.getPatientByBah(bah);
        logger.info("Found {} patients for archive-number lookup", patients.size());
        return Result.success(patients);
    }

    @Deprecated
    @Operation(summary = "通过 URL 身份证号查询病案号（已停用）")
    @GetMapping("/getBAHByID/{idCard}")
    public Result<List<Patient>> getBAHByiDCard() {
        return Result.fail(410, LEGACY_ID_CARD_QUERY_MESSAGE);
    }

    @Operation(summary = "通过身份证号查询病案号")
    @PostMapping("/getBAHByID")
    @RequirePermissions({"search:read"})
    public Result<List<Patient>> getBAHByIdCard(@Valid @RequestBody IdCardQueryRequest request) {
        List<Patient> patients = searchService.getBAHByID(request.getIdCard());
        logger.info("Found {} records for id-card lookup", patients.size());
        return Result.success(patients);
    }
}
