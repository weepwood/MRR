package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.dto.req.ImageRequest;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ArchiveExportService;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.storage.InvalidImagePathException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageController 影像控制器测试")
class ImageControllerTest {

    @Mock
    private ScanService scanService;

    @Mock
    private ArchiveExportService archiveExportService;

    @Mock
    private ImageStorage imageStorage;

    @Mock
    private OssService ossService;

    @Mock
    private ImageUrlService imageUrlService;

    @InjectMocks
    private ImageController imageController;

    private Scan mockScan;

    @BeforeEach
    void setUp() {
        mockScan = new Scan();
        mockScan.setId(1);
        mockScan.setBah("00789508");
        mockScan.setBrxh("605746");
        mockScan.setFilename("test.jpg");
        mockScan.setFolder("25.03.15");
    }

    @Test
    void hello() {
        Result<Map<String, Object>> result = imageController.hello();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("message", "服务正常");
    }

    @Test
    void downloadUsesStreamingArchiveService() {
        ArchiveExportService.BatchZipExport export = new ArchiveExportService.BatchZipExport(
                List.of(new PathDO("25.03.15", "test.jpg", "605746", "00789508"))
        );
        when(archiveExportService.prepareArchive("00789508", "")).thenReturn(export);

        ResponseEntity<StreamingResponseBody> response = imageController.download("789508", null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
                .contains("00789508.zip");
        assertThat(response.getBody()).isNotNull();
    }

    @Test
    void getDataByBAH() {
        when(scanService.getImageListByBAH("00789508", "789508")).thenReturn(List.of(mockScan));
        BAHDataResponseDTO dto = new BAHDataResponseDTO();
        dto.setBah("00789508");
        doReturn(List.of(dto)).when(imageUrlService).toDtoList(anyList());

        Result<List<BAHDataResponseDTO>> result = imageController.getDataByBAH("00789508");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    void getDataByBAH_nullFolder() {
        mockScan.setFolder(null);
        when(scanService.getImageListByBAH("00789508", "789508")).thenReturn(List.of(mockScan));
        BAHDataResponseDTO dto = new BAHDataResponseDTO();
        dto.setBah("00789508");
        dto.setImg_url("http://192.2.1.182:8001/ba-img-00/605746-00789508/test.jpg");
        doReturn(List.of(dto)).when(imageUrlService).toDtoList(anyList());

        Result<List<BAHDataResponseDTO>> result = imageController.getDataByBAH("00789508");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData().get(0).getImg_url())
                .isEqualTo("http://192.2.1.182:8001/ba-img-00/605746-00789508/test.jpg");
    }

    @Test
    void getImage_success() throws Exception {
        when(imageStorage.size(any(PathDO.class))).thenReturn(5L);
        when(imageStorage.open(any(PathDO.class))).thenReturn(
                new ByteArrayInputStream("image".getBytes(StandardCharsets.UTF_8))
        );

        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746", "25.03.15", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(result.getHeaders().getContentLength()).isEqualTo(5L);
        assertThat(result.getHeaders().getContentType()).isNotNull();
    }

    @Test
    void getImage_pathTraversalBlocked() throws Exception {
        when(imageStorage.size(any(PathDO.class)))
                .thenThrow(new InvalidImagePathException("filename 包含非法路径字符"));

        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746", "25.03.15", "../../etc/passwd");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getImage_nullParameterBlocked() throws Exception {
        when(imageStorage.size(any(PathDO.class)))
                .thenThrow(new InvalidImagePathException("bah 不能为空"));

        ResponseEntity<?> result = imageController.getImage(
                null, "605746", "25.03.15", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getImage_shortFolderBlocked() throws Exception {
        when(imageStorage.size(any(PathDO.class)))
                .thenThrow(new InvalidImagePathException("folder 长度不足 5 位"));

        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746", "12.3", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getImage_notFound() throws Exception {
        when(imageStorage.size(any(PathDO.class)))
                .thenThrow(new FileNotFoundException("missing"));

        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746", "25.03.15", "notexist.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void updateImageType_nullBtype() {
        ImageRequest req = new ImageRequest();
        req.setBtype(null);

        Result<Void> result = imageController.updateImageType(1, req);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("不能为空");
    }

    @Test
    void updateImageType_invalidBtype() {
        ImageRequest req = new ImageRequest();
        req.setBtype(99);

        Result<Void> result = imageController.updateImageType(1, req);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("类型错误");
    }
}
