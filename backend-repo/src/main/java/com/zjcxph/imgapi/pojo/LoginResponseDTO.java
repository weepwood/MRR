package com.zjcxph.imgapi.pojo;

public class LoginResponseDTO {
    private String token;
    private AuthSession user;

    public LoginResponseDTO() {}

    public LoginResponseDTO(String token, AuthSession user) {
        this.token = token;
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public AuthSession getUser() {
        return user;
    }

    public void setUser(AuthSession user) {
        this.user = user;
    }
}
