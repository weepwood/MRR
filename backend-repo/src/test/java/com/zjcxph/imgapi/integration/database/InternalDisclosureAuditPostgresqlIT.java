package com.zjcxph.imgapi.integration.database;

import com.zjcxph.imgapi.audit.MedicalAuditAdmissionInterceptor;
import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.controller.*;
import com.zjcxph.imgapi.integration.PostgresqlIntegrationTestSupport;
import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.service.*;
import com.zjcxph.imgapi.mapper.SearchMapper;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.boot.test.autoconfigure.MybatisTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.flyway.autoconfigure.FlywayAutoConfiguration;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.http.MediaType;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@MybatisTest
@Import(MedicalAuditAdmissionInterceptor.class)
@ImportAutoConfiguration(FlywayAutoConfiguration.class)
@AutoConfigureTestDatabase(replace=AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation=Propagation.NOT_SUPPORTED)
class InternalDisclosureAuditPostgresqlIT extends PostgresqlIntegrationTestSupport {
    @Autowired JdbcTemplate jdbc;
    @Autowired MedicalAuditAdmissionInterceptor gate;
    static final long ACTOR=987654321L;
    final ImageContentService images=mock(ImageContentService.class);
    final ArchiveExportService archiveExports=mock(ArchiveExportService.class);
    final DataExchangeExportService exports=mock(DataExchangeExportService.class);
    final SearchMapper patients=mock(SearchMapper.class);

    private MockMvc mvc() {
        return MockMvcBuilders.standaloneSetup(new ImageContentController(images),
                new ArchiveExportController(archiveExports),
                new DataExchangeController(mock(PatientImportService.class),mock(StatisticsDataExchangeService.class),
                        mock(ArchiveBoxDataExchangeService.class),mock(ScanDataExchangeService.class),exports),
                new PatientController(patients,mock(PatientImportService.class),mock(PatientAnalyticsService.class)))
                .addInterceptors(gate).build();
    }

    private MockHttpServletRequestBuilder admitted(MockHttpServletRequestBuilder request,String trace) {
        var actor=new AuthSession();actor.setId(ACTOR);actor.setRoleCode("ADMIN");
        return request.requestAttr(AuthorizationInterceptor.AUTH_SESSION_ATTRIBUTE,actor).requestAttr("requestId",trace);
    }

    @ParameterizedTest
    @ValueSource(strings={"image","zip","csv","idcard"})
    void dedicatedLedgerFailurePreventsActualMvcDisclosure(String action) throws Exception {
        String trace="synthetic-"+UUID.randomUUID();
        when(images.open(42)).thenReturn(new ImageContentService.ImageContent(
                new java.io.ByteArrayInputStream(new byte[]{1,2,3}),"synthetic.jpg",MediaType.IMAGE_JPEG,3L));
        when(archiveExports.prepareArchive(anyString(),anyString())).thenReturn(new ArchiveExportService.BatchZipExport(java.util.List.of(new com.zjcxph.imgapi.entity.PathDO())));
        var request=switch(action) {
            case "image"->get("/api/v1/img/content/42");
            case "zip"->get("/api/v1/archive-exports/zip").param("bah","7654316");
            case "csv"->get("/api/v1/data-exchange/patients/export/csv").param("keyword","synthetic-private");
            case "idcard"->get("/api/v1/patients/idcard/990000200001010001");
            default->throw new AssertionError(action);
        };
        try {
            jdbc.execute("ALTER TABLE app.medical_audit_event ADD CONSTRAINT ck_test_internal_audit_reject CHECK(actor_id<>987654321) NOT VALID");
            mvc().perform(admitted(request,trace)).andExpect(status().isServiceUnavailable());
            verifyNoInteractions(images,archiveExports,exports,patients);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE request_id=?",Long.class,trace)).isZero();
            // Template download is not disclosure and must remain available.
            mvc().perform(admitted(get("/api/v1/data-exchange/patients/template"),trace)).andExpect(status().isOk());
        } finally {
            jdbc.execute("ALTER TABLE app.medical_audit_event DROP CONSTRAINT IF EXISTS ck_test_internal_audit_reject");
            jdbc.update("DELETE FROM app.medical_audit_event WHERE request_id=?",trace);
        }
    }

    @Test void terminalLedgerFailureDoesNotEraseConfirmedAdmission() throws Exception {
        String trace="synthetic-"+UUID.randomUUID();
        try {
            doAnswer(call->{
                assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE request_id=?",String.class,trace)).containsExactly("INTENT");
                call.<java.io.Writer>getArgument(1).write("synthetic");
                return null;
            }).when(exports).exportPatients(any(),any());
            jdbc.execute("ALTER TABLE app.medical_audit_event ADD CONSTRAINT ck_test_internal_outcome_reject CHECK(actor_id<>987654321 OR phase='INTENT') NOT VALID");
            mvc().perform(admitted(get("/api/v1/data-exchange/patients/export/csv"),trace)).andExpect(status().isOk());
            assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE request_id=?",String.class,trace)).containsExactly("INTENT");
        } finally {
            jdbc.execute("ALTER TABLE app.medical_audit_event DROP CONSTRAINT IF EXISTS ck_test_internal_outcome_reject");
            jdbc.update("DELETE FROM app.medical_audit_event WHERE request_id=?",trace);
        }
    }

    @Test void confirmedIntentPrecedesSourceOpenAndAsyncOutcomeIsNotDuplicate() throws Exception {
        String trace="synthetic-"+UUID.randomUUID();
        try {
            when(images.open(42)).thenAnswer(call->{
                assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE request_id=?",String.class,trace)).containsExactly("INTENT");
                return new ImageContentService.ImageContent(new java.io.ByteArrayInputStream(new byte[]{1,2,3}),"synthetic.jpg",MediaType.IMAGE_JPEG,3L);
            });
            var mvc=mvc();
            var pending=mvc.perform(admitted(get("/api/v1/img/content/42").param("secret","synthetic-private"),trace))
                    .andExpect(request().asyncStarted()).andReturn();
            mvc.perform(asyncDispatch(pending)).andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1,2,3}));
            assertThat(jdbc.queryForList("SELECT phase FROM app.medical_audit_event WHERE request_id=? ORDER BY phase",String.class,trace)).containsExactly("INTENT","OUTCOME");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM app.medical_audit_event WHERE request_id=? AND actor_id=? AND route='/api/v1/img/content/{id}' AND target NOT LIKE '%synthetic-private%'",Long.class,trace,ACTOR)).isEqualTo(2L);
            assertThat(jdbc.queryForObject("SELECT observation FROM app.medical_audit_event WHERE request_id=? AND phase='OUTCOME'",String.class,trace)).isEqualTo("HTTP_COMPLETED");
        } finally {
            jdbc.update("DELETE FROM app.medical_audit_event WHERE request_id=?",trace);
        }
    }
}
