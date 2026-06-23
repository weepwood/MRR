package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.dto.req.UserRequest;
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

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private static final Logger logger = LoggerFactory.getLogger(AuthServiceImpl.class);

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
    public LoginResponseDTO login(UserRequest req) {
        String username = Optional.ofNullable(req.getUsername()).map(String::trim).orElse("");
        String password = Optional.ofNullable(req.getPassword()).map(String::trim).orElse("");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        // 频率限制检查
        if (rateLimiter.isLoginBlocked(username)) {
            throw new BusinessException("登录尝试过于频繁，请15分钟后重试");
        }

        AuthUser user = authUserMapper.findByUsername(username);
        if (user == null) {
            rateLimiter.recordLoginFailure(username);
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            rateLimiter.recordLoginFailure(username);
            throw new IllegalArgumentException("用户名或密码错误");
        }

        rateLimiter.resetLoginFailures(username);

        LocalDateTime now = LocalDateTime.now();
        authUserMapper.updateLastLoginAt(user.getId(), now);
        user.setLastLoginAt(now);

        AuthSession session = toSession(user);
        return new LoginResponseDTO(JwtUtil.getToken(session), session);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO register(RegisterRequest req) {
        String username = Optional.ofNullable(req.getUsername()).map(String::trim).orElse("");
        String password = Optional.ofNullable(req.getPassword()).map(String::trim).orElse("");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }
        if (password.length() < 6 || password.length() > 18) {
            throw new IllegalArgumentException("密码长度为6到18位");
        }

        AuthUser existing = authUserMapper.findByUsername(username);
        if (existing != null) {
            throw new BusinessException("用户名已存在");
        }

        // 角色验证：仅允许 NURSE 或 DOCTOR，默认 DOCTOR
        String roleCode = Optional.ofNullable(req.getRoleCode()).map(String::trim)
                .filter(r -> !r.isEmpty()).orElse("DOCTOR").toUpperCase();
        if (!"DOCTOR".equals(roleCode) && !"NURSE".equals(roleCode)) {
            throw new BusinessException("无效的角色代码，仅支持 DOCTOR 或 NURSE");
        }

        AuthUser user = new AuthUser();
        user.setUsername(username);
        user.setDisplayName(Optional.ofNullable(req.getDisplayName()).map(String::trim).orElse(username));
        user.setPasswordHash(PasswordUtil.encode(password));
        user.setRoleCode(roleCode);
        user.setStatus("active");

        authUserMapper.insertUser(user);

        AuthUser created = authUserMapper.findByUsername(username);
        AuthSession session = toSession(created);
        return new LoginResponseDTO(JwtUtil.getToken(session), session);
    }

    @Override
    public AuthSession currentUser() {
        return AuthContext.getCurrentUser();
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
        List<AuthUser> users = authUserMapper.findAllWithPagination(offset, size, normalizedKeyword, normalizedRoleCode, normalizedStatus);
        int total = authUserMapper.countAll(normalizedKeyword, normalizedRoleCode, normalizedStatus);
        return PageResult.of(users.stream().map(this::toProfile).toList(), total, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public AuthUserProfileDTO updateUser(Long id, AuthUserUpdateRequest request) {
        AuthUser user = authUserMapper.findById(id);
        if (user == null) {
            return null;
        }

        String displayName = request.getDisplayName();
        user.setDisplayName(StringUtils.hasText(displayName) ? displayName.trim() : null);
        user.setRoleCode(request.getRoleCode().trim());
        user.setStatus(request.getStatus().trim().toLowerCase(Locale.ROOT));

        int updated = authUserMapper.updateUser(user);
        if (updated == 0) {
            return null;
        }
        return toProfile(authUserMapper.findById(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int disableUser(Long id) {
        AuthUser user = authUserMapper.findById(id);
        if (user == null) {
            return 0;
        }
        return authUserMapper.updateStatus(id, "disabled");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changePassword(Long userId, String oldPassword, String newPassword) {
        if (oldPassword == null || newPassword == null || newPassword.length() < 6 || newPassword.length() > 18) {
            throw new IllegalArgumentException("密码长度为6到18位");
        }

        AuthUser user = authUserMapper.findById(userId);
        if (user == null) {
            throw new BusinessException("用户不存在");
        }
        if (!PasswordUtil.matches(oldPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("原密码错误");
        }

        authUserMapper.updatePassword(userId, PasswordUtil.encode(newPassword));
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
            throw new IllegalArgumentException("Role not found: " + code);
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
        session.setPermissions(new ArrayList<>(PermissionResolver.resolve(splitPermissions(user.getPermissionsCsv()))));
        session.setStatus(user.getStatus());
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
        profile.setPermissions(new ArrayList<>(PermissionResolver.resolve(splitPermissions(user.getPermissionsCsv()))));
        profile.setStatus(user.getStatus());
        profile.setLastLoginAt(user.getLastLoginAt());
        return profile;
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
        return StringUtils.hasText(primary) ? primary : fallback;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }
}
