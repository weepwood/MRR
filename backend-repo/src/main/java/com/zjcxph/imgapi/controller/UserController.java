package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.annotation.RequirePermissions;
import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.common.Result;
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

    @Operation(summary = "Login")
    @PostMapping("/login")
    public Result<LoginResponseDTO> login(@Valid @RequestBody UserRequest req) {
        LoginResponseDTO response = authService.login(req);
        if (response.getToken() == null || response.getToken().isBlank()) {
            return Result.<LoginResponseDTO>fail("Invalid username or password");
        }
        logger.info("User {} logged in successfully", req.getUsername());
        return Result.<LoginResponseDTO>success("Login success").data(response);
    }

    @Operation(summary = "Current user")
    @GetMapping("/me")
    public Result<AuthSession> currentUser() {
        AuthSession session = authService.currentUser();
        if (session == null) {
            return Result.<AuthSession>fail("Not logged in or token expired");
        }
        return Result.<AuthSession>success("success").data(session);
    }

    @Operation(summary = "List users")
    @RequirePermissions({"user:manage"})
    @GetMapping("/users")
    public Result<List<AuthUserProfileDTO>> listUsers() {
        return Result.<List<AuthUserProfileDTO>>success("success").data(authService.listUsers());
    }

    @Operation(summary = "List roles")
    @RequirePermissions({"role:read"})
    @GetMapping("/roles")
    public Result<List<AuthRole>> listRoles() {
        return Result.<List<AuthRole>>success("success").data(authService.listRoles());
    }

    @Operation(summary = "Update user")
    @RequirePermissions({"user:manage"})
    @PutMapping("/users/{id}")
    public Result<AuthUserProfileDTO> updateUser(@PathVariable Long id, @Valid @RequestBody AuthUserUpdateRequest request) {
        AuthUserProfileDTO updated = authService.updateUser(id, request);
        if (updated == null) {
            return Result.<AuthUserProfileDTO>fail("User not found");
        }
        return Result.<AuthUserProfileDTO>success("Update success").data(updated);
    }

    @Operation(summary = "Disable user")
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
