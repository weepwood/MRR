package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.pojo.AuthRole;
import com.zjcxph.imgapi.pojo.AuthSession;
import com.zjcxph.imgapi.pojo.AuthUserProfileDTO;
import com.zjcxph.imgapi.pojo.AuthUserUpdateRequest;
import com.zjcxph.imgapi.pojo.LoginResponseDTO;
import com.zjcxph.imgapi.pojo.UserRequest;

import java.util.List;

public interface AuthService {
    LoginResponseDTO login(UserRequest req);

    AuthSession currentUser();

    List<AuthUserProfileDTO> listUsers();

    List<AuthRole> listRoles();

    AuthUserProfileDTO updateUser(Long id, AuthUserUpdateRequest request);

    int disableUser(Long id);
}
