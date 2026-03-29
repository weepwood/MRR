package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.service.SearchService;
import com.zjcxph.imgapi.utils.AESUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/v2/search")
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
    public Result<Object> getBAHByEncryptID(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv,
            @RequestParam String timestamp) {
        try {
            logger.info("Decrypt id-card for userId={}, timestamp={}", userId, timestamp);
            String decryptedIdCard = AESUtil.decryptIdCardWithTimestamp(EncryptID, iv, userId, timestamp, secretKey);
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("Found {} records for decrypted id-card", patients.size());
            return new Result<>(200, "success", patients);
        } catch (Exception e) {
            logger.error("Decrypt id-card failed: {}", e.getMessage(), e);
            return new Result<>(500, "decrypt failed: " + e.getMessage(), null);
        }
    }

    @GetMapping("/getBAHByEncryptIDLegacy")
    public Result<Object> getBAHByEncryptIDLegacy(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv) {
        try {
            logger.info("Decrypt legacy id-card for userId={}", userId);
            String decryptedIdCard = AESUtil.decryptIdCard(EncryptID, iv, userId, secretKey);
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("Found {} records for decrypted legacy id-card", patients.size());
            return new Result<>(200, "success", patients);
        } catch (Exception e) {
            logger.error("Decrypt legacy id-card failed: {}", e.getMessage(), e);
            return new Result<>(500, "decrypt failed: " + e.getMessage(), null);
        }
    }

    @GetMapping("/getBAHByID/{idCard}")
    public Result<Object> getBAHByiDCard(@PathVariable String idCard) {
        List<Patient> patients = searchService.getBAHByID(idCard);
        logger.info("Found {} records for idCard={}", patients.size(), idCard);
        return new Result<>(200, "success", patients);
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
