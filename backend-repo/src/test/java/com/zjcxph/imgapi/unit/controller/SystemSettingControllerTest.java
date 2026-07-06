package com.zjcxph.imgapi.unit.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.controller.SystemSettingController;
import com.zjcxph.imgapi.service.SystemSettingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SystemSettingController.class)
@DisplayName("SystemSettingController 控制器测试")
class SystemSettingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SystemSettingService systemSettingService;

    @Test
    @DisplayName("GET /api/v1/settings — 返回全部设置")
    void getAllSettings() throws Exception {
        Map<String, String> settings = new LinkedHashMap<>();
        settings.put("systemName", "MRR");
        settings.put("logLevel", "info");
        when(systemSettingService.getAllSettings()).thenReturn(settings);

        mockMvc.perform(get("/api/v1/settings"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.systemName").value("MRR"));
    }

    @Test
    @DisplayName("PUT /api/v1/settings — 批量保存成功")
    void saveSettings() throws Exception {
        Map<String, String> body = Map.of("systemName", "MRR-Prod");
        doNothing().when(systemSettingService).saveSettings(anyMap(), eq(null));

        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("PUT /api/v1/settings — 空 body 返回 400")
    void saveSettings_emptyBody() throws Exception {
        mockMvc.perform(put("/api/v1/settings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("GET /api/v1/settings/{key} — 存在时返回 200")
    void getSetting_found() throws Exception {
        when(systemSettingService.getSetting("logLevel")).thenReturn("info");

        mockMvc.perform(get("/api/v1/settings/logLevel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data").value("info"));
    }

    @Test
    @DisplayName("GET /api/v1/settings/{key} — 不存在返回 404")
    void getSetting_notFound() throws Exception {
        when(systemSettingService.getSetting("unknown")).thenReturn(null);

        mockMvc.perform(get("/api/v1/settings/unknown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    @DisplayName("DELETE /api/v1/settings/{key} — 删除成功")
    void deleteSetting() throws Exception {
        doNothing().when(systemSettingService).deleteSetting("key");

        mockMvc.perform(delete("/api/v1/settings/key"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }
}
