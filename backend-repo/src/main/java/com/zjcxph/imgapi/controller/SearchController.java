package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.pojo.Patient;
import com.zjcxph.imgapi.pojo.Result;
import com.zjcxph.imgapi.service.SearchService;
import com.zjcxph.imgapi.utils.AESUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/v2/search")
public class SearchController {
    @GetMapping("/hello")
    public String hello(HttpServletRequest request) {
        String clientIP = getClientIP(request);
        return "hello world search, your IP is: " + clientIP;
    }

    private static final Logger logger = LoggerFactory.getLogger(SearchController.class);

    @Autowired
    private SearchService searchService;
    
    @Value("${aes.secret.key}")
    private String secretKey;

    // 根据加密的身份证号码获取该病人全部的病案号 需要解密（新版本，包含时间戳）
    // 解密方法 使用 ASE CBC 模式 HEX 编码, 使用密钥为 your-32-byte-secret-key-12345678
    @GetMapping("/getBAHByEncryptID")
    public Result<Object> getBAHByEncryptID(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv,
            @RequestParam String timestamp) {
        try {
            logger.info("开始解密身份证号码，用户ID: {}, IV: {}, 时间戳: {}", userId, iv, timestamp);
            
            // 解密身份证号码（新版本，包含时间戳）
            String decryptedIdCard = AESUtil.decryptIdCardWithTimestamp(EncryptID, iv, userId, timestamp, secretKey);
            logger.info("解密成功，身份证号码: {}", decryptedIdCard);
            
            // 根据解密后的身份证号码查询病案号
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("获取 {} 身份证号下的病案号，共 {} 条记录", decryptedIdCard, patients.size());
            
            // 打印 patients
            for (Patient patient : patients) {
                logger.info("{}", patient);
            }
            
            return new Result<>(
                    200,
                    "success",
                    patients
            );
            
        } catch (Exception e) {
            logger.error("解密身份证号码失败: {}", e.getMessage(), e);
            return new Result<>(
                    500,
                    "解密失败: " + e.getMessage(),
                    null
            );
        }
    }
    
    // 根据加密的身份证号码获取该病人全部的病案号 需要解密（旧版本，不包含时间戳，向后兼容）
    // 解密方法 使用 ASE CBC 模式 HEX 编码, 使用密钥为 your-32-byte-secret-key-12345678
    @GetMapping("/getBAHByEncryptIDLegacy")
    public Result<Object> getBAHByEncryptIDLegacy(
            @RequestParam String EncryptID,
            @RequestParam String userId,
            @RequestParam String iv) {
        try {
            logger.info("开始解密身份证号码（旧版本），用户ID: {}, IV: {}", userId, iv);
            
            // 解密身份证号码（旧版本，不包含时间戳）
            String decryptedIdCard = AESUtil.decryptIdCard(EncryptID, iv, userId, secretKey);
            logger.info("解密成功，身份证号码: {}", decryptedIdCard);
            
            // 根据解密后的身份证号码查询病案号
            List<Patient> patients = searchService.getBAHByID(decryptedIdCard);
            logger.info("获取 {} 身份证号下的病案号，共 {} 条记录", decryptedIdCard, patients.size());
            
            // 打印 patients
            for (Patient patient : patients) {
                logger.info("{}", patient);
            }
            
            return new Result<>(
                    200,
                    "success",
                    patients
            );
            
        } catch (Exception e) {
            logger.error("解密身份证号码失败: {}", e.getMessage(), e);
            return new Result<>(
                    500,
                    "解密失败: " + e.getMessage(),
                    null
            );
        }
    }

    // 根据身份证号码获取该病人全部的病案号
    @GetMapping("/getBAHByID/{idCard}")
    public Result<Object> getBAHByiDCard(@PathVariable String idCard) {
        List<Patient> patients = searchService.getBAHByID(idCard);
        logger.info("获取 {} 身份证号下的病案号", idCard);
        // 打印patients
        for (Patient patient : patients) {
            logger.info("{}", patient);
        }
        return new Result<>(
                200,
                "success",
                patients
        );
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
