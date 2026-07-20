package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.common.ArchiveAccessAttributes;
import com.zjcxph.imgapi.entity.ArchiveIpBinding;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ArchiveIpBindingMapper;
import com.zjcxph.imgapi.service.ArchiveAccessService;
import com.zjcxph.imgapi.service.SystemSettingService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArchiveAccessServiceTest {

    @Mock
    private ArchiveIpBindingMapper archiveIpBindingMapper;

    @Mock
    private SystemSettingService systemSettingService;

    @Mock
    private HttpServletRequest request;

    private ArchiveAccessService archiveAccessService;

    @BeforeEach
    void setUp() {
        archiveAccessService = new ArchiveAccessService(archiveIpBindingMapper, systemSettingService);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getHeader("X-Real-IP")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.20.30.11");
        when(systemSettingService.getSetting(ArchiveAccessService.MAX_IP_CHANGES_SETTING)).thenReturn("3");
    }

    @Test
    void recordsAuditTargetForInternalUserWithoutExternalUserId() {
        archiveAccessService.verifyAndRecord(null, "00000011", "00000021", request);

        verify(request).setAttribute(ArchiveAccessAttributes.AUDIT_TARGET, "00000011:00000021");
        verify(request, never()).setAttribute(eq(ArchiveAccessAttributes.USER_ID), any());
        verify(archiveIpBindingMapper, never()).insertIfAbsent(any(), any(), any());
    }

    @Test
    void bindsFirstIpWithoutCountingAsAChange() {
        when(archiveIpBindingMapper.insertIfAbsent(any(LocalDate.class), eq("u1001"), eq("10.20.30.11")))
                .thenReturn(1);

        archiveAccessService.verifyAndRecord("u1001", "00000011", "00000021", request);

        verify(request).setAttribute(ArchiveAccessAttributes.USER_ID, "u1001");
        verify(request).setAttribute(ArchiveAccessAttributes.AUDIT_TARGET, "00000011:00000021");
        verify(archiveIpBindingMapper, never()).changeIp(any(), any());
    }

    @Test
    void allowsSameIpWithoutIncreasingCounter() {
        ArchiveIpBinding binding = binding("10.20.30.11", 2);
        when(archiveIpBindingMapper.insertIfAbsent(any(LocalDate.class), eq("u1001"), eq("10.20.30.11")))
                .thenReturn(0);
        when(archiveIpBindingMapper.findForUpdate(any(LocalDate.class), eq("u1001"))).thenReturn(binding);

        archiveAccessService.verifyAndRecord("u1001", "00000011", "", request);

        verify(archiveIpBindingMapper).touch(1L);
        verify(archiveIpBindingMapper, never()).changeIp(any(), any());
    }

    @Test
    void allowsIpChangeWithinConfiguredLimit() {
        ArchiveIpBinding binding = binding("10.20.30.10", 2);
        when(archiveIpBindingMapper.insertIfAbsent(any(LocalDate.class), eq("u1001"), eq("10.20.30.11")))
                .thenReturn(0);
        when(archiveIpBindingMapper.findForUpdate(any(LocalDate.class), eq("u1001"))).thenReturn(binding);

        archiveAccessService.verifyAndRecord("u1001", "00000011", "", request);

        verify(archiveIpBindingMapper).changeIp(1L, "10.20.30.11");
        verify(request).setAttribute(
                eq(ArchiveAccessAttributes.IP_AUDIT_NOTE),
                eq("IP 从 10.20.30.10 切换到 10.20.30.11（3/3）")
        );
    }

    @Test
    void rejectsIpChangeAfterConfiguredLimit() {
        ArchiveIpBinding binding = binding("10.20.30.10", 3);
        when(archiveIpBindingMapper.insertIfAbsent(any(LocalDate.class), eq("u1001"), eq("10.20.30.11")))
                .thenReturn(0);
        when(archiveIpBindingMapper.findForUpdate(any(LocalDate.class), eq("u1001"))).thenReturn(binding);

        assertThatThrownBy(() -> archiveAccessService.verifyAndRecord(
                "u1001", "00000011", "", request
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("最多允许 3 次");

        verify(archiveIpBindingMapper, never()).changeIp(any(), any());
    }

    @Test
    void allowsArchiveAccessWhenBindingStorageIsUnavailable() {
        when(archiveIpBindingMapper.insertIfAbsent(any(LocalDate.class), eq("DOMAIN\\u1001"), eq("10.20.30.11")))
                .thenThrow(new IllegalStateException("table missing"));

        assertThatCode(() -> archiveAccessService.verifyAndRecord(
                "DOMAIN\\u1001", "00000011", "", request
        )).doesNotThrowAnyException();

        verify(request).setAttribute(ArchiveAccessAttributes.USER_ID, "DOMAIN\\u1001");
        verify(request).setAttribute(
                ArchiveAccessAttributes.IP_AUDIT_NOTE,
                "IP 绑定记录异常，已临时放行"
        );
    }

    private ArchiveIpBinding binding(String ip, int changes) {
        ArchiveIpBinding binding = new ArchiveIpBinding();
        binding.setId(1L);
        binding.setBoundIp(ip);
        binding.setIpChangeCount(changes);
        return binding;
    }
}
