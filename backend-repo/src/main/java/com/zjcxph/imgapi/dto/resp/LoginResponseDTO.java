package com.zjcxph.imgapi.dto.resp;

import lombok.Data;
import com.zjcxph.imgapi.common.AuthSession;

@Data
public class LoginResponseDTO {
    private String token;
    private AuthSession user;

    public LoginResponseDTO() {}

    public LoginResponseDTO(String token, AuthSession user) {
        this.token = token;
        this.user = user;
    }

}
