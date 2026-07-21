package com.zjcxph.imgapi.utils;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HttpByteRangeTest {

    @Test
    void parsesExplicitAndSuffixRanges() {
        HttpByteRange explicit = HttpByteRange.parse("bytes=10-19", 100);
        assertThat(explicit.start()).isEqualTo(10);
        assertThat(explicit.end()).isEqualTo(19);
        assertThat(explicit.length()).isEqualTo(10);

        HttpByteRange suffix = HttpByteRange.parse("bytes=-20", 100);
        assertThat(suffix.start()).isEqualTo(80);
        assertThat(suffix.end()).isEqualTo(99);
    }

    @Test
    void rejectsMultipleOrUnsatisfiedRanges() {
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=0-1,3-4", 100))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> HttpByteRange.parse("bytes=100-120", 100))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
