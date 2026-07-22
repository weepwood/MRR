package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegistrationResultDTO {
    private Long id;
    private String username;
    private String displayName;
    private String status;
    private LocalDateTime appliedAt;
}
