package com.zjcxph.imgapi.service;

import com.zjcxph.imgapi.common.AuthSession;
import com.zjcxph.imgapi.config.ArchiveExportProperties;
import com.zjcxph.imgapi.dto.req.ArchiveExportJobRequest;
import com.zjcxph.imgapi.exception.BusinessException;
import com.zjcxph.imgapi.repository.ArchiveExportJobRepository;
import com.zjcxph.imgapi.service.impl.ArchiveExportTempFileManager;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.Executor;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ArchiveExportJobServiceValidationTest {

    @Test
    void recordReadAloneCannotCreateZipJob() {
        ArchiveExportService exportService = mock(ArchiveExportService.class);
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportJobService service = service(exportService, repository);
        AuthSession session = session(List.of("record:read"));
        ArchiveExportJobRequest request = new ArchiveExportJobRequest();
        request.setFormat("ZIP");
        request.setBah("789508");

        assertThatThrownBy(() -> service.create(session, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("没有病案导出权限");
        verifyNoInteractions(exportService, repository);
    }

    @Test
    void highBahRequiresSjhForWholeArchiveJob() {
        ArchiveExportService exportService = mock(ArchiveExportService.class);
        ArchiveExportJobRepository repository = mock(ArchiveExportJobRepository.class);
        ArchiveExportJobService service = service(exportService, repository);
        AuthSession session = session(List.of("record:download"));
        ArchiveExportJobRequest request = new ArchiveExportJobRequest();
        request.setFormat("ZIP");
        request.setBah("10000000");

        assertThatThrownBy(() -> service.create(session, request))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须使用上架号");
        verifyNoInteractions(exportService, repository);
    }

    private ArchiveExportJobService service(
            ArchiveExportService exportService,
            ArchiveExportJobRepository repository) {
        return new ArchiveExportJobService(
                exportService,
                repository,
                mock(ArchiveExportTempFileManager.class),
                new ArchiveExportProperties(),
                (Executor) Runnable::run);
    }

    private AuthSession session(List<String> permissions) {
        AuthSession session = new AuthSession();
        session.setId(1L);
        session.setUsername("tester");
        session.setRoleCode("USER");
        session.setPermissions(permissions);
        return session;
    }
}
