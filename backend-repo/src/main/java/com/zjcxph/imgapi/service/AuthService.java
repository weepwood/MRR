package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.req.RegisterRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.resp.PageResult;
import com.zjcxph.imgapi.dto.req.UserRequest;

import java.util.List;

public interface AuthService {
    LoginResponseDTO login(UserRequest req);

    LoginResponseDTO register(RegisterRequest req);

    AuthSession currentUser();

    List<AuthUserProfileDTO> listUsers();

    /** 分页查询用户列表 */
    PageResult<AuthUserProfileDTO> listUsersPaginated(int page, int size, String keyword, String roleCode, String status);

    default PageResult<AuthUserProfileDTO> listUsersPaginated(int page, int size) {
        return listUsersPaginated(page, size, null, null, null);
    }

    /** 修改当前用户密码 */
    void changePassword(Long userId, String oldPassword, String newPassword);

    List<AuthRole> listRoles();

    AuthRole updateRole(String code, String name, String description, String permissions, Integer sortOrder);

    AuthUserProfileDTO updateUser(Long id, AuthUserUpdateRequest request);

    int disableUser(Long id);
}
