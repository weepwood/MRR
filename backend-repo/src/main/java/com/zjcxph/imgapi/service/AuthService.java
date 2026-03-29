package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.entity.AuthRole;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.dto.resp.AuthUserProfileDTO;
import com.zjcxph.imgapi.dto.req.AuthUserUpdateRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.dto.req.UserRequest;

import java.util.List;

public interface AuthService {
    LoginResponseDTO login(UserRequest req);

    AuthSession currentUser();

    List<AuthUserProfileDTO> listUsers();

    List<AuthRole> listRoles();

    AuthUserProfileDTO updateUser(Long id, AuthUserUpdateRequest request);

    int disableUser(Long id);
}
