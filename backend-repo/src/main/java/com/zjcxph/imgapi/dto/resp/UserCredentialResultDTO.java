package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
public class UserCredentialResultDTO {
    private AuthUserProfileDTO user;
    private String temporaryPassword;
    private LocalDateTime temporaryPasswordExpiresAt;
}
