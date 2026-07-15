package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.dto.req.ScanRequest;
import com.zjcxph.imgapi.dto.resp.CursorPageResult;
import com.zjcxph.imgapi.entity.Scan;
import com.zjcxph.imgapi.mapper.ScanMapper;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ScanServiceImpl 扫描服务测试")
class ScanServiceImplTest {

    @Mock
    private ScanMapper scanMapper;

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
        void findById_found() {
            when(scanMapper.findById(1)).thenReturn(mockScan);
            assertThat(scanService.findById(1)).isEqualTo(mockScan);
        }

        @Test
        void findById_notFound() {
            when(scanMapper.findById(999)).thenReturn(null);
            assertThat(scanService.findById(999)).isNull();
        }

        @Test
        @DisplayName("findAll 在服务层将旧接口限制为最多 1000 条")
        void findAll_limitsLegacyQuery() {
            when(scanMapper.findAll(1000)).thenReturn(List.of(mockScan));

            assertThat(scanService.findAll(5000)).containsExactly(mockScan);
            verify(scanMapper).findAll(1000);
        }

        @Test
        void findByBah() {
            when(scanMapper.findByBah("00789508", "789508")).thenReturn(List.of(mockScan));
            assertThat(scanService.findByBah("00789508")).hasSize(1);
        }

        @Test
        void findByBrxh() {
            when(scanMapper.findByBrxh("605746")).thenReturn(List.of(mockScan));
            assertThat(scanService.findByBrxh("605746")).hasSize(1);
        }

        @Test
        void findAllWithPagination() {
            when(scanMapper.findAllWithPagination(0, 10)).thenReturn(List.of(mockScan));
            assertThat(scanService.findAllWithPagination(1, 10)).hasSize(1);
        }

        @Test
        void findAllWithPagination_invalidPage() {
            assertThatThrownBy(() -> scanService.findAllWithPagination(0, 10))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("游标分页多取一条判断是否存在下一页")
        void findAfterId_hasMore() {
            Scan second = new Scan();
            second.setId(2);
            Scan lookAhead = new Scan();
            lookAhead.setId(3);
            when(scanMapper.findAfterId(0, 3)).thenReturn(List.of(mockScan, second, lookAhead));

            CursorPageResult<Scan> result = scanService.findAfterId(0, 2);

            assertThat(result.getList()).extracting(Scan::getId).containsExactly(1, 2);
            assertThat(result.isHasMore()).isTrue();
            assertThat(result.getNextCursorId()).isEqualTo(2L);
        }

        @Test
        void findAfterId_lastPage() {
            when(scanMapper.findAfterId(10, 3)).thenReturn(List.of(mockScan));

            CursorPageResult<Scan> result = scanService.findAfterId(10, 2);

            assertThat(result.isHasMore()).isFalse();
            assertThat(result.getNextCursorId()).isNull();
        }

        @Test
        @DisplayName("旧条件查询在数据库层限制返回数量")
        void findByCondition_isLimited() {
            ScanRequest request = new ScanRequest();
            when(scanMapper.findByCondition(request, 1000)).thenReturn(List.of(mockScan));

            assertThat(scanService.findByCondition(request, 5000)).hasSize(1);
            verify(scanMapper).findByCondition(request, 1000);
        }
    }

    @Nested
    @DisplayName("写入方法")
    class WriteTests {

        @Test
        void create_successReturnsPersistedState() {
            when(scanMapper.insert(mockScan)).thenReturn(1);
            when(scanMapper.findById(1)).thenReturn(mockScan);

            Scan result = scanService.create(mockScan);

            assertThat(result).isSameAs(mockScan);
        }

        @Test
        void create_fail() {
            when(scanMapper.insert(mockScan)).thenReturn(0);
            assertThat(scanService.create(mockScan)).isNull();
        }

        @Test
        void update_successReturnsPersistedState() {
            when(scanMapper.update(mockScan)).thenReturn(1);
            when(scanMapper.findById(1)).thenReturn(mockScan);

            assertThat(scanService.update(mockScan)).isSameAs(mockScan);
        }

        @Test
        void update_fail() {
            when(scanMapper.update(mockScan)).thenReturn(0);
            assertThat(scanService.update(mockScan)).isNull();
        }

        @Test
        void softDeleteById_success() {
            when(scanMapper.softDeleteById(1)).thenReturn(1);
            assertThat(scanService.softDeleteById(1)).isTrue();
        }

        @Test
        void softDeleteById_fail() {
            when(scanMapper.softDeleteById(999)).thenReturn(0);
            assertThat(scanService.softDeleteById(999)).isFalse();
        }

        @Test
        void updateImageType() {
            when(scanMapper.updateImageType(1, 2)).thenReturn(1);
            assertThat(scanService.updateImageType(1, 2)).isEqualTo(1);
        }
    }
}
