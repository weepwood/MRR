package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.dto.req.DataTransferExportRequest;
import com.zjcxph.imgapi.dto.req.DataTransferInboxRequest;
import com.zjcxph.imgapi.dto.resp.DataTransferJobDetailDTO;
import com.zjcxph.imgapi.entity.DataTransferJob;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface DataTransferService {
    DataTransferJob createUploadImportJob(String entityType, String importMode, List<MultipartFile> files);

    DataTransferJob createInboxImportJob(DataTransferInboxRequest request);

    DataTransferJob createExportJob(DataTransferExportRequest request);

    void execute(long jobId);

    void pause(long jobId);

    void resume(long jobId);

    void cancel(long jobId);

    void retry(long jobId);

    DataTransferJobDetailDTO findDetail(long jobId);

    List<DataTransferJob> findJobs(int limit);

    List<String> listInboxFiles();
}
