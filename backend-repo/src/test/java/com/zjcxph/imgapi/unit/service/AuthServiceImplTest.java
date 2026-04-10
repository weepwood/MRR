package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.UserRequest;
import com.zjcxph.imgapi.dto.resp.LoginResponseDTO;
import com.zjcxph.imgapi.entity.AuthUser;
import com.zjcxph.imgapi.mapper.AuthRoleMapper;
import com.zjcxph.imgapi.mapper.AuthUserMapper;
import com.zjcxph.imgapi.service.impl.AuthServiceImpl;
import com.zjcxph.imgapi.utils.AuthContext;
import com.zjcxph.imgapi.utils.PasswordUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthServiceImpl 单元测试")
class AuthServiceImplTest {

    @Mock
    private AuthUserMapper authUserMapper;

    @Mock
    private AuthRoleMapper authRoleMapper;

    @InjectMocks
    private AuthServiceImpl authService;

    private AuthUser mockUser;

    @BeforeEach
    void setUp() {
        mockUser = new AuthUser();
        mockUser.setId(1L);
        mockUser.setUsername("admin");
        mockUser.setDisplayName("管理员");
        mockUser.setPasswordHash(PasswordUtil.encode("123456"));
        mockUser.setRoleCode("ADMIN");
        mockUser.setRoleName("管理员");
        mockUser.setPermissionsCsv("user:manage,role:manage,role:read");
        mockUser.setStatus("active");
        mockUser.setLastLoginAt(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
        AuthContext.clear();
    }

    @Nested
    @DisplayName("login 方法")
    class LoginTests {

        @Test
        @DisplayName("正常登录 — 返回 Token 和用户信息")
        void login_success() {
            // given
            UserRequest req = new UserRequest();
            req.setUsername("admin");
            req.setPassword("123456");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);
            when(authUserMapper.updateLastLoginAt(eq(1L), any(LocalDateTime.class))).thenReturn(1);

            // when
            LoginResponseDTO result = authService.login(req);

            // then
            assertThat(result).isNotNull();
            assertThat(result.getToken()).isNotBlank();
            assertThat(result.getUser()).isNotNull();
            assertThat(result.getUser().getUsername()).isEqualTo("admin");
            assertThat(result.getUser().getRoleCode()).isEqualTo("ADMIN");
            verify(authUserMapper).updateLastLoginAt(eq(1L), any(LocalDateTime.class));
        }

        @Test
        @DisplayName("用户名为空 — 抛出 IllegalArgumentException")
        void login_emptyUsername_throwsException() {
            UserRequest req = new UserRequest();
            req.setUsername("");
            req.setPassword("123456");

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能为空");
        }

        @Test
        @DisplayName("用户不存在 — 抛出 IllegalArgumentException")
        void login_userNotFound_throwsException() {
            UserRequest req = new UserRequest();
            req.setUsername("ghost");
            req.setPassword("123456");
            when(authUserMapper.findByUsername("ghost")).thenReturn(null);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名或密码错误");
        }

        @Test
        @DisplayName("密码错误 — 抛出 IllegalArgumentException")
        void login_wrongPassword_throwsException() {
            UserRequest req = new UserRequest();
            req.setUsername("admin");
            req.setPassword("wrong_password");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("用户名或密码错误");
        }

        @Test
        @DisplayName("账号被禁用 — 抛出 IllegalStateException")
        void login_disabledUser_throwsException() {
            mockUser.setStatus("disabled");
            UserRequest req = new UserRequest();
            req.setUsername("admin");
            req.setPassword("123456");
            when(authUserMapper.findByUsername("admin")).thenReturn(mockUser);

            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("禁用");
        }
    }

    @Nested
    @DisplayName("listUsers 方法")
    class ListUsersTests {

        @Test
        @DisplayName("返回所有用户的 Profile 列表")
        void listUsers_returnsProfiles() {
            when(authUserMapper.findAll()).thenReturn(List.of(mockUser));

            var profiles = authService.listUsers();

            assertThat(profiles).hasSize(1);
            assertThat(profiles.getFirst().getUsername()).isEqualTo("admin");
            assertThat(profiles.getFirst().getPermissions()).contains("user:manage");
        }
    }

    @Nested
    @DisplayName("disableUser 方法")
    class DisableUserTests {

        @Test
        @DisplayName("禁用用户 — 返回影响行数")
        void disableUser_success() {
            when(authUserMapper.updateStatus(1L, "disabled")).thenReturn(1);

            int result = authService.disableUser(1L);

            assertThat(result).isEqualTo(1);
            verify(authUserMapper).updateStatus(1L, "disabled");
        }
    }
}