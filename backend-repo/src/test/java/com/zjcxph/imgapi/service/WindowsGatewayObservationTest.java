package com.zjcxph.imgapi.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WindowsGatewayObservationTest {
    @TempDir Path directory;
    private Path fixture() throws IOException {
        Path root=directory.toRealPath();
        Path executable=root.resolve("System32/WindowsPowerShell/v1.0/powershell.exe");
        Files.createDirectories(executable.getParent());
        Files.writeString(executable,"synthetic");
        return root;
    }

    private Process process(String output) throws InterruptedException {
        var p=mock(Process.class);
        when(p.getOutputStream()).thenReturn(new ByteArrayOutputStream());
        when(p.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.US_ASCII)));
        when(p.waitFor(8,TimeUnit.SECONDS)).thenReturn(true);
        return p;
    }

    @Test void fixedLocalQueriesOnlyReturnServiceAndOwnedPorts() throws Exception {
        var root=fixture();
        var launch=mock(WindowsGatewayObservation.Launcher.class);
        var ids=List.of(new NginxRuntimeObservation.ProcessIdentity(1234,Instant.EPOCH),new NginxRuntimeObservation.ProcessIdentity(1235,Instant.EPOCH));
        var proc=process("SERVICE=RUNNING\r\nTCP=OBSERVED:80,443\r\n");
        when(launch.start(anyList(),eq(root))).thenReturn(proc);
        var service=new WindowsGatewayObservation(true,true,root,root,()->ids,launch);
        var result=service.observe();
        assertThat(result.serviceState()).isEqualTo("RUNNING");
        assertThat(result.listenerState()).isEqualTo("OBSERVED");
        assertThat(result.ports()).containsExactly(80,443);
        @SuppressWarnings("unchecked") var args=org.mockito.ArgumentCaptor.forClass(List.class);
        verify(launch).start(args.capture(),eq(root));
        List<?> command=args.getValue();
        assertThat(command.subList(0,5)).isEqualTo(List.of(root.resolve("System32/WindowsPowerShell/v1.0/powershell.exe").toString(),"-NoLogo","-NoProfile","-NonInteractive","-EncodedCommand"));
        var script=new String(Base64.getDecoder().decode(command.get(5).toString()),StandardCharsets.UTF_16LE);
        assertThat(script).contains("Name='MRR-Gateway'","State=2 AND (OwningProcess=1234 OR OwningProcess=1235)").doesNotContain("Stop-Service","Start-Service","Restart-Service","-ComputerName");
    }

    @Test void noVerifiedOwnerOrIdentityChangeNeverClaimsOwnedPorts() throws Exception {
        var root=fixture();
        var launch=mock(WindowsGatewayObservation.Launcher.class);
        var unverified=process("SERVICE=RUNNING\nTCP=UNVERIFIED\n");
        var observed=process("SERVICE=RUNNING\nTCP=OBSERVED:80\n");
        when(launch.start(anyList(),any())).thenReturn(unverified,observed);
        var noOwner=new WindowsGatewayObservation(true,true,root,root,List::of,launch);
        assertThat(noOwner.observe().listenerState()).isEqualTo("UNVERIFIED");
        assertThat(WindowsGatewayObservation.script(List.of())).doesNotContain("MSFT_NetTCPConnection");
        @SuppressWarnings("unchecked") Supplier<List<NginxRuntimeObservation.ProcessIdentity>> owners=mock(Supplier.class);
        when(owners.get()).thenReturn(List.of(new NginxRuntimeObservation.ProcessIdentity(1,Instant.EPOCH)),List.of(new NginxRuntimeObservation.ProcessIdentity(1,Instant.EPOCH.plusSeconds(1))));
        var changed=new WindowsGatewayObservation(true,true,root,root,owners,launch).observe();
        assertThat(changed.serviceState()).isEqualTo("RUNNING");
        assertThat(changed.listenerState()).isEqualTo("IDENTITY_CHANGED");
        assertThat(changed.ports()).isEmpty();
    }

    @Test void rejectsUnknownRawOutputAndKeepsEmptyDistinctFromUnavailable() {
        assertThat(WindowsGatewayObservation.parse("SERVICE=STOPPED\nTCP=OBSERVED:\n").listenerState()).isEqualTo("OBSERVED");
        assertThat(WindowsGatewayObservation.parse("SERVICE=NOT_FOUND\nTCP=UNAVAILABLE").serviceState()).isEqualTo("NOT_FOUND");
        assertThat(WindowsGatewayObservation.parse("SERVICE=RUNNING\nTCP=TRUNCATED").listenerState()).isEqualTo("TRUNCATED");
        for(String invalid:List.of("SERVICE=secret\nTCP=OBSERVED:80","SERVICE=RUNNING\nTCP=OBSERVED:0","SERVICE=RUNNING\nTCP=OBSERVED:80,80","SERVICE=RUNNING\nTCP=OBSERVED:65536","SERVICE=RUNNING\nTCP=OBSERVED:80\nsecret")) {
            var result=WindowsGatewayObservation.parse(invalid);
            assertThat(result.state()).isEqualTo("UNAVAILABLE");
            assertThat(result.ports()).isEmpty();
        }
    }

    @Test void disabledDoesNotLaunchAndTimeoutRetainsPermit() throws Exception {
        var root=fixture();
        var launch=mock(WindowsGatewayObservation.Launcher.class);
        assertThat(new WindowsGatewayObservation(false,true,root,root,List::of,launch).observe().state()).isEqualTo("DISABLED");
        assertThat(new WindowsGatewayObservation(true,false,root,root,List::of,launch).observe().state()).isEqualTo("UNSUPPORTED_PLATFORM");
        verifyNoInteractions(launch);
        var proc=process("");
        var exit=new CompletableFuture<Process>();
        when(proc.waitFor(8,TimeUnit.SECONDS)).thenReturn(false);
        when(proc.isAlive()).thenReturn(true);
        when(proc.onExit()).thenReturn(exit);
        when(launch.start(anyList(),any())).thenReturn(proc);
        var service=new WindowsGatewayObservation(true,true,root,root,List::of,launch);
        assertThat(service.observe().state()).isEqualTo("TIMEOUT");
        assertThat(service.observe().state()).isEqualTo("BUSY");
        verify(proc).destroyForcibly();
        exit.complete(proc);
        when(proc.isAlive()).thenReturn(false);
        assertThat(service.observe().state()).isEqualTo("TIMEOUT");
    }
}
