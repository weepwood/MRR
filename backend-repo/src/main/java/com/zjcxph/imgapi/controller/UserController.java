package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.AdminCreateUserRequest;
import com.zjcxph.imgapi.dto.req.AdminResetPasswordRequest;
import com.zjcxph.imgapi.dto.req.AuthRoleUpdateRequest;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.req.RequiredPasswordChangeRequest;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.dto.resp.UserCredentialResultDTO;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.security.TokenBlacklist;
import com.zjcxph.imgapi.service.AuthService;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.IpUtil;
import com.zjcxph.imgapi.utils.JwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "Auth API", description = "Authentication and permission management")
@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final AuthService authService;
    private final TokenBlacklist tokenBlacklist;

    public UserController(AuthService authService, TokenBlacklist tokenBlacklist) {
        this.authService = authService;
        this.tokenBlacklist = tokenBlacklist;
    }

    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Valid @RequestBody UserRequest req,
                                          HttpServletRequest httpRequest) {
        LoginResponseDTO response = authService.login(req, IpUtil.getClientIp(httpRequest));
        if (response.getToken() == null || response.getToken().isBlank()) {
            return Result.<LoginResponseDTO>fail("用户名或密码错误");
        }
        logger.info("User {} logged in successfully, nextAction={}", req.getUsername(), response.getNextAction());
        return Result.success("登录成功", response);
    }

    /**
     * 旧版注册接口会直接签发新用户 JWT，绕过一次性临时密码与首次改密流程。
     * 保留路由只用于向旧客户端返回明确迁移提示，不再创建账号或签发 Token。
     */
    @Deprecated
    @Operation(summary = "旧版注册接口（已停用）")
    @RequirePermissions({"user:manage"})
    @PostMapping("/register")
    public Result<LoginResponseDTO> register(@RequestBody(required = false) RegisterRequest req,
                                              HttpServletRequest httpRequest) {
        logger.warn("Blocked legacy register endpoint: actor={}, sourceIp={}",
                AuthContext.getCurrentUser() == null ? null : AuthContext.getCurrentUser().getUsername(),
                IpUtil.getClientIp(httpRequest));
        throw new BusinessException(410, "旧版注册接口已停用，请使用用户管理中的创建用户功能");
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public Result<AuthSession> currentUser() {
        AuthSession session = authService.currentUser();
        if (session == null) {
            return Result.<AuthSession>fail("未登录或 Token 已过期");
        }
        return Result.<AuthSession>success("success").data(session);
    }

    @Operation(summary = "管理员创建用户")
    @RequirePermissions({"user:manage"})
    @PostMapping("/users")
    public Result<UserCredentialResultDTO> createUser(@Valid @RequestBody AdminCreateUserRequest request,
                                                       HttpServletRequest httpRequest) {
        AuthSession administrator = requireCurrentUser();
        UserCredentialResultDTO created = authService.createUser(
                request, administrator.getId(), IpUtil.getClientIp(httpRequest));
        return Result.success("用户创建成功，临时密码只显示一次", created);
    }

    @Operation(summary = "获取用户列表（支持分页）")
    @RequirePermissions({"user:manage"})
    @GetMapping("/users")
    public Result<PageResult<AuthUserProfileDTO>> listUsers(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String roleCode,
            @RequestParam(required = false) String status) {
        return Result.<PageResult<AuthUserProfileDTO>>success("success")
                .data(authService.listUsersPaginated(page, size, keyword, roleCode, status));
    }

    @Operation(summary = "管理员重置用户密码")
    @RequirePermissions({"user:manage"})
    @PostMapping("/users/{id}/password/reset")
    public Result<UserCredentialResultDTO> resetPassword(@PathVariable Long id,
                                                          @Valid @RequestBody AdminResetPasswordRequest request,
                                                          HttpServletRequest httpRequest) {
        AuthSession administrator = requireCurrentUser();
        UserCredentialResultDTO result = authService.resetPassword(
                id, request, administrator.getId(), IpUtil.getClientIp(httpRequest));
        return Result.success("密码已重置，临时密码只显示一次", result);
    }

    @Operation(summary = "首次登录或重置后强制修改密码")
    @PostMapping("/password/required-change")
    public Result<Void> changeRequiredPassword(@Valid @RequestBody RequiredPasswordChangeRequest request,
                                                HttpServletRequest httpRequest) {
        AuthSession session = requireCurrentUser();
        authService.changeRequiredPassword(session.getId(), request, IpUtil.getClientIp(httpRequest));
        revokeCurrentToken(httpRequest);
        AuthContext.clear();
        return Result.success("密码修改成功，请使用新密码重新登录");
    }

    @Operation(summary = "获取角色列表")
    @RequirePermissions({"role:read"})
    @GetMapping("/roles")
    public Result<List<AuthRole>> listRoles() {
        return Result.<List<AuthRole>>success("success").data(authService.listRoles());
    }

    @Operation(summary = "更新角色信息")
    @RequirePermissions({"role:manage"})
    @PutMapping("/roles/{code}")
    public Result<AuthRole> updateRole(@PathVariable String code,
                                       @Valid @RequestBody AuthRoleUpdateRequest request) {
        AuthRole role = authService.updateRole(
                code, request.getName(), request.getDescription(), request.getPermissions(), request.getSortOrder());
        return Result.<AuthRole>success("角色更新成功").data(role);
    }

    @Operation(summary = "更新用户信息")
    @RequirePermissions({"user:manage"})
    @PutMapping("/users/{id}")
    public Result<AuthUserProfileDTO> updateUser(@PathVariable Long id,
                                                  @Valid @RequestBody AuthUserUpdateRequest request) {
        AuthSession session = requireCurrentUser();
        if (session.getId().equals(id) && "disabled".equalsIgnoreCase(request.getStatus())) {
            return Result.<AuthUserProfileDTO>fail("不能禁用当前登录账号");
        }
        AuthUserProfileDTO updated = authService.updateUser(id, request);
        if (updated == null) {
            return Result.<AuthUserProfileDTO>fail("用户不存在");
        }
        return Result.<AuthUserProfileDTO>success("更新成功").data(updated);
    }

    @Operation(summary = "禁用用户账号")
    @RequirePermissions({"user:manage"})
    @DeleteMapping("/users/{id}")
    public Result<Void> disableUser(@PathVariable Long id) {
        AuthSession session = requireCurrentUser();
        if (session.getId().equals(id)) {
            return Result.<Void>fail("不能禁用当前登录账号");
        }
        int updated = authService.disableUser(id);
        if (updated == 0) {
            return Result.<Void>fail("用户不存在");
        }
        return Result.<Void>success("禁用成功");
    }

    @Operation(summary = "用户登出")
    @PostMapping("/logout")
    public Result<String> logout(HttpServletRequest request) {
        revokeCurrentToken(request);
        AuthContext.clear();
        return Result.success("已登出");
    }

    @Operation(summary = "修改当前用户密码")
    @PostMapping("/password/edit")
    public Result<Void> changePassword(@RequestBody Map<String, String> body,
                                       HttpServletRequest httpRequest) {
        AuthSession session = requireCurrentUser();
        String oldPassword = body.get("password");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null) {
            return Result.fail("password 和 newPassword 不能为空");
        }
        authService.changePassword(session.getId(), oldPassword, newPassword);
        revokeCurrentToken(httpRequest);
        AuthContext.clear();
        return Result.success("密码修改成功，请重新登录");
    }

    private AuthSession requireCurrentUser() {
        AuthSession session = AuthContext.getCurrentUser();
        if (session == null || session.getId() == null) {
            throw new IllegalStateException("未登录");
        }
        return session;
    }

    private void revokeCurrentToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return;
        }
        String token = authorization.substring(7).trim();
        try {
            tokenBlacklist.revoke(JwtUtil.getJti(token), JwtUtil.getExpirationMillis(token));
        } catch (Exception e) {
            logger.warn("Unable to revoke current token: {}", e.getMessage());
        }
    }
}
