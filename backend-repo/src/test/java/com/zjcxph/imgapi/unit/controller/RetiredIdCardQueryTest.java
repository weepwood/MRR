package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.SearchController;
import com.zjcxph.imgapi.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RetiredIdCardQueryTest {

    @Test
    void retiredGetRoutesReturnHttpGoneWithoutQueryingPatients() throws Exception {
        SearchService service = mock(SearchService.class);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new SearchController(service)).build();

        for (String route : new String[] {
                "/api/v1/search/getBAHByEncryptID",
                "/api/v1/search/getBAHByEncryptIDLegacy",
                "/api/v1/search/getBAHByID/synthetic-id"
        }) {
            mvc.perform(get(route))
                    .andExpect(status().isGone())
                    .andExpect(jsonPath("$.code").value(410));
        }
        verifyNoInteractions(service);
    }
}
