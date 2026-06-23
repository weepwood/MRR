package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.dto.req.IdCardQueryRequest;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SearchService;
import com.zjcxph.imgapi.utils.AESUtil;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/search")
@RequirePermissions({"search:read"})
public class SearchController {

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    private final SearchService searchService;

    @Value("${aes.secret.key}")
    private String secretKey;

    public SearchController(SearchService searchService) {
        this.searchService = searchService;
    }

    @GetMapping("/hello")
    public String hello(HttpServletRequest request) {
        return "hello world search, your IP is: " + getClientIP(request);
    }

    @GetMapping("/getBAHByEncryptID")
    public Result<List<Patient>> getBAHByEncryptID(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv,
            @RequestParam String timestamp) {
        try {
            logger.info("Decrypt id-card for userId={}, timestamp={}", userId, timestamp);
            String decryptedIdCard = AESUtil.decryptIdCardWithTimestamp(EncryptID, iv, userId, timestamp, secretKey);
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("Found {} records for decrypted id-card", patients.size());
            return Result.success(patients);
        } catch (Exception e) {
            logger.error("Decrypt id-card failed: {}", e.getMessage(), e);
            return Result.fail("decrypt failed: " + e.getMessage());
        }
    }

    @GetMapping("/getBAHByEncryptIDLegacy")
    public Result<List<Patient>> getBAHByEncryptIDLegacy(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv) {
        try {
            logger.info("Decrypt legacy id-card for userId={}", userId);
            String decryptedIdCard = AESUtil.decryptIdCard(EncryptID, iv, userId, secretKey);
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("Found {} records for decrypted legacy id-card", patients.size());
            return Result.success(patients);
        } catch (Exception e) {
            logger.error("Decrypt legacy id-card failed: {}", e.getMessage(), e);
            return Result.fail("decrypt failed: " + e.getMessage());
        }
    }

    @Operation(summary = "根据病案号查询患者信息")
    @GetMapping("/patient/{bah}")
    public Result<List<Patient>> getPatientByBah(@PathVariable String bah) {
        logger.info("查询病案号 {} 的患者信息", bah);
        if (bah == null || bah.isEmpty()) {
            return Result.fail("病案号不能为空");
        }
        List<Patient> patients = searchService.getPatientByBah(bah);
        return Result.success(patients);
    }

    @Deprecated
    @GetMapping("/getBAHByID/{idCard}")
    public Result<List<Patient>> getBAHByiDCard(@PathVariable String idCard) {
        List<Patient> patients = searchService.getBAHByID(idCard);
        String masked = idCard.length() > 4 ? idCard.substring(0, 4) + "***" : "***";
        logger.info("Found {} records for idCard={}", patients.size(), masked);
        return Result.success(patients);
    }

    @Operation(summary = "通过身份证号查询病案号")
    @PostMapping("/getBAHByID")
    public Result<List<Patient>> getBAHByIdCard(@Valid @RequestBody IdCardQueryRequest request) {
        List<Patient> patients = searchService.getBAHByID(request.getIdCard());
        logger.info("Found {} records for idCard={}***", patients.size(),
                request.getIdCard().substring(0, 4));
        return Result.success(patients);
    }

    private String getClientIP(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
