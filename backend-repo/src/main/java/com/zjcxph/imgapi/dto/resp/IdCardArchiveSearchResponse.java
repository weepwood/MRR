package com.zjcxph.imgapi.dto.resp;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

/**
 * 身份证检索影像档案袋响应。
 *
 * <p>响应不返回身份证原文，只返回可安全放入 URL 的服务端令牌和脱敏展示值。</p>
 */
@Data
public class IdCardArchiveSearchResponse {
    private String token;
    private String maskedIdCard;
    private List<ArchiveCase> cases;

    public IdCardArchiveSearchResponse(String token, String maskedIdCard, List<ArchiveCase> cases) {
        this.token = token;
        this.maskedIdCard = maskedIdCard;
        this.cases = cases;
    }

    @Data
    public static class ArchiveCase {
        private Integer patientRecordId;
        private String bah;
        private String sjh;
        private String name;
        private LocalDate ruyuan;
        private String admissionTime;
        private String department;
        private String binqu;
        private String chuangwei;

        public ArchiveCase(Integer patientRecordId, String bah, String sjh, String name,
                           LocalDate ruyuan, String admissionTime, String department,
                           String binqu, String chuangwei) {
            this.patientRecordId = patientRecordId;
            this.bah = bah;
            this.sjh = sjh;
            this.name = name;
            this.ruyuan = ruyuan;
            this.admissionTime = admissionTime;
            this.department = department;
            this.binqu = binqu;
            this.chuangwei = chuangwei;
        }
    }
}
