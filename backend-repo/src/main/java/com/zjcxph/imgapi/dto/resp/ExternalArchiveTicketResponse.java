package com.zjcxph.imgapi.dto.resp;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ExternalArchiveTicketResponse {
    private String ticket;
    private String launchUrl;
    private int expiresIn;
    private int archiveCount;
}
