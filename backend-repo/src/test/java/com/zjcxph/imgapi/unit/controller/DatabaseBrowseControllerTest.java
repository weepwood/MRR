package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.DatabaseBrowseController;
import com.zjcxph.imgapi.dto.resp.browse.DbBrowsePage;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.service.DatabaseBrowseService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("受控只读数据库浏览控制器测试")
class DatabaseBrowseControllerTest {

    @Mock
    private DatabaseBrowseService databaseBrowseService;

    @InjectMocks
    private DatabaseBrowseController controller;

    @Test
    @DisplayName("page 非数字返回 3002，而不是被全局兜底成 500")
    void rejectsNonNumericPage() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.rows("demo", null, null, "desc", "abc", "50", null, response))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(3002);

        verify(databaseBrowseService, never()).readRows(anyString(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("size 非数字返回 3002")
    void rejectsNonNumericSize() {
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> controller.rows("demo", null, null, "desc", "1", "1e9", null, response))
                .isInstanceOf(BusinessException.class)
                .extracting(e -> ((BusinessException) e).getCode())
                .isEqualTo(3002);

        verify(databaseBrowseService, never()).readRows(anyString(), any(), any(), any(), anyInt(), anyInt(), any());
    }

    @Test
    @DisplayName("成功响应带 no-store，且缓存头在取数前已设置")
    void setsNoStoreBeforeQuerying() {
        MockHttpServletResponse response = new MockHttpServletResponse();
        DbBrowsePage page = new DbBrowsePage("demo", List.of(), List.of(), 1, 50, false, null,
                null, false, 0, List.of(), 3L);
        when(databaseBrowseService.readRows(eq("demo"), isNull(), isNull(), eq("desc"), eq(1), eq(50), isNull()))
                .thenReturn(page);

        controller.rows("demo", null, null, "desc", "1", "50", null, response);

        assertThat(response.getHeader("Cache-Control")).isEqualTo("no-store");
        assertThat(response.getHeader("X-MRR-Browse-Rows")).isEqualTo("0");
    }
}
