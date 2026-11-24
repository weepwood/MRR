package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.PatientController;
import com.zjcxph.imgapi.entity.Patient;
import com.zjcxph.imgapi.mapper.SearchMapper;
import com.zjcxph.imgapi.service.PatientAnalyticsService;
import com.zjcxph.imgapi.service.PatientImportService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PatientIdCardProtocolTest {
    private final SearchMapper mapper = mock(SearchMapper.class);
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(
            new PatientController(mapper, mock(PatientImportService.class),
                    mock(PatientAnalyticsService.class))).build();

    @Test
    void legacyPathNeverQueriesPatients() throws Exception {
        mvc.perform(get("/api/v1/patients/idcard/synthetic-marker"))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.code").value(410));
        verifyNoInteractions(mapper);
    }

    @Test
    void postKeepsExactLookupAndForbidsCaching() throws Exception {
        String synthetic = "000000000000000000";
        when(mapper.findBAHByIDCard(synthetic)).thenReturn(List.of(new Patient()));
        mvc.perform(post("/api/v1/patients/idcard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCard\":\"" + synthetic + "\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(mapper).findBAHByIDCard(synthetic);
    }

    @Test
    void invalidPostNeverQueriesPatients() throws Exception {
        mvc.perform(post("/api/v1/patients/idcard")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"idCard\":\"invalid\"}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(mapper);
    }
}
