package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Permissions;
import com.zjcxph.imgapi.dto.req.AdminCreateUserRequest;
import com.zjcxph.imgapi.dto.req.AdminResetPasswordRequest;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.req.RequiredPasswordChangeRequest;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.dto.resp.UserCredentialResultDTO;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.security.LoginRateLimiter;
import com.zjcxph.imgapi.service.AuthService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.JwtUtil;
import com.zjcxph.imgapi.utils.PasswordUtil;
import com.zjcxph.imgapi.utils.PermissionResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);
    private static final Logger securityAudit = LoggerFactory.getLogger("SECURITY_AUDIT");
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();
    private static final char[] TEMPORARY_PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789!@#$%".toCharArray();
    private static final int TEMPORARY_PASSWORD_LENGTH = 16;
    private static final int MIN_PASSWORD_LENGTH = 12;
    private static final int MAX_PASSWORD_LENGTH = 64;

    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;
    private final LoginRateLimiter rateLimiter;

    public AuthServiceImpl(AuthUserMapper authUserMapper, AuthRoleMapper authRoleMapper,
                           LoginRateLimiter rateLimiter) {
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
        this.rateLimiter = rateLimiter;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO login(UserRequest req, String clientIp) {
        String username = Optional.ofNullable(req.getUsername()).map(String::trim).orElse("");
        String password = Optional.ofNullable(req.getPassword()).orElse("");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        String attemptKey = loginAttemptKey(username, clientIp);
        if (rateLimiter.isLoginBlocked(attemptKey)) {
            throw new BusinessException(429, "登录尝试过于频繁，请15分钟后重试");
        }

        AuthUser user = authUserMapper.findByUsername(username);
        if (user == null || !PasswordUtil.matches(password, user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(attemptKey);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        // 只有凭据校验成功后才返回账号状态，避免通过任意密码枚举禁用账号。
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        if (user.isPasswordChangeRequired()
                && user.getTemporaryPasswordExpiresAt() != null
                && user.getTemporaryPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            securityAudit.warn("event=USER_TEMP_PASSWORD_EXPIRED targetUserId={} username={} sourceIp={} result=DENIED",
                    user.getId(), user.getUsername(), clientIp);
            throw new BusinessException("临时密码已过期，请联系管理员重新生成");
        }

        rateLimiter.resetLoginFailures(attemptKey);
        LocalDateTime now = LocalDateTime.now();
        authUserMapper.updateLastLoginAt(user.getId(), now);
        user.setLastLoginAt(now);

        AuthSession session = toSession(user);
        return new LoginResponseDTO(JwtUtil.getToken(session), session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO register(RegisterRequest req) {
        return register(req, null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO register(RegisterRequest req, String clientIp) {
        String username = Optional.ofNullable(req.getUsername()).map(String::trim).orElse("");
        String password = Optional.ofNullable(req.getPassword()).orElse("");
        validatePassword(password);
        if (!StringUtils.hasText(username)) {
            throw new IllegalArgumentException("用户名不能为空");
        }
        if (authUserMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }

        String roleCode = normalizeRoleCode(req.getRoleCode(), "DOCTOR");
        requireRole(roleCode);

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(firstText(req.getDisplayName(), username));
        user.setPasswordHash(PasswordUtil.encode(password));
        user.setRoleCode(roleCode);
        user.setStatus("active");
        user.setMustChangePassword(false);
        user.setPasswordVersion(1);
        authUserMapper.insertUser(user);

        AuthUser created = authUserMapper.findByUsername(username);
        securityAudit.info("event=USER_CREATED actorUserId={} targetUserId={} username={} sourceIp={} result=SUCCESS legacyRegister=true",
                AuthContext.getCurrentUser() == null ? null : AuthContext.getCurrentUser().getId(),
                created == null ? null : created.getId(), username, clientIp);
        AuthSession session = toSession(created);
        return new LoginResponseDTO(JwtUtil.getToken(session), session);
    }

    @Override
    public AuthSession currentUser() {
        AuthSession session = AuthContext.getCurrentUser();
        if (session != null && session.isAdmin()) {
            session.setPermissions(adminPermissions());
        }
        return session;
    }

    @Override
    public List<AuthUserProfileDTO> listUsers() {
        return authUserMapper.findAll().stream().map(this::toProfile).toList();
    }

    @Override
    public PageResult<AuthUserProfileDTO> listUsersPaginated(int page, int size, String keyword, String roleCode, String status) {
        int offset = (page - 1) * size;
        String normalizedKeyword = trimToNull(keyword);
        String normalizedRoleCode = trimToNull(roleCode);
        String normalizedStatus = Optional.ofNullable(trimToNull(status))
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse(null);
        List<AuthUser> users = authUserMapper.findAllWithPagination(
                offset, size, normalizedKeyword, normalizedRoleCode, normalizedStatus);
        int total = authUserMapper.countAll(normalizedKeyword, normalizedRoleCode, normalizedStatus);
        return PageResult.of(users.stream().map(this::toProfile).toList(), total, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCredentialResultDTO createUser(AdminCreateUserRequest request,
                                               Long administratorId,
                                               String clientIp) {
        requireActiveAdministrator(administratorId);
        String username = Optional.ofNullable(request.getUsername()).map(String::trim).orElse("");
        if (authUserMapper.findByUsername(username) != null) {
            throw new BusinessException("用户名已存在");
        }
        String roleCode = normalizeRoleCode(request.getRoleCode(), null);
        requireRole(roleCode);
        String requestedStatus = normalizeStatus(request.getStatus());
        if (!"active".equals(requestedStatus)) {
            throw new BusinessException("创建用户时初始状态必须为启用；如暂不使用，请创建后再禁用账号");
        }
        int validHours = normalizeValidHours(request.getTemporaryPasswordValidHours());
        String temporaryPassword = generateTemporaryPassword();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(validHours);

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(firstText(request.getDisplayName(), username));
        user.setPasswordHash(PasswordUtil.encode(temporaryPassword));
        user.setRoleCode(roleCode);
        user.setStatus("active");
        user.setMustChangePassword(true);
        user.setPasswordVersion(1);
        user.setTemporaryPasswordExpiresAt(expiresAt);
        user.setCreatedBy(administratorId);
        authUserMapper.insertUser(user);

        AuthUser created = authUserMapper.findByUsername(username);
        securityAudit.info("event=USER_CREATED actorUserId={} targetUserId={} username={} roleCode={} sourceIp={} tempExpiresAt={} result=SUCCESS",
                administratorId, created == null ? null : created.getId(), username, roleCode, clientIp, expiresAt);
        return new UserCredentialResultDTO(toProfile(created), temporaryPassword, expiresAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UserCredentialResultDTO resetPassword(Long userId,
                                                  AdminResetPasswordRequest request,
                                                  Long administratorId,
                                                  String clientIp) {
        if (userId == null || administratorId == null) {
            throw new BusinessException("用户信息无效");
        }
        if (userId.equals(administratorId)) {
            throw new BusinessException("不能通过管理员重置接口修改自己的密码，请使用个人修改密码功能");
        }

        AuthUser administrator = requireActiveAdministrator(administratorId);
        if (!PasswordUtil.matches(request.getAdministratorPassword(), administrator.getPasswordHash())) {
            securityAudit.warn("event=USER_PASSWORD_RESET actorUserId={} targetUserId={} sourceIp={} result=DENIED reason=ADMIN_PASSWORD_MISMATCH",
                    administratorId, userId, clientIp);
            throw new BusinessException("当前管理员密码验证失败");
        }

        AuthUser target = authUserMapper.findById(userId);
        if (target == null) {
            throw new BusinessException("用户不存在");
        }
        if (!"active".equalsIgnoreCase(target.getStatus())) {
            throw new BusinessException("用户已被禁用，请先启用账号再重置密码");
        }

        int validHours = normalizeValidHours(request.getTemporaryPasswordValidHours());
        String temporaryPassword = generateTemporaryPassword();
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(validHours);
        authUserMapper.resetPassword(userId, PasswordUtil.encode(temporaryPassword), expiresAt, administratorId);

        AuthUser updated = authUserMapper.findById(userId);
        securityAudit.warn("event=USER_PASSWORD_RESET actorUserId={} targetUserId={} username={} sourceIp={} passwordVersion={} tempExpiresAt={} result=SUCCESS",
                administratorId, userId, target.getUsername(), clientIp,
                updated == null ? null : updated.effectivePasswordVersion(), expiresAt);
        return new UserCredentialResultDTO(toProfile(updated), temporaryPassword, expiresAt);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeRequiredPassword(Long userId,
                                       RequiredPasswordChangeRequest request,
                                       String clientIp) {
        AuthUser user = authUserMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!user.isPasswordChangeRequired()) {
            throw new BusinessException("当前账号不需要执行首次改密");
        }
        if (user.getTemporaryPasswordExpiresAt() != null
                && user.getTemporaryPasswordExpiresAt().isBefore(LocalDateTime.now())) {
            throw new BusinessException("临时密码已过期，请联系管理员重新生成");
        }
        if (!PasswordUtil.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new BusinessException("当前密码错误");
        }
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new BusinessException("两次输入的新密码不一致");
        }
        validatePassword(request.getNewPassword());
        if (request.getCurrentPassword().equals(request.getNewPassword())) {
            throw new BusinessException("新密码不能与当前密码相同");
        }

        authUserMapper.changePassword(userId, PasswordUtil.encode(request.getNewPassword()));
        securityAudit.info("event=USER_FIRST_PASSWORD_CHANGED actorUserId={} targetUserId={} sourceIp={} result=SUCCESS",
                userId, userId, clientIp);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthUserProfileDTO updateUser(Long id, AuthUserUpdateRequest request) {
        AuthUser user = authUserMapper.findById(id);
        if (user == null) {
            return null;
        }

        String nextRoleCode = normalizeRoleCode(request.getRoleCode(), null);
        requireRole(nextRoleCode);
        String nextStatus = normalizeStatus(request.getStatus());
        if (isLastActiveAdmin(user)
                && (!"ADMIN".equalsIgnoreCase(nextRoleCode) || !"active".equalsIgnoreCase(nextStatus))) {
            throw new BusinessException("不能停用或降级最后一个有效管理员");
        }

        user.setDisplayName(StringUtils.hasText(request.getDisplayName()) ? request.getDisplayName().trim() : null);
        user.setRoleCode(nextRoleCode);
        user.setStatus(nextStatus);
        int updated = authUserMapper.updateUser(user);
        if (updated == 0) {
            return null;
        }
        securityAudit.info("event=USER_ROLE_OR_STATUS_CHANGED actorUserId={} targetUserId={} roleCode={} status={} result=SUCCESS",
                AuthContext.getCurrentUser() == null ? null : AuthContext.getCurrentUser().getId(),
                id, nextRoleCode, nextStatus);
        return toProfile(authUserMapper.findById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int disableUser(Long id) {
        AuthUser user = authUserMapper.findById(id);
        if (user == null) {
            return 0;
        }
        if (isLastActiveAdmin(user)) {
            throw new BusinessException("不能禁用最后一个有效管理员");
        }
        int updated = authUserMapper.updateStatus(id, "disabled");
        if (updated > 0) {
            securityAudit.warn("event=USER_DISABLED actorUserId={} targetUserId={} result=SUCCESS",
                    AuthContext.getCurrentUser() == null ? null : AuthContext.getCurrentUser().getId(), id);
        }
        return updated;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        validatePassword(newPassword);
        AuthUser user = authUserMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }
        if (oldPassword.equals(newPassword)) {
            throw new BusinessException("新密码不能与原密码相同");
        }
        authUserMapper.changePassword(userId, PasswordUtil.encode(newPassword));
        securityAudit.info("event=USER_PASSWORD_CHANGED actorUserId={} targetUserId={} result=SUCCESS", userId, userId);
    }

    @Override
    public List<AuthRole> listRoles() {
        return authRoleMapper.findAll();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthRole updateRole(String code, String name, String description, String permissions, Integer sortOrder) {
        AuthRole role = authRoleMapper.findByCode(code);
        if (role == null) {
            throw new IllegalArgumentException("角色不存在: " + code);
        }
        if (name != null) role.setName(name);
        if (description != null) role.setDescription(description);
        if (permissions != null) role.setPermissions(permissions);
        if (sortOrder != null) role.setSortOrder(sortOrder);
        authRoleMapper.update(role);
        logger.info("Role updated: code={}, name={}, permissions={}", code, role.getName(), role.getPermissions());
        return role;
    }

    private AuthSession toSession(AuthUser user) {
        AuthSession session = new AuthSession();
        session.setId(user.getId());
        session.setUsername(user.getUsername());
        session.setDisplayName(firstText(user.getDisplayName(), user.getUsername()));
        session.setRoleCode(user.getRoleCode());
        session.setRoleName(firstText(user.getRoleName(), user.getRoleCode()));
        session.setPermissions(isAdminRole(user.getRoleCode())
                ? adminPermissions()
                : new ArrayList<>(PermissionResolver.resolve(splitPermissions(user.getPermissionsCsv()))));
        session.setStatus(user.getStatus());
        session.setMustChangePassword(user.isPasswordChangeRequired());
        session.setPasswordVersion(user.effectivePasswordVersion());
        session.setTemporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt());
        session.setLastLoginAt(user.getLastLoginAt());
        return session;
    }

    private AuthUserProfileDTO toProfile(AuthUser user) {
        if (user == null) {
            return null;
        }
        AuthUserProfileDTO profile = new AuthUserProfileDTO();
        profile.setId(user.getId());
        profile.setUsername(user.getUsername());
        profile.setDisplayName(firstText(user.getDisplayName(), user.getUsername()));
        profile.setRoleCode(user.getRoleCode());
        profile.setRoleName(firstText(user.getRoleName(), user.getRoleCode()));
        profile.setPermissions(isAdminRole(user.getRoleCode())
                ? adminPermissions()
                : new ArrayList<>(PermissionResolver.resolve(splitPermissions(user.getPermissionsCsv()))));
        profile.setStatus(user.getStatus());
        profile.setMustChangePassword(user.isPasswordChangeRequired());
        profile.setPasswordVersion(user.effectivePasswordVersion());
        profile.setPasswordChangedAt(user.getPasswordChangedAt());
        profile.setTemporaryPasswordExpiresAt(user.getTemporaryPasswordExpiresAt());
        profile.setPasswordResetAt(user.getPasswordResetAt());
        profile.setPasswordResetBy(user.getPasswordResetBy());
        profile.setLastLoginAt(user.getLastLoginAt());
        profile.setCreatedAt(user.getCreatedAt());
        profile.setUpdatedAt(user.getUpdatedAt());
        return profile;
    }

    private AuthUser requireActiveAdministrator(Long administratorId) {
        if (administratorId == null) {
            throw new BusinessException("管理员信息无效");
        }
        AuthUser administrator = authUserMapper.findById(administratorId);
        if (administrator == null
                || !"ADMIN".equalsIgnoreCase(administrator.getRoleCode())
                || !"active".equalsIgnoreCase(administrator.getStatus())) {
            throw new BusinessException("当前账号不是有效管理员");
        }
        return administrator;
    }

    private boolean isLastActiveAdmin(AuthUser user) {
        return user != null
                && "ADMIN".equalsIgnoreCase(user.getRoleCode())
                && "active".equalsIgnoreCase(user.getStatus())
                && authUserMapper.countActiveAdmins() <= 1;
    }

    private AuthRole requireRole(String roleCode) {
        AuthRole role = authRoleMapper.findByCode(roleCode);
        if (role == null) {
            throw new BusinessException("角色不存在: " + roleCode);
        }
        return role;
    }

    private String generateTemporaryPassword() {
        StringBuilder password = new StringBuilder(TEMPORARY_PASSWORD_LENGTH);
        for (int i = 0; i < TEMPORARY_PASSWORD_LENGTH; i++) {
            password.append(TEMPORARY_PASSWORD_ALPHABET[
                    SECURE_RANDOM.nextInt(TEMPORARY_PASSWORD_ALPHABET.length)]);
        }
        return password.toString();
    }

    private void validatePassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH || password.length() > MAX_PASSWORD_LENGTH) {
            throw new IllegalArgumentException("密码长度应为 12 到 64 位");
        }
    }

    private int normalizeValidHours(Integer hours) {
        int normalized = hours == null ? 24 : hours;
        if (normalized < 1 || normalized > 168) {
            throw new IllegalArgumentException("临时密码有效期应为 1 到 168 小时");
        }
        return normalized;
    }

    private String normalizeStatus(String status) {
        String normalized = Optional.ofNullable(status).map(String::trim).orElse("active").toLowerCase(Locale.ROOT);
        if (!"active".equals(normalized) && !"disabled".equals(normalized)) {
            throw new BusinessException("用户状态只能是 active 或 disabled");
        }
        return normalized;
    }

    private String normalizeRoleCode(String roleCode, String fallback) {
        String normalized = Optional.ofNullable(roleCode).map(String::trim).filter(StringUtils::hasText)
                .orElse(fallback);
        if (!StringUtils.hasText(normalized)) {
            throw new BusinessException("角色不能为空");
        }
        return normalized.toUpperCase(Locale.ROOT);
    }

    private String loginAttemptKey(String username, String clientIp) {
        String normalizedUser = Optional.ofNullable(username).map(String::trim)
                .orElse("").toLowerCase(Locale.ROOT);
        String normalizedIp = Optional.ofNullable(clientIp).map(String::trim)
                .filter(StringUtils::hasText).orElse("unknown");
        return normalizedUser + "|" + normalizedIp;
    }

    private boolean isAdminRole(String roleCode) {
        return "ADMIN".equalsIgnoreCase(roleCode);
    }

    private ArrayList<String> adminPermissions() {
        return new ArrayList<>(PermissionResolver.resolve(Permissions.ALL_PERMISSIONS));
    }

    private List<String> splitPermissions(String rawPermissions) {
        if (!StringUtils.hasText(rawPermissions)) {
            return Collections.emptyList();
        }
        return java.util.Arrays.stream(rawPermissions.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
    }

    private String firstText(String primary, String fallback) {
        return StringUtils.hasText(primary) ? primary.trim() : fallback;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
