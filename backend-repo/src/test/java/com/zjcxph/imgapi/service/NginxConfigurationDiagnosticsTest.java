package com.zjcxph.imgapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class NginxConfigurationDiagnosticsTest {
    @TempDir Path directory;

    @Test void disabledAndNonWindowsNeverLaunch() throws Exception {
        var launcher = mock(NginxConfigurationDiagnostics.Launcher.class);
        assertThat(new NginxConfigurationDiagnostics(false,true,null,launcher).check().status()).isEqualTo("DISABLED");
        assertThat(new NginxConfigurationDiagnostics(true,false,null,launcher).check().status()).isEqualTo("UNSUPPORTED_PLATFORM");
        assertThat(new NginxConfigurationDiagnostics(true,true,null,launcher).check().status()).isEqualTo("NOT_CONFIGURED");
        verifyNoInteractions(launcher);
    }

    @Test void onlyFixedCommandAndRedactedMetadata() throws Exception {
        var root = fixture();
        var launcher = mock(NginxConfigurationDiagnostics.Launcher.class);
        var process = mock(Process.class);
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor(8,TimeUnit.SECONDS)).thenReturn(true);
        when(process.exitValue()).thenReturn(1);
        var prefix = root.resolve("runtime/nginx");
        var args = List.of(prefix.resolve("nginx.exe").toString(),"-p",prefix.toString(),"-c",
                root.resolve("config/nginx/nginx.conf").toString(),"-t");
        when(launcher.start(args,prefix)).thenReturn(process);
        var service = new NginxConfigurationDiagnostics(true,true,root,launcher);
        assertThat(service.status().state()).isEqualTo("AVAILABLE");
        assertThat(service.status().configBytes()).isEqualTo(4);
        verifyNoInteractions(launcher);
        assertThat(service.check().status()).isEqualTo("FAILED");
        verify(launcher).start(args,prefix);
        when(process.exitValue()).thenReturn(0);
        assertThat(service.check().status()).isEqualTo("PASSED");
    }

    @Test void timeoutRetainsPermitUntilProcessExit() throws Exception {
        var root = fixture();
        var launcher = mock(NginxConfigurationDiagnostics.Launcher.class);
        var process = mock(Process.class);
        var exited = new CompletableFuture<Process>();
        when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(process.waitFor(8,TimeUnit.SECONDS)).thenReturn(false);
        when(process.isAlive()).thenReturn(true);
        when(process.onExit()).thenReturn(exited);
        when(launcher.start(anyList(),any())).thenReturn(process);
        var service = new NginxConfigurationDiagnostics(true,true,root,launcher);
        assertThat(service.check().status()).isEqualTo("TIMEOUT");
        assertThat(service.check().status()).isEqualTo("BUSY");
        verify(launcher,times(1)).start(anyList(),any());
        verify(process,atLeastOnce()).destroyForcibly();
        exited.complete(process);
        when(process.isAlive()).thenReturn(false);
        when(process.waitFor(8,TimeUnit.SECONDS)).thenReturn(true);
        assertThat(service.check().status()).isEqualTo("PASSED");
    }

    @Test void missingFileNeverLaunches() throws Exception {
        var root = fixture();
        var launcher = mock(NginxConfigurationDiagnostics.Launcher.class);
        Files.delete(root.resolve("runtime/nginx/nginx.exe"));
        var service = new NginxConfigurationDiagnostics(true,true,root,launcher);
        assertThat(service.status().state()).isEqualTo("INSTALLATION_UNAVAILABLE");
        assertThat(service.check().status()).isEqualTo("UNAVAILABLE");
        verifyNoInteractions(launcher);
    }

    @Test void versionOnlyRunsFixedFlagAndReturnsAllowlistedVersion() throws Exception {
        var root=fixture();
        var check=mock(NginxConfigurationDiagnostics.Launcher.class);
        var launch=mock(NginxConfigurationDiagnostics.Launcher.class);
        var proc=mock(Process.class);
        when(proc.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(proc.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("nginx version: nginx/1.28.0\r\n".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
        when(proc.waitFor(5,TimeUnit.SECONDS)).thenReturn(true);
        var prefix=root.resolve("runtime/nginx");
        when(launch.start(List.of(prefix.resolve("nginx.exe").toString(),"-v"),prefix)).thenReturn(proc);
        var service=new NginxConfigurationDiagnostics(true,true,root,check,launch);
        assertThat(service.version().version()).isEqualTo("1.28.0");
        verify(launch).start(List.of(prefix.resolve("nginx.exe").toString(),"-v"),prefix);
        verifyNoInteractions(check);
        when(proc.getInputStream()).thenReturn(new java.io.ByteArrayInputStream("nginx version: nginx/1.28.0 secret".getBytes()));
        var invalid=service.version();
        assertThat(invalid.state()).isEqualTo("OUTPUT_UNRECOGNIZED");
        assertThat(invalid.version()).isNull();
        when(proc.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[1025]));
        assertThat(service.version().state()).isEqualTo("OUTPUT_UNRECOGNIZED");
        assertThat(new NginxConfigurationDiagnostics(false,true,root,check,launch).version().state()).isEqualTo("DISABLED");
        verify(launch,times(3)).start(anyList(),any());
    }

    @Test void versionAndConfigCheckSharePermitUntilExit() throws Exception {
        var root=fixture();
        var launch=mock(NginxConfigurationDiagnostics.Launcher.class);
        var check=mock(NginxConfigurationDiagnostics.Launcher.class);
        var proc=mock(Process.class);
        var exited=new CompletableFuture<Process>();
        when(proc.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(proc.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        when(proc.waitFor(5,TimeUnit.SECONDS)).thenReturn(false);
        when(proc.isAlive()).thenReturn(true);
        when(proc.onExit()).thenReturn(exited);
        when(launch.start(anyList(),any())).thenReturn(proc);
        var service=new NginxConfigurationDiagnostics(true,true,root,check,launch);
        assertThat(service.version().state()).isEqualTo("TIMEOUT");
        assertThat(service.check().status()).isEqualTo("BUSY");
        verifyNoInteractions(check);
        verify(proc).destroyForcibly();
        exited.complete(proc);
        assertThat(service.version().state()).isEqualTo("TIMEOUT");
    }

    @Test void listenerHintsBoundSizeAndNeverExposeAddressesOrIncludes() throws Exception {
        var root=fixture();
        var launch=mock(NginxConfigurationDiagnostics.Launcher.class);
        var service=new NginxConfigurationDiagnostics(true,true,root,launch,launch);
        var file=root.resolve("config/nginx/nginx.conf");
        Files.writeString(file,"# listen 12;\n  listen 80 default_server;\nlisten 443; # comment\nlisten 443;\nlisten 127.0.0.1:1234;\nlisten 65536;\ninclude private-path;\nlisten $port;\n");
        var hints=service.listenerHints();
        assertThat(hints.state()).isEqualTo("TEXT_HINTS");
        assertThat(hints.ports()).containsExactly(80,443);
        Files.writeString(file,"x".repeat(65537));
        assertThat(service.listenerHints().state()).isEqualTo("CONFIG_TOO_LARGE");
        verifyNoInteractions(launch);
    }

    private Path fixture() throws Exception {
        var root = directory.toRealPath();
        Files.createDirectories(root.resolve("runtime/nginx"));
        Files.createDirectories(root.resolve("config/nginx"));
        Files.writeString(root.resolve("runtime/nginx/nginx.exe"),"synthetic");
        Files.writeString(root.resolve("config/nginx/nginx.conf"),"test");
        return root;
    }
}
