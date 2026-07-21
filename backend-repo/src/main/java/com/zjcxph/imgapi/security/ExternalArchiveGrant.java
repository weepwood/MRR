package com.zjcxph.imgapi.security;

import com.zjcxph.imgapi.dto.resp.ExternalArchiveCaseDTO;
import com.zjcxph.imgapi.utils.MedicalRecordCodeUtils;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public record ExternalArchiveGrant(
        String clientId,
        String externalUserId,
        boolean allowDownload,
        long expiresAt,
        List<ExternalArchiveCaseDTO> cases
) {
    public boolean allows(String bah, String sjh) {
        String key = keyOf(bah, sjh);
        return allowedKeys().contains(key);
    }

    public Set<String> allowedKeys() {
        return cases.stream()
                .map(item -> keyOf(item.getBah(), item.getSjh()))
                .collect(Collectors.toUnmodifiableSet());
    }

    public static String keyOf(String bah, String sjh) {
        return MedicalRecordCodeUtils.normalizeOrEmpty(bah) + ":" + MedicalRecordCodeUtils.normalizeOrEmpty(sjh);
    }
}
