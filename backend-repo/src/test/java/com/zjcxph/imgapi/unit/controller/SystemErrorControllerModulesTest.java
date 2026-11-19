package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.common.Result;
import com.zjcxph.imgapi.controller.SystemErrorController;
import com.zjcxph.imgapi.service.SystemErrorEventService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SystemErrorController 模块筛选项接口")
class SystemErrorControllerModulesTest {

    @Mock
    private SystemErrorEventService service;

    @InjectMocks
    private SystemErrorController controller;

    @Test
    @DisplayName("modules 透传 service 去重模块名")
    void delegatesToListModules() {
        when(service.listModules()).thenReturn(List.of("controller", "service", "storage"));

        Result<List<String>> result = controller.modules();

        assertThat(result.getData()).containsExactly("controller", "service", "storage");
        verify(service).listModules();
    }

    @Test
    @DisplayName("无模块数据时返回空列表")
    void emptyModules() {
        when(service.listModules()).thenReturn(List.of());

        Result<List<String>> result = controller.modules();

        assertThat(result.getData()).isEmpty();
    }
}
