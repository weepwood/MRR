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
    void legacyPathStillQueriesPatientsAndForbidsCaching() throws Exception {
        String synthetic = "000000000000000000";
        when(mapper.findBAHByIDCard(synthetic)).thenReturn(List.of(new Patient()));
        mvc.perform(get("/api/v1/patients/idcard/" + synthetic))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "private, no-store"))
                .andExpect(jsonPath("$.data.length()").value(1));
        verify(mapper).findBAHByIDCard(synthetic);
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
    void realAuthorizationInterceptorProtectsBothProtocols() throws Exception {
        var guarded = MockMvcBuilders.standaloneSetup(new PatientController(mapper,
                mock(PatientImportService.class), mock(PatientAnalyticsService.class)))
                .addInterceptors(new com.zjcxph.imgapi.interceptors.AuthorizationInterceptor()).build();
        var denied = new com.zjcxph.imgapi.common.AuthSession();
        denied.setRoleCode("OPERATOR");
        denied.setPermissions(List.of("search:read"));
        var allowed = new com.zjcxph.imgapi.common.AuthSession();
        allowed.setRoleCode("OPERATOR");
        allowed.setPermissions(List.of("record:read"));
        String synthetic = "000000000000000000";
        String body = "{\"idCard\":\"" + synthetic + "\"}";
        guarded.perform(post("/api/v1/patients/idcard").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized());
        guarded.perform(post("/api/v1/patients/idcard").contentType(MediaType.APPLICATION_JSON).content(body)
                        .requestAttr(com.zjcxph.imgapi.interceptors.AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, denied))
                .andExpect(status().isForbidden());
        guarded.perform(get("/api/v1/patients/idcard/" + synthetic)
                        .requestAttr(com.zjcxph.imgapi.interceptors.AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, denied))
                .andExpect(status().isForbidden());
        verifyNoInteractions(mapper);
        when(mapper.findBAHByIDCard(synthetic)).thenReturn(List.of(new Patient()));
        guarded.perform(post("/api/v1/patients/idcard").contentType(MediaType.APPLICATION_JSON).content(body)
                        .requestAttr(com.zjcxph.imgapi.interceptors.AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE, allowed))
                .andExpect(status().isOk()).andExpect(jsonPath("$.data.length()").value(1));
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
