package com.zjcxph.imgapi.dto.resp;

import com.zjcxph.imgapi.entity.DataTransferError;
import com.zjcxph.imgapi.entity.DataTransferFile;
import com.zjcxph.imgapi.entity.DataTransferJob;
import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class DataTransferJobDetailDTO {
    private DataTransferJob job;
    private List<DataTransferFile> files;
    private List<DataTransferError> errors;
}
