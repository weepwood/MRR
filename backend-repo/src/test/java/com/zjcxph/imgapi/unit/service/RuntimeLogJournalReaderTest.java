package com.zjcxph.imgapi.unit.service;

import com.zjcxph.imgapi.service.RuntimeLogJournalReader;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.assertj.core.api.Assertions.assertThat;

class RuntimeLogJournalReaderTest {
    @TempDir Path directory;
    @Test void incompleteUtf8LineIsNotAcknowledgedAndResumesAfterAppend() throws Exception {
        Path file = directory.resolve("runtime.log");
        Files.writeString(file, "第一行\npartial");
        var first = RuntimeLogJournalReader.read(file, 0, 100);
        assertThat(first.lines()).hasSize(1);
        assertThat(first.complete()).isFalse();
        Files.writeString(file, "-complete\n", StandardOpenOption.APPEND);
        var second = RuntimeLogJournalReader.read(file, first.nextOffset(), 100);
        assertThat(second.lines()).extracting(RuntimeLogJournalReader.Line::text).containsExactly("partial-complete");
    }
    @Test void readsAtMostTheRequestedBatch() throws Exception {
        Path file = directory.resolve("runtime.log");
        Files.writeString(file, "a\nb\nc\n");
        assertThat(RuntimeLogJournalReader.read(file, 0, 2).lines()).hasSize(2);
    }
}
