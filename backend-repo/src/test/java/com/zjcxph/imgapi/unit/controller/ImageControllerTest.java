package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.ImageController;
import com.zjcxph.imgapi.dto.resp.BAHDataResponseDTO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.service.ImageUrlService;
import com.zjcxph.imgapi.service.OssService;
import com.zjcxph.imgapi.service.PdfService;
import com.zjcxph.imgapi.service.ScanService;
import com.zjcxph.imgapi.config.ImageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ImageController 影像控制器测试")
class ImageControllerTest {

    @Mock
    private ImageProperties imageProperties;

    @Mock
    private ScanService scanService;

    @Mock
    private PdfService pdfService;

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
    @DisplayName("hello — 返回服务正常")
    void hello() {
        Result<Map<String, Object>> result = imageController.hello();
        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).containsEntry("message", "服务正常");
    }

    @Test
    @DisplayName("getDataByBAH — 返回图片列表")
    void getDataByBAH() {
        when(scanService.getImageListByBAH("00789508", "00789508")).thenReturn(List.of(mockScan));
        BAHDataResponseDTO dto = new BAHDataResponseDTO();
        dto.setBah("00789508");
        doReturn(List.of(dto)).when(imageUrlService).toDtoList(anyList());

        Result<List<BAHDataResponseDTO>> result = imageController.getDataByBAH("00789508");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
    }

    @Test
    @DisplayName("getDataByBAH — folder为null时返回ba-img-00的img_url")
    void getDataByBAH_nullFolder() {
        mockScan.setFolder(null);
        when(scanService.getImageListByBAH("00789508", "00789508")).thenReturn(List.of(mockScan));
        BAHDataResponseDTO dto = new BAHDataResponseDTO();
        dto.setBah("00789508");
        dto.setImg_url("http://192.2.1.182:8001/ba-img-00/605746-00789508/test.jpg");
        doReturn(List.of(dto)).when(imageUrlService).toDtoList(anyList());

        Result<List<BAHDataResponseDTO>> result = imageController.getDataByBAH("00789508");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getImg_url()).isEqualTo("http://192.2.1.182:8001/ba-img-00/605746-00789508/test.jpg");
    }

    @Test
    @DisplayName("getDataByBAH — brxh为null时仍返回记录，img_url为null")
    void getDataByBAH_nullBrxh() {
        mockScan.setBrxh(null);
        mockScan.setFolder("25.03.15");
        when(scanService.getImageListByBAH("00789508", "00789508")).thenReturn(List.of(mockScan));
        BAHDataResponseDTO dto = new BAHDataResponseDTO();
        dto.setBah("00789508");
        dto.setImg_url(null);
        doReturn(List.of(dto)).when(imageUrlService).toDtoList(anyList());

        Result<List<BAHDataResponseDTO>> result = imageController.getDataByBAH("00789508");

        assertThat(result.getCode()).isEqualTo(200);
        assertThat(result.getData()).hasSize(1);
        assertThat(result.getData().get(0).getImg_url()).isNull();
    }

    @Test
    @DisplayName("getImage — 路径遍历攻击被拦截")
    void getImage_pathTraversal_blocked() {
        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746",
                "25.03.15", "../../etc/passwd");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getImage — folder包含..被拦截")
    void getImage_folderDotDot() {
        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746",
                "../config", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getImage — BAH包含..被拦截")
    void getImage_bahDotDot() {
        ResponseEntity<?> result = imageController.getImage(
                "../admin", "605746",
                "25.03.15", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getImage — null参数被拦截")
    void getImage_nullParam() {
        ResponseEntity<?> result = imageController.getImage(null, "605746", "25.03.15", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getImage — folder长度不足5被拦截")
    void getImage_shortFolder() {
        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746",
                "12.3", "test.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    @DisplayName("getImage — 文件不存在返回404")
    void getImage_notFound() {
        when(imageProperties.getBasePath()).thenReturn("C:/nonexistent");

        ResponseEntity<?> result = imageController.getImage(
                "00789508", "605746",
                "25.03.15", "notexist.jpg");

        assertThat(result.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    @DisplayName("updateImageType — btype为null返回失败")
    void updateImageType_nullBtype() {
        com.zjcxph.imgapi.dto.req.ImageRequest req = new com.zjcxph.imgapi.dto.req.ImageRequest();
        req.setBtype(null);

        Result<Void> result = imageController.updateImageType(1, req);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("不能为空");
    }

    @Test
    @DisplayName("updateImageType — btype越界返回失败")
    void updateImageType_invalidBtype() {
        com.zjcxph.imgapi.dto.req.ImageRequest req = new com.zjcxph.imgapi.dto.req.ImageRequest();
        req.setBtype(99);

        Result<Void> result = imageController.updateImageType(1, req);

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("类型错误");
    }
}
