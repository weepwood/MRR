package com.zjcxph.imgapi.dto.req;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class DataTransferInboxRequest {
    @NotBlank
    private String entityType;

    private String importMode = "SKIP_DUPLICATES";

    @NotEmpty
    private List<String> filenames;
}
