package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.service.AuthService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.JwtUtil;
import com.zjcxph.imgapi.utils.PasswordUtil;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AuthServiceImpl implements AuthService {

    private final AuthUserMapper authUserMapper;
    private final AuthRoleMapper authRoleMapper;

    public AuthServiceImpl(AuthUserMapper authUserMapper, AuthRoleMapper authRoleMapper) {
        this.authUserMapper = authUserMapper;
        this.authRoleMapper = authRoleMapper;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResponseDTO login(UserRequest req) {
        String username = Optional.ofNullable(req.getUsername()).map(String::trim).orElse("");
        String password = Optional.ofNullable(req.getPassword()).map(String::trim).orElse("");

        if (!StringUtils.hasText(username) || !StringUtils.hasText(password)) {
            throw new IllegalArgumentException("用户名和密码不能为空");
        }

        AuthUser user = authUserMapper.findByUsername(username);
        if (user == null) {
            throw new IllegalArgumentException("用户名或密码错误");
        }
        if (!"active".equalsIgnoreCase(user.getStatus())) {
            throw new BusinessException("账号已被禁用，请联系管理员");
        }
        if (!PasswordUtil.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("用户名或密码错误");
        }

        LocalDateTime now = LocalDateTime.now();
        authUserMapper.updateLastLoginAt(user.getId(), now);
        user.setLastLoginAt(now);

        AuthSession session = toSession(user);
        return new LoginResponseDTO(JwtUtil.getToken(session), session);
    }

    @Override
    @Cacheable(value = "userByUsername", key = "#username", unless = "#result == null")
    public AuthSession currentUser() {
        return AuthContext.getCurrentUser();
    }

    @Override
    public List<AuthUserProfileDTO> listUsers() {
        return authUserMapper.findAll().stream().map(this::toProfile).toList();
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
    public List<AuthRole> listRoles() {
        return authRoleMapper.findAll();
    }

    private AuthSession toSession(AuthUser user) {
        AuthSession session = new AuthSession();
        session.setId(user.getId());
        session.setUsername(user.getUsername());
        session.setDisplayName(firstText(user.getDisplayName(), user.getUsername()));
        session.setRoleCode(user.getRoleCode());
        session.setRoleName(firstText(user.getRoleName(), user.getRoleCode()));
        session.setPermissions(splitPermissions(user.getPermissionsCsv()));
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
        profile.setPermissions(splitPermissions(user.getPermissionsCsv()));
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
}
