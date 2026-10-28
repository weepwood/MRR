package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.IdCardQueryRequest;
import com.zjcxph.imgapi.dto.resp.IdCardArchiveSearchResponse;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.service.SearchService;
import com.zjcxph.imgapi.utils.AESUtil;
import com.zjcxph.imgapi.utils.IdCardQueryTimestampValidator;
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

import java.time.Instant;
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

    /**
     * 加密身份证查询允许的时间戳偏差（秒）。小于等于 0 表示关闭校验。
     *
     * <p>字段初始化值保证非 Spring 环境下（例如单元测试）仍采用安全默认值。
     * 如需临时放宽以兼容调用方时钟偏差，可调整
     * {@code aes.id-card-query.timestamp-tolerance-seconds}。</p>
     */
    @Value("${aes.id-card-query.timestamp-tolerance-seconds:300}")
    private int timestampToleranceSeconds = IdCardQueryTimestampValidator.DEFAULT_TOLERANCE_SECONDS;

    /**
     * 是否允许旧版加密身份证查询（无时间戳）。
     *
     * <p>旧版接口的密文是「用户ID + 身份证号」的静态产物：同一组
     * {@code EncryptID}/{@code iv}/{@code userId} 可以无限次重放，没有任何新鲜度约束，
     * 一旦从日志、浏览器历史或截图中泄露即长期有效。新版接口把时间戳并入密钥派生，
     * 超出窗口即失效。</p>
     *
     * <p>默认关闭：本仓库前端已全部改用新版接口，仓库内没有旧版的调用方。若现场
     * 确有未升级的外部系统，可在 {@code application.properties} 打开
     * {@code aes.id-card-query.legacy-encrypt-id-enabled=true} 临时兼容，并尽快完成改造。</p>
     */
    @Value("${aes.id-card-query.legacy-encrypt-id-enabled:false}")
    private boolean legacyEncryptIdEnabled = false;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @Operation(summary = "健康检查")
    @GetMapping("/hello")
    public String hello(HttpServletRequest request) {
        return "hello world search, your IP is: " + IpUtil.getClientIp(request);
    }

    @Operation(summary = "通过加密身份证号查询病案号（带时间戳）")
    @GetMapping("/getBAHByEncryptID")
    public Result<List<Patient>> getBAHByEncryptID(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv,
            @RequestParam String timestamp) {
        try {
            IdCardQueryTimestampValidator.validate(
                    timestamp, timestampToleranceSeconds, Instant.now().getEpochSecond());
            logger.info("Decrypt id-card for external userId={}", userId);
            String decryptedIdCard = AESUtil.decryptIdCardWithTimestamp(EncryptID, iv, userId, timestamp, secretKey);
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("Found {} records for decrypted id-card", patients.size());
            return Result.success(patients);
        } catch (IllegalArgumentException e) {
            logger.warn("Encrypted id-card query rejected: {}", e.getMessage());
            return Result.fail("加密身份证查询参数无效");
        } catch (Exception e) {
            logger.error("Encrypted id-card query failed: {}", e.getMessage());
            return Result.fail("加密身份证查询失败");
        }
    }

    @Operation(
            summary = "通过加密身份证号查询病案号（旧版，默认停用）",
            description = "密文不含时间戳，可被无限重放，默认返回 410。"
                    + "如确有未升级的外部系统，需显式开启 aes.id-card-query.legacy-encrypt-id-enabled。")
    @GetMapping("/getBAHByEncryptIDLegacy")
    public Result<List<Patient>> getBAHByEncryptIDLegacy(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv) {
        if (!legacyEncryptIdEnabled) {
            logger.warn("Legacy encrypted id-card endpoint rejected (disabled): external userId={}", userId);
            return Result.fail(
                    410,
                    "旧版加密身份证查询已停用，请改用 /api/v1/search/getBAHByEncryptID（带 timestamp）");
        }
        logger.warn("Legacy encrypted id-card endpoint in use, please migrate caller: external userId={}", userId);
        try {
            logger.info("Decrypt legacy id-card for external userId={}", userId);
            String decryptedIdCard = AESUtil.decryptIdCard(EncryptID, iv, userId, secretKey);
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("Found {} records for decrypted legacy id-card", patients.size());
            return Result.success(patients);
        } catch (IllegalArgumentException e) {
            logger.warn("Legacy encrypted id-card query rejected: {}", e.getMessage());
            return Result.fail("加密身份证查询参数无效");
        } catch (Exception e) {
            logger.error("Legacy encrypted id-card query failed: {}", e.getMessage());
            return Result.fail("加密身份证查询失败");
        }
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
    @Operation(summary = "通过身份证号查询病案号（已弃用，使用POST版本）")
    @GetMapping("/getBAHByID/{idCard}")
    public Result<List<Patient>> getBAHByiDCard(@PathVariable String idCard) {
        List<Patient> patients = searchService.getBAHByID(idCard);
        logger.info("Found {} records for id-card path lookup", patients.size());
        return Result.success(patients);
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
