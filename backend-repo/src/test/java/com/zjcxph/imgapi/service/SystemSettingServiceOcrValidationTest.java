package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.SystemSetting;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.SystemSettingMapper;
import com.zjcxph.imgapi.service.impl.SystemSettingServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemSettingServiceOcrValidationTest {

    @Mock
    private SystemSettingMapper mapper;

    @Mock
    private DeveloperModeService developerModeService;

    @Mock
    private DeveloperApiAccessService developerApiAccessService;

    private SystemSettingServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new SystemSettingServiceImpl(mapper, developerModeService, developerApiAccessService);
    }

    @Test
    void shouldRejectEnablingOcrWithoutWhitelistedProfileName() {
        assertThrows(BusinessException.class, () -> service.saveSettings(Map.of(
                "ocrEnabled", "true",
                "ocrProfile", ""
        ), "admin"));

        verify(mapper, never()).upsertAll(anyList());
    }

    @Test
    void shouldRejectClearingProfileWhileOcrIsAlreadyEnabled() {
        when(mapper.findByKey("ocrEnabled"))
                .thenReturn(new SystemSetting("ocrEnabled", "true", null));

        assertThrows(BusinessException.class, () -> service.saveSettings(Map.of(
                "ocrProfile", ""
        ), "admin"));

        verify(mapper, never()).upsertAll(anyList());
    }

    @Test
    void shouldAllowDisablingOcrAndClearingProfileTogether() {
        assertDoesNotThrow(() -> service.saveSettings(Map.of(
                "ocrEnabled", "false",
                "ocrProfile", ""
        ), "admin"));

        verify(mapper).upsertAll(anyList());
    }

    @Test
    void shouldAllowEnablingOcrWithStoredProfile() {
        when(mapper.findByKey("ocrProfile"))
                .thenReturn(new SystemSetting("ocrProfile", "tesseract-local", null));

        assertDoesNotThrow(() -> service.saveSettings(Map.of(
                "ocrEnabled", "true"
        ), "admin"));

        verify(mapper).upsertAll(anyList());
    }

    @Test
    void shouldRejectInvalidOcrBoolean() {
        assertThrows(BusinessException.class, () -> service.saveSettings(Map.of(
                "ocrEnabled", "yes"
        ), "admin"));

        verify(mapper, never()).upsertAll(anyList());
    }

    @Test
    void shouldRejectUnsafeBatchReviewThreshold() {
        assertThrows(BusinessException.class, () -> service.saveSettings(Map.of(
                "classificationBatchReviewThreshold", "0.50"
        ), "admin"));

        verify(mapper, never()).upsertAll(anyList());
    }

    @Test
    void shouldAcceptBoundedOcrConfiguration() {
        Map<String, String> settings = Map.of(
                "ocrEnabled", "true",
                "ocrProfile", "tesseract-local",
                "ocrLanguages", "chi_sim+eng",
                "ocrMaxConcurrency", "1",
                "ocrPageTimeoutSeconds", "30",
                "ocrMaxOutputBytes", "4194304",
                "ocrLowConfidenceThreshold", "0.70",
                "classificationBatchReviewThreshold", "0.92"
        );

        assertDoesNotThrow(() -> service.saveSettings(settings, "admin"));
        verify(mapper).upsertAll(anyList());
    }
}
