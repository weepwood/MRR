package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class ExternalArchiveSessionResponse {
    private String clientId;
    private String externalUserId;
    private boolean allowDownload;
    private int expiresIn;
    private List<ExternalArchiveCaseDTO> cases;
}
