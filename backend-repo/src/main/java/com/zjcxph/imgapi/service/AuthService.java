package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
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

import java.util.List;

public interface AuthService {
    default LoginResponseDTO login(UserRequest req) {
        return login(req, null);
    }

    LoginResponseDTO login(UserRequest req, String clientIp);

    LoginResponseDTO register(RegisterRequest req);

    LoginResponseDTO register(RegisterRequest req, String clientIp);

    AuthSession currentUser();

    List<AuthUserProfileDTO> listUsers();

    PageResult<AuthUserProfileDTO> listUsersPaginated(int page, int size, String keyword, String roleCode, String status);

    default PageResult<AuthUserProfileDTO> listUsersPaginated(int page, int size) {
        return listUsersPaginated(page, size, null, null, null);
    }

    UserCredentialResultDTO createUser(AdminCreateUserRequest request, Long administratorId, String clientIp);

    UserCredentialResultDTO resetPassword(Long userId,
                                          AdminResetPasswordRequest request,
                                          Long administratorId,
                                          String clientIp);

    void changeRequiredPassword(Long userId, RequiredPasswordChangeRequest request, String clientIp);

    void changePassword(Long userId, String oldPassword, String newPassword);

    List<AuthRole> listRoles();

    AuthRole updateRole(String code, String name, String description, String permissions, Integer sortOrder);

    AuthUserProfileDTO updateUser(Long id, AuthUserUpdateRequest request);

    int disableUser(Long id);
}
