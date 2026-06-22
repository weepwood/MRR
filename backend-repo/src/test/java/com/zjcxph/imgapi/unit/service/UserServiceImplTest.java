package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.User;
import com.zjcxph.imgapi.mapper.UserMapper;
import com.zjcxph.imgapi.service.impl.UserServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserServiceImpl 用户服务测试")
class UserServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    @DisplayName("findById — 命中返回 User")
    void findById_found() {
        User user = new User();
        user.setId(1L);
        when(userMapper.findById(1L)).thenReturn(user);

        assertThat(userService.findById(1L)).isSameAs(user);
    }

    @Test
    @DisplayName("findById — 未命中返回 null")
    void findById_miss() {
        when(userMapper.findById(999L)).thenReturn(null);
        assertThat(userService.findById(999L)).isNull();
    }
}
