package com.zjcxph.imgapi.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zjcxph.imgapi.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class NginxRuntimeObservationTest {
    @TempDir Path directory;
    private Path fixture() throws Exception {
        Path root=directory.toRealPath();
        Files.createDirectories(root.resolve("runtime/nginx"));
        Files.createDirectories(root.resolve("state"));
        Files.createDirectories(root.resolve("logs/nginx"));
        Files.writeString(root.resolve("runtime/nginx/nginx.exe"),"synthetic");
        Files.writeString(root.resolve("state/nginx.pid"),"1234\n");
        return root;
    }
    @Test void disabledUnsupportedAndInvalidInputDoNotInspect() {
        var lookup=mock(NginxRuntimeObservation.ProcessLookup.class);
        var disabled=new NginxRuntimeObservation(false,true,null,new ObjectMapper(),lookup);
        assertThat(disabled.runtime().state()).isEqualTo("DISABLED");
        assertThat(disabled.logs("access",50).state()).isEqualTo("DISABLED");
        var linux=new NginxRuntimeObservation(true,false,null,new ObjectMapper(),lookup);
        assertThat(linux.runtime().state()).isEqualTo("UNSUPPORTED_PLATFORM");
        assertThatThrownBy(()->linux.logs("../state/nginx.pid",50)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(()->linux.logs("access",101)).isInstanceOf(BusinessException.class);
        verifyNoInteractions(lookup);
    }
    @Test void pidUsesOnlyFixedExecutableAndRejectsInvalidFile() throws Exception {
        Path root=fixture();
        var lookup=mock(NginxRuntimeObservation.ProcessLookup.class);
        when(lookup.state(1234,root.resolve("runtime/nginx/nginx.exe"))).thenReturn("PID_MISMATCH");
        var service=new NginxRuntimeObservation(true,true,root,new ObjectMapper(),lookup);
        assertThat(service.runtime().state()).isEqualTo("PID_MISMATCH");
        verify(lookup).state(1234,root.resolve("runtime/nginx/nginx.exe"));
        Files.writeString(root.resolve("state/nginx.pid"),"1234\n5678");
        assertThat(service.runtime().state()).isEqualTo("PID_UNVERIFIABLE");
        Files.writeString(root.resolve("state/nginx.pid"),"1".repeat(65));
        assertThat(service.runtime().state()).isEqualTo("PID_UNVERIFIABLE");
        verifyNoMoreInteractions(lookup);
    }
    @Test void logProjectionNeverReturnsRawTextAndDropsPartialLines() throws Exception {
        Path root=fixture();
        String valid="{\"time\":\"2026-11-25T10:00:00+08:00\",\"method\":\"GET\",\"status\":502,\"request_time\":0.25,\"remote_addr\":\"synthetic-secret\",\"uri\":\"/private/synthetic-secret\",\"user_agent\":\"synthetic-secret\"}";
        Files.writeString(root.resolve("logs/nginx/access-audit.jsonl"),"invalid\n"+valid+"\n"+valid.replace("GET","synthetic-secret")+"\n"+valid);
        Files.writeString(root.resolve("logs/nginx/error.log"),"2026/11/25 10:00:00 [error] synthetic-secret path and token\ninvalid\n");
        var mapper=new ObjectMapper().findAndRegisterModules();
        var service=new NginxRuntimeObservation(true,true,root,mapper,mock(NginxRuntimeObservation.ProcessLookup.class));
        var access=service.logs("access",100);
        assertThat(access.entries()).hasSize(2);
        assertThat(access.omitted()).isEqualTo(1);
        assertThat(access.entries().getFirst().method()).isEqualTo("OTHER");
        assertThat(access.entries().getLast().status()).isEqualTo(502);
        var error=service.logs("error",100);
        assertThat(error.entries()).hasSize(1);
        assertThat(error.entries().getFirst().severity()).isEqualTo("error");
        assertThat(mapper.writeValueAsString(access)+mapper.writeValueAsString(error)).doesNotContain("synthetic-secret","remote_addr","user_agent");
    }
    @Test void tailIsBoundedAndRedirectedFileRefused() throws Exception {
        Path root=fixture();
        Path log=root.resolve("logs/nginx/error.log");
        Files.writeString(log,("2026/11/25 10:00:00 [warn] "+"x".repeat(1000)+"\n").repeat(200));
        var service=new NginxRuntimeObservation(true,true,root,new ObjectMapper(),mock(NginxRuntimeObservation.ProcessLookup.class));
        var result=service.logs("error",10);
        assertThat(result.entries()).hasSize(10);
        assertThat(result.truncated()).isTrue();
        Files.delete(log);
        Path external=Files.writeString(root.resolve("not-log.txt"),"secret");
        try {Files.createSymbolicLink(log,external);} catch(UnsupportedOperationException|java.io.IOException|SecurityException unavailable) {return;}
        assertThat(service.logs("error",10).state()).isEqualTo("LOG_UNAVAILABLE");
    }
}
