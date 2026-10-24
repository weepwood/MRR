package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.PatientController;
import com.zjcxph.imgapi.controller.SearchController;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.handler.ResultHttpStatusAdvice;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.service.PatientAnalyticsService;
import com.zjcxph.imgapi.service.PatientImportService;
import com.zjcxph.imgapi.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class LegacyIdCardQueryMockMvcTest {

    private static final String ID_CARD = "110101199001011234";

    @Test
    void oldSearchGetRoutesReturnGoneWithoutQueryingPatientData() throws Exception {
        SearchService searchService = mock(SearchService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new ResultHttpStatusAdvice()).build();

        mvc.perform(get("/api/v1/search/getBAHByEncryptID")
                        .param("EncryptID", "invalid").param("userId", "test")
                        .param("iv", "invalid").param("timestamp", "0"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410))
                .andExpect(jsonPath("$.message").value("旧身份证查询接口已停用，请改用受控 POST 查询或外部 Ticket"));
        mvc.perform(get("/api/v1/search/getBAHByEncryptIDLegacy")
                        .param("EncryptID", "invalid").param("userId", "test").param("iv", "invalid"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));
        mvc.perform(get("/api/v1/search/getBAHByID/{idCard}", ID_CARD))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));

        verifyNoInteractions(searchService);
    }

    @Test
    void patientIdCardGetRouteReturnsGoneWithoutQueryingPatientData() throws Exception {
        SearchMapper searchMapper = mock(SearchMapper.class);
        PatientController controller = new PatientController(searchMapper,
                mock(PatientImportService.class), mock(PatientAnalyticsService.class));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ResultHttpStatusAdvice()).build();

        mvc.perform(get("/api/v1/patients/idcard/{idCard}", ID_CARD))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));

        verifyNoInteractions(searchMapper);
    }

    @Test
    void protectedPostSearchKeepsWorking() throws Exception {
        SearchService searchService = mock(SearchService.class);
        Patient patient = new Patient();
        patient.setBah("00000001");
        when(searchService.getBAHByID(ID_CARD)).thenReturn(List.of(patient));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SearchController(searchService))
                .setControllerAdvice(new ResultHttpStatusAdvice()).build();

        mvc.perform(post("/api/v1/search/getBAHByID")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCard\":\"" + ID_CARD + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].bah").value("00000001"));
    }
}
