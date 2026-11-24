package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.entity.PathDO;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ArchiveContentVerificationTest {
    private final PathDO image = new PathDO();
    private final ArchiveImageSource source = mock(ArchiveImageSource.class);
    private final ResolvedImageStorage storage = new ResolvedImageStorage(
            new ArchiveImageSourceResolver(List.of(source)), new SimpleMeterRegistry());

    private void available(InputStream stream) throws IOException {
        when(source.supports(image)).thenReturn(true);
        when(source.describeSource(image)).thenReturn("LOCAL");
        when(source.open(image)).thenReturn(stream);
    }

    @Test
    void verifiesActualBodyAndStoredChecksumWithoutMetadataProbe() throws Exception {
        available(new ByteArrayInputStream("abc".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        image.setFileSize(3L);
        var result = storage.verifyContent(image, "900150983cd24fb0d6963f7d28e17f72");
        assertThat(result.status()).isEqualTo("VERIFIED");
        assertThat(result.bytesRead()).isEqualTo(3);
        assertThat(result.sha256()).isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        verify(source, never()).size(image);
    }

    @Test
    void distinguishesUnknownChecksumAndSizeMismatch() throws Exception {
        available(new ByteArrayInputStream(new byte[]{1, 2}));
        assertThat(storage.verifyContent(image, null).status()).isEqualTo("READABLE_NO_BASELINE");
        available(new ByteArrayInputStream(new byte[]{1, 2}));
        image.setFileSize(1L);
        assertThat(storage.verifyContent(image, null).status()).isEqualTo("SIZE_MISMATCH");
    }

    @Test
    void bodyFailureNeverReportsVerifiedAndDoesNotExposePath() throws Exception {
        available(new InputStream() {
            @Override public int read() throws IOException { throw new IOException("synthetic-private-path"); }
        });
        var result = storage.verifyContent(image, null);
        assertThat(result.status()).isEqualTo("READ_FAILED");
        assertThat(result.toString()).doesNotContain("synthetic-private-path");
    }

    @Test
    void boundedReadClosesOversizedBodyWithoutClaimingFullVerification() throws Exception {
        var closed = new java.util.concurrent.atomic.AtomicBoolean();
        available(new ByteArrayInputStream(new byte[8 * 1024 * 1024 + 2]) {
            @Override public void close() { closed.set(true); }
        });
        var result = storage.verifyContent(image, null);
        assertThat(result.status()).isEqualTo("BUDGET_EXCEEDED");
        assertThat(result.bytesRead()).isEqualTo(8L * 1024 * 1024 + 1);
        assertThat(result.sha256()).isEmpty();
        assertThat(closed).isTrue();
    }

    @Test
    void missingOrUnreadableIsNotConfusedWithBodyCorruption() throws Exception {
        when(source.supports(image)).thenReturn(true);
        when(source.describeSource(image)).thenReturn("LOCAL");
        when(source.open(image)).thenThrow(new FileNotFoundException("synthetic-private-path"));
        var result = storage.verifyContent(image, null);
        assertThat(result.status()).isEqualTo("MISSING_OR_UNREADABLE");
        assertThat(result.toString()).doesNotContain("synthetic-private-path");
    }
}
