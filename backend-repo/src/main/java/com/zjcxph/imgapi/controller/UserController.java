package com.zjcxph.imgapi.controller;

import com.zjcxph.imgapi.pojo.LoginResponseDTO;
import com.zjcxph.imgapi.pojo.Result;
import com.zjcxph.imgapi.utils.jwtUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import com.zjcxph.imgapi.pojo.UserRequest;


@Tag(name = "User Controller", description = "用户管理接口")
@RestController
public class UserController {
    private static final Logger logger = LoggerFactory.getLogger(UserController.class);

    @Value("${image.username}")
    private String br_admin;

    @Value("${image.password}")
    private String br_password;


    @Operation(summary = "用户登录")
    @PostMapping("/login")
    public Result<?> login(
            @RequestBody UserRequest req) {
        String username = req.getUsername();
        String password = req.getPassword();
        if (username == null || password == null) {
            return Result.fail("用户名或密码不能为空");
        }

        System.out.println(username);
        System.out.println(password);
        Result<LoginResponseDTO> loginResponseDTOResult = new Result<>();
        if (username.equals(br_admin) && password.equals(br_password)) {
            // 过期时间 24h
            String token = jwtUtil.getToken(username);
            logger.info("用户 {} 登录", username);
            LoginResponseDTO loginResponseDTO = new LoginResponseDTO(token);
            loginResponseDTOResult.code(200).message("登录成功").data(loginResponseDTO);
        } else {
            String token = "";
            LoginResponseDTO loginResponseDTO = new LoginResponseDTO(token);
            logger.info("用户 {} 密码错误", username);
            loginResponseDTOResult.code(400).message("用户名或密码错误").data(loginResponseDTO);
        }
        return loginResponseDTOResult;
    }

}
