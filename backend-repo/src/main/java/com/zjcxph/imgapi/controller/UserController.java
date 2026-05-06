package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "Auth API", description = "Authentication and permission management")
@RestController
@RequestMapping("/api/v1/auth")
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    private final AuthService authService;

    public UserController(AuthService authService) {
        this.authService = authService;
    }

    /**
     * 用户登录接口。
     * <p>
     * 验证用户提供的用户名和密码，如果认证成功则返回包含 Token 的登录响应。
     * 认证失败时返回错误提示，成功时记录日志并返回 Token 信息。
     * </p>
     *
     * @param req 用户登录请求对象，包含用户名和密码等认证信息，必须通过参数校验
     * @return Result<LoginResponseDTO> 统一响应结果，包含：
     *         - 成功时：返回 "Login success" 消息和包含 Token 的 LoginResponseDTO 对象
     *         - 失败时：返回 "Invalid username or password" 错误消息
     */
    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Valid @RequestBody UserRequest req) {
        // 调用认证服务执行登录逻辑
        LoginResponseDTO response = authService.login(req);
        
        // 验证 Token 是否有效，无效则返回认证失败
        if (response.getToken() == null || response.getToken().isBlank()) {
            return Result.<LoginResponseDTO>fail("Invalid username or password");
        }
        
        // 记录成功登录日志并返回响应
        logger.info("User {} logged in successfully", req.getUsername());
        return Result.<LoginResponseDTO>success("Login success").data(response);
    }

    /**
     * 用户注册接口。
     * <p>
     * 创建新用户账号，默认分配 DOCTOR 角色。
     * 注册成功后自动登录，返回包含 Token 的登录响应。
     * </p>
     *
     * @param req 用户注册请求对象，包含用户名、密码和可选的显示名称，必须通过参数校验
     * @return Result<LoginResponseDTO> 统一响应结果，包含 Token 和用户信息
     */
    @Operation(summary = "用户注册")
    @PostMapping("/register")
    public Result<LoginResponseDTO> register(@Valid @RequestBody RegisterRequest req) {
        LoginResponseDTO response = authService.register(req);
        if (response.getToken() == null || response.getToken().isBlank()) {
            return Result.<LoginResponseDTO>fail("Registration failed");
        }
        logger.info("User {} registered successfully", req.getUsername());
        return Result.<LoginResponseDTO>success("Registration success").data(response);
    }

    /**
     * 获取当前登录用户信息
     * <p>
     * 通过认证服务获取当前会话中的用户信息，用于前端页面展示当前登录用户的详细资料。
     * 如果用户未登录或Token已过期，则返回失败响应。
     * </p>
     *
     * @return Result<AuthSession> 统一响应结果
     *         - 成功时(code=200)：data字段包含AuthSession对象，其中有用户ID、用户名、显示名称、角色信息、权限列表等
     *         - 失败时(code=400)：message字段包含"Not logged in or token expired"提示信息
     */
    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public Result<AuthSession> currentUser() {
        AuthSession session = authService.currentUser();
        if (session == null) {
            return Result.<AuthSession>fail("Not logged in or token expired");
        }
        return Result.<AuthSession>success("success").data(session);
    }

    /**
     * 获取用户列表
     * <p>
     * 查询系统中所有用户的资料信息，需要"user:manage"权限才能访问。
     * 返回的用户信息包括用户ID、用户名、显示名称、角色信息和状态等。
     * </p>
     *
     * @return Result<List<AuthUserProfileDTO>> 统一响应结果
     *         - 成功时(code=200)：data字段包含AuthUserProfileDTO对象列表，每个对象包含用户的完整资料信息
     */
    @Operation(summary = "获取用户列表")
    @RequirePermissions({"user:manage"})
    @GetMapping("/users")
    public Result<List<AuthUserProfileDTO>> listUsers() {
        return Result.<List<AuthUserProfileDTO>>success("success").data(authService.listUsers());
    }

    /**
     * 获取角色列表
     * <p>
     * 查询系统中所有角色的信息，需要"role:read"权限才能访问。
     * 返回的角色信息包括角色代码、角色名称、描述、权限配置和排序等。
     * </p>
     *
     * @return Result<List<AuthRole>> 统一响应结果
     *         - 成功时(code=200)：data字段包含AuthRole对象列表，每个对象包含角色的完整信息（代码、名称、描述、权限配置等）
     */
    @Operation(summary = "获取角色列表")
    @RequirePermissions({"role:read"})
    @GetMapping("/roles")
    public Result<List<AuthRole>> listRoles() {
        return Result.<List<AuthRole>>success("success").data(authService.listRoles());
    }

    /**
     * 更新用户信息
     * <p>
     * 根据用户ID更新指定用户的资料，需要"user:manage"权限才能访问。
     * 可以更新用户的显示名称、角色代码和状态等信息。
     * </p>
     *
     * @param id 用户ID，从URL路径中获取
     * @param request 用户更新请求对象，包含displayName（显示名称）、roleCode（角色代码）、status（状态）等字段，需通过验证
     * @return Result<AuthUserProfileDTO> 统一响应结果
     *         - 成功时(code=200)：data字段包含更新后的AuthUserProfileDTO对象
     *         - 失败时(code=400)：message字段包含"User not found"提示信息，表示用户不存在
     */
    @Operation(summary = "更新用户信息")
    @RequirePermissions({"user:manage"})
    @PutMapping("/users/{id}")
    public Result<AuthUserProfileDTO> updateUser(@PathVariable Long id, @Valid @RequestBody AuthUserUpdateRequest request) {
        AuthUserProfileDTO updated = authService.updateUser(id, request);
        if (updated == null) {
            return Result.<AuthUserProfileDTO>fail("User not found");
        }
        return Result.<AuthUserProfileDTO>success("Update success").data(updated);
    }

    /**
     * 禁用用户账号
     * <p>
     * 根据用户ID禁用指定的用户账号，需要"user:manage"权限才能访问。
     * 该操作会将用户状态设置为禁用，使用户无法再登录系统。
     * </p>
     *
     * @param id 用户ID，从URL路径中获取
     * @return Result<Void> 统一响应结果
     *         - 成功时(code=200)：表示用户已成功禁用
     *         - 失败时(code=400)：message字段包含"User not found"提示信息，表示用户不存在
     */
    @Operation(summary = "禁用用户账号")
    @RequirePermissions({"user:manage"})
    @DeleteMapping("/users/{id}")
    public Result<Void> disableUser(@PathVariable Long id) {
        int updated = authService.disableUser(id);
        if (updated == 0) {
            return Result.<Void>fail("User not found");
        }
        return Result.<Void>success("Disable success");
    }
}
