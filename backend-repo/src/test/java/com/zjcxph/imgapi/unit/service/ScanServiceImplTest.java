package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.service.impl.ScanServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanServiceImpl 扫描服务测试")
class ScanServiceImplTest {

    @Mock
    private ScanMapper scanMapper;

    @Mock
    private ImageProperties imageProperties;

    @InjectMocks
    private ScanServiceImpl scanService;

    private Scan mockScan;

    @BeforeEach
    void setUp() {
        mockScan = new Scan();
        mockScan.setId(1);
        mockScan.setBah("00789508");
        mockScan.setBrxh("605746");
        mockScan.setFilename("test.jpg");
        mockScan.setBtype(1);
        mockScan.setPages(2);
        mockScan.setFolder("25.03.15");
    }

    @Nested
    @DisplayName("查询方法")
    class QueryTests {

        @Test
        @DisplayName("findById — 存在返回Scan")
        void findById_found() {
            when(scanMapper.findById(1)).thenReturn(mockScan);
            assertThat(scanService.findById(1)).isEqualTo(mockScan);
        }

        @Test
        @DisplayName("findById — 不存在返回null")
        void findById_notFound() {
            when(scanMapper.findById(999)).thenReturn(null);
            assertThat(scanService.findById(999)).isNull();
        }

        @Test
        @DisplayName("findAll — 返回全部记录")
        void findAll() {
            when(scanMapper.findAll()).thenReturn(List.of(mockScan));
            assertThat(scanService.findAll()).hasSize(1);
        }

        @Test
        @DisplayName("findByBah — 按病案号查询")
        void findByBah() {
            when(scanMapper.findByBah("00789508")).thenReturn(List.of(mockScan));
            assertThat(scanService.findByBah("00789508")).hasSize(1);
        }

        @Test
        @DisplayName("findByBrxh — 按病人序号查询")
        void findByBrxh() {
            when(scanMapper.findByBrxh("605746")).thenReturn(List.of(mockScan));
            assertThat(scanService.findByBrxh("605746")).hasSize(1);
        }

        @Test
        @DisplayName("findAllWithPagination — 分页查询")
        void findAllWithPagination() {
            when(scanMapper.findAllWithPagination(0, 10)).thenReturn(List.of(mockScan));
            List<Scan> result = scanService.findAllWithPagination(1, 10);
            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("findAllWithPagination — 非法分页参数抛异常")
        void findAllWithPagination_invalidPage() {
            assertThatThrownBy(() -> scanService.findAllWithPagination(0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("findByCondition — 条件查询")
        void findByCondition() {
            ScanRequest request = new ScanRequest();
            when(scanMapper.findByCondition(request)).thenReturn(List.of(mockScan));
            assertThat(scanService.findByCondition(request)).hasSize(1);
        }
    }

    @Nested
    @DisplayName("写入方法")
    class WriteTests {

        @Test
        @DisplayName("create — 成功创建返回Scan")
        void create_success() {
            when(scanMapper.insert(mockScan)).thenReturn(1);
            Scan result = scanService.create(mockScan);
            assertThat(result).isNotNull();
            assertThat(result.getBah()).isEqualTo("00789508");
        }

        @Test
        @DisplayName("create — 失败返回null")
        void create_fail() {
            when(scanMapper.insert(mockScan)).thenReturn(0);
            assertThat(scanService.create(mockScan)).isNull();
        }

        @Test
        @DisplayName("update — 成功更新返回Scan")
        void update_success() {
            when(scanMapper.update(mockScan)).thenReturn(1);
            Scan result = scanService.update(mockScan);
            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("update — 失败返回null")
        void update_fail() {
            when(scanMapper.update(mockScan)).thenReturn(0);
            assertThat(scanService.update(mockScan)).isNull();
        }

        @Test
        @DisplayName("softDeleteById — 成功返回true")
        void softDeleteById_success() {
            when(scanMapper.softDeleteById(1)).thenReturn(1);
            assertThat(scanService.softDeleteById(1)).isTrue();
        }

        @Test
        @DisplayName("softDeleteById — 失败返回false")
        void softDeleteById_fail() {
            when(scanMapper.softDeleteById(999)).thenReturn(0);
            assertThat(scanService.softDeleteById(999)).isFalse();
        }

        @Test
        @DisplayName("updateImageType — 更新类型返回影响行数")
        void updateImageType() {
            when(scanMapper.updateImageType(1, 2)).thenReturn(1);
            assertThat(scanService.updateImageType(1, 2)).isEqualTo(1);
        }
    }
}
