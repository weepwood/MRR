package com.zjcxph.imgapi.dto.resp;

import lombok.Data;
import com.zjcxph.imgapi.common.AuthSession;

@Data
public class LoginResponseDTO {
    private String token;
    private AuthSession user;
    private String nextAction;

    public LoginResponseDTO() {}

    public LoginResponseDTO(String token, AuthSession user) {
        this(token, user, user != null && user.isPasswordChangeRequired() ? "CHANGE_PASSWORD" : "NONE");
    }

    public LoginResponseDTO(String token, AuthSession user, String nextAction) {
        this.token = token;
        this.user = user;
        this.nextAction = nextAction;
    }
}
