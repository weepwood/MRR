package com.zjcxph.imgapi.dto.req;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.time.LocalDateTime;

/** Client-editable fields only; database identity and owner always come from the server. */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ArchiveSearchHistoryRequest {
    private String bah;
    private String sjh;
    private boolean success;
    private Integer imageCount;
    private Integer queryCount;
    private String failureReason;
    private boolean favorite;
    private LocalDateTime searchedAt;
}
