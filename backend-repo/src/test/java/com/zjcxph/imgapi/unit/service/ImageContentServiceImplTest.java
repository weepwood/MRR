package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.exception.OssErrorType;
import com.zjcxph.imgapi.exception.OssOperationException;
import com.zjcxph.imgapi.mapper.ImageContentMapper;
import com.zjcxph.imgapi.service.ImageContentService;
import com.zjcxph.imgapi.service.impl.ImageContentServiceImpl;
import com.zjcxph.imgapi.storage.ImageStorage;
import com.zjcxph.imgapi.storage.InvalidImagePathException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;

import java.io.ByteArrayInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("ImageContentServiceImpl 内部影像内容服务测试")
@ExtendWith(MockitoExtension.class)
class ImageContentServiceImplTest {

    @Mock
    private ImageContentMapper imageContentMapper;

    @Mock
    private ImageStorage imageStorage;

    private ImageContentServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ImageContentServiceImpl(imageContentMapper, imageStorage);
    }

    @Test
    @DisplayName("按有效影像 ID 构造完整 PathDO 并打开统一存储流")
    void opensActiveImageById() throws Exception {
        Scan scan = activeScan();
        InputStream stream = new ByteArrayInputStream(new byte[]{1, 2, 3});
        when(imageContentMapper.findActiveById(12)).thenReturn(scan);
        when(imageStorage.open(any(PathDO.class))).thenReturn(stream);

        ImageContentService.ImageContent content = service.open(12);

        assertThat(content.inputStream()).isSameAs(stream);
        assertThat(content.filename()).isEqualTo("病案首页-01.jpg");
        assertThat(content.mediaType()).isEqualTo(MediaType.IMAGE_JPEG);
        assertThat(content.contentLength()).isEqualTo(1234L);

        ArgumentCaptor<PathDO> pathCaptor = ArgumentCaptor.forClass(PathDO.class);
        verify(imageStorage).open(pathCaptor.capture());
        PathDO path = pathCaptor.getValue();
        assertThat(path.getScanId()).isEqualTo(12);
        assertThat(path.getBah()).isEqualTo("10000001");
        assertThat(path.getSjh()).isEqualTo("12345678");
        assertThat(path.getBrxh()).isEqualTo("605746");
        assertThat(path.getFolder()).isEqualTo("BA01-2026");
        assertThat(path.getFilename()).isEqualTo("病案首页-01.jpg");
        assertThat(path.getSourceType()).isEqualTo("HTTP");
        assertThat(path.getSourceNode()).isEqualTo("BA01");
        assertThat(path.getSourceRef()).isEqualTo("source-reference");
        assertThat(path.getOssUrl()).isEqualTo("medical-records/0001/object.jpg");
        assertThat(path.getFileSize()).isEqualTo(1234L);
    }

    @Test
    @DisplayName("空或负数影像 ID 返回稳定 400")
    void rejectsInvalidId() {
        assertThatThrownBy(() -> service.open(0))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("影像 ID 必须是正整数");
                });

        verify(imageContentMapper, never()).findActiveById(any());
    }

    @Test
    @DisplayName("软删除或不存在的影像返回 404")
    void rejectsMissingOrDeletedImage() {
        when(imageContentMapper.findActiveById(99)).thenReturn(null);

        assertThatThrownBy(() -> service.open(99))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(404);
                    assertThat(exception.getMessage()).isEqualTo("影像不存在或已删除");
                });

        verify(imageStorage, never()).open(any());
    }

    @Test
    @DisplayName("非法影像元数据返回脱敏 400")
    void mapsInvalidPathToSafeBadRequest() throws Exception {
        when(imageContentMapper.findActiveById(12)).thenReturn(activeScan());
        when(imageStorage.open(any()))
                .thenThrow(new InvalidImagePathException("非法服务器路径 C:\\hospital\\secret"));

        assertThatThrownBy(() -> service.open(12))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(400);
                    assertThat(exception.getMessage()).isEqualTo("影像元数据无法安全解析");
                    assertThat(exception.getMessage()).doesNotContain("hospital", "secret");
                });
    }

    @Test
    @DisplayName("全部来源均不存在时返回 404")
    void mapsAllSourcesNotFoundTo404() throws Exception {
        IOException aggregate = new IOException("所有受控图片来源均读取失败");
        aggregate.addSuppressed(new FileNotFoundException("C:\\hospital\\missing.jpg"));
        aggregate.addSuppressed(new OssOperationException(
                OssErrorType.OSS_OBJECT_NOT_FOUND,
                new RuntimeException("medical-records/private/missing.jpg")
        ));
        when(imageContentMapper.findActiveById(12)).thenReturn(activeScan());
        when(imageStorage.open(any())).thenThrow(aggregate);

        assertThatThrownBy(() -> service.open(12))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(404);
                    assertThat(exception.getMessage()).isEqualTo("影像文件不存在");
                    assertThat(exception.getMessage()).doesNotContain("hospital", "medical-records");
                });
    }

    @Test
    @DisplayName("OSS 不可用错误穿过聚合存储层并保持稳定业务码")
    void preservesTypedOssUnavailableFailure() throws Exception {
        OssOperationException ossFailure = new OssOperationException(
                OssErrorType.OSS_UNAVAILABLE,
                new RuntimeException("endpoint http://10.0.0.10:9000 unavailable")
        );
        IOException aggregate = new IOException("所有受控图片来源均读取失败");
        aggregate.addSuppressed(ossFailure);
        when(imageContentMapper.findActiveById(12)).thenReturn(activeScan());
        when(imageStorage.open(any())).thenThrow(aggregate);

        assertThatThrownBy(() -> service.open(12))
                .isSameAs(ossFailure)
                .hasMessage("OSS 服务暂不可用，请稍后重试")
                .doesNotHaveMessageContaining("10.0.0.10");
    }

    @Test
    @DisplayName("未知读取失败返回脱敏 503")
    void mapsUnknownReadFailureTo503() throws Exception {
        when(imageContentMapper.findActiveById(12)).thenReturn(activeScan());
        when(imageStorage.open(any()))
                .thenThrow(new IOException("failed at C:\\hospital\\private\\image.jpg"));

        assertThatThrownBy(() -> service.open(12))
                .isInstanceOfSatisfying(BusinessException.class, exception -> {
                    assertThat(exception.getCode()).isEqualTo(503);
                    assertThat(exception.getMessage()).isEqualTo("影像来源暂不可用");
                    assertThat(exception.getMessage()).doesNotContain("hospital", "private");
                });
    }

    @Test
    @DisplayName("历史文件名中的路径字符和控制字符不会进入响应头")
    void sanitizesHistoricalFilename() throws Exception {
        Scan scan = activeScan();
        scan.setFilename("folder\\sub/image\r\n01.jpg");
        scan.setFileSize(null);
        when(imageContentMapper.findActiveById(12)).thenReturn(scan);
        when(imageStorage.open(any())).thenReturn(new ByteArrayInputStream(new byte[]{1}));

        ImageContentService.ImageContent content = service.open(12);

        assertThat(content.filename()).isEqualTo("folder_sub_image__01.jpg");
        assertThat(content.contentLength()).isNull();
    }

    private Scan activeScan() {
        Scan scan = new Scan();
        scan.setId(12);
        scan.setBah("10000001");
        scan.setSjh("12345678");
        scan.setBrxh("605746");
        scan.setFolder("BA01-2026");
        scan.setFilename("病案首页-01.jpg");
        scan.setSourceType("HTTP");
        scan.setSourceNode("BA01");
        scan.setSourceRef("source-reference");
        scan.setOssUrl("medical-records/0001/object.jpg");
        scan.setFileSize(1234L);
        scan.setUploadFlag(1);
        return scan;
    }
}
