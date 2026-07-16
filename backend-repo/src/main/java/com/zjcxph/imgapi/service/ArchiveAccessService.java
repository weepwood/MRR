package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.ArchiveAccessAttributes;
import com.zjcxph.imgapi.entity.ArchiveIpBinding;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.ArchiveIpBindingMapper;
import com.zjcxph.imgapi.utils.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Objects;

@Service
public class ArchiveAccessService {

    public static final String MAX_IP_CHANGES_SETTING = "archiveIpMaxChanges";
    public static final int DEFAULT_MAX_IP_CHANGES = 3;
    private static final int MAX_CONFIGURABLE_IP_CHANGES = 20;
    private static final int MAX_USER_ID_LENGTH = 128;
    private static final Logger logger = LoggerFactory.getLogger(ArchiveAccessService.class);

    private final ArchiveIpBindingMapper archiveIpBindingMapper;
    private final SystemSettingService systemSettingService;

    public ArchiveAccessService(
            ArchiveIpBindingMapper archiveIpBindingMapper,
            SystemSettingService systemSettingService
    ) {
        this.archiveIpBindingMapper = archiveIpBindingMapper;
        this.systemSettingService = systemSettingService;
    }

    /**
     * 对 archive URL 中的明文 userid 执行每日 IP 绑定。
     * userid 为空时保持现有内部访问兼容性，不启用绑定。
     *
     * 明确的参数错误和 IP 切换超限会拒绝访问；数据库或设置读取异常只记录错误并临时放行，
     * 避免辅助审计功能故障导致原有病案图片完全不可用。
     */
    @Transactional
    public void verifyAndRecord(
            String rawUserId,
            String normalizedBah,
            String normalizedSjh,
            HttpServletRequest request
    ) {
        String userid = normalizeUserId(rawUserId);
        if (userid == null) {
            return;
        }

        String clientIp = IpUtil.getClientIp(request);
        LocalDate accessDate = LocalDate.now();

        request.setAttribute(ArchiveAccessAttributes.USER_ID, userid);
        request.setAttribute(
                ArchiveAccessAttributes.AUDIT_TARGET,
                buildAuditTarget(normalizedBah, normalizedSjh)
        );

        try {
            verifyIpBinding(userid, clientIp, accessDate, request);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            String note = "IP 绑定记录异常，已临时放行";
            request.setAttribute(ArchiveAccessAttributes.IP_AUDIT_NOTE, note);
            logger.error(
                    "病案访问 IP 绑定失败，已临时放行: userid={}, ip={}, date={}",
                    userid, clientIp, accessDate, exception
            );
        }
    }

    private void verifyIpBinding(
            String userid,
            String clientIp,
            LocalDate accessDate,
            HttpServletRequest request
    ) {
        int maxChanges = resolveMaxIpChanges();
        int inserted = archiveIpBindingMapper.insertIfAbsent(accessDate, userid, clientIp);
        if (inserted > 0) {
            String note = "首次绑定 IP " + clientIp + "（每日允许切换 " + maxChanges + " 次）";
            request.setAttribute(ArchiveAccessAttributes.IP_AUDIT_NOTE, note);
            logger.info("病案访问首次绑定 IP: userid={}, ip={}, date={}", userid, clientIp, accessDate);
            return;
        }

        ArchiveIpBinding binding = archiveIpBindingMapper.findForUpdate(accessDate, userid);
        if (binding == null) {
            throw new IllegalStateException("无法读取病案访问 IP 绑定状态");
        }

        if (Objects.equals(binding.getBoundIp(), clientIp)) {
            archiveIpBindingMapper.touch(binding.getId());
            return;
        }

        int currentChanges = binding.getIpChangeCount() == null ? 0 : binding.getIpChangeCount();
        int nextChanges = currentChanges + 1;
        String previousIp = binding.getBoundIp();

        if (nextChanges > maxChanges) {
            String note = "IP 从 " + previousIp + " 切换到 " + clientIp
                    + " 已拒绝（已允许 " + currentChanges + "/" + maxChanges + " 次）";
            request.setAttribute(ArchiveAccessAttributes.IP_AUDIT_NOTE, note);
            logger.warn(
                    "病案访问 IP 切换超限: userid={}, previousIp={}, currentIp={}, changes={}/{}, date={}",
                    userid, previousIp, clientIp, currentChanges, maxChanges, accessDate
            );
            throw new BusinessException(
                    403,
                    "当前 userid 今日 IP 切换次数已超过限制，最多允许 " + maxChanges + " 次"
            );
        }

        archiveIpBindingMapper.changeIp(binding.getId(), clientIp);
        String note = "IP 从 " + previousIp + " 切换到 " + clientIp
                + "（" + nextChanges + "/" + maxChanges + "）";
        request.setAttribute(ArchiveAccessAttributes.IP_AUDIT_NOTE, note);
        logger.warn(
                "病案访问 IP 已切换: userid={}, previousIp={}, currentIp={}, changes={}/{}, date={}",
                userid, previousIp, clientIp, nextChanges, maxChanges, accessDate
        );
    }

    private String normalizeUserId(String rawUserId) {
        if (rawUserId == null || rawUserId.isBlank()) {
            return null;
        }
        String userid = rawUserId.trim();
        if (userid.length() > MAX_USER_ID_LENGTH || userid.chars().anyMatch(Character::isISOControl)) {
            throw new BusinessException(400, "userid 格式不正确，长度不能超过 128 且不能包含控制字符");
        }
        return userid;
    }

    private int resolveMaxIpChanges() {
        String configured = systemSettingService.getSetting(MAX_IP_CHANGES_SETTING);
        if (configured == null || configured.isBlank()) {
            return DEFAULT_MAX_IP_CHANGES;
        }
        try {
            int value = Integer.parseInt(configured.trim());
            return Math.max(0, Math.min(MAX_CONFIGURABLE_IP_CHANGES, value));
        } catch (NumberFormatException exception) {
            logger.warn("系统设置 {}={} 无效，使用默认值 {}",
                    MAX_IP_CHANGES_SETTING, configured, DEFAULT_MAX_IP_CHANGES);
            return DEFAULT_MAX_IP_CHANGES;
        }
    }

    private String buildAuditTarget(String bah, String sjh) {
        String safeBah = bah == null ? "" : bah;
        String safeSjh = sjh == null ? "" : sjh;
        if (!safeSjh.isBlank()) {
            return safeBah + ":" + safeSjh;
        }
        return safeBah;
    }
}
