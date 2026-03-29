package com.zjcxph.imgapi.service.impl;

import com.zjcxph.imgapi.mapper.UserMapper;
import com.zjcxph.imgapi.entity.User;
import com.zjcxph.imgapi.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;

    @Autowired
    public UserServiceImpl(UserMapper userMapper) {
        this.userMapper = userMapper;
    }


    @Override
    public User findById(Long id) {
        return userMapper.findById(id);
    }
}
