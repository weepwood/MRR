package com.zjcxph.imgapi.ocr;

import org.junit.jupiter.api.Test;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class OcrProtocolTest {
    @Test void capsOutputWithoutReadingUnboundedStream() {
        assertThrows(OcrFailure.class, () -> OcrProtocol.readLine(
                new ByteArrayInputStream(new byte[1025]), 1024));
    }
    @Test void rejectsTruncatedAndMismatchedResponses() {
        assertThrows(OcrFailure.class, () -> OcrProtocol.readLine(
                new ByteArrayInputStream("partial".getBytes(StandardCharsets.UTF_8)), 1024));
        assertThrows(OcrFailure.class, () -> OcrProtocol.parse(
                "{\"id\":\"other\",\"width\":10,\"height\":10,\"lines\":[]}", "expected"));
    }
    @Test void rejectsInvalidScoresAndBoundsAndPreservesOriginalText() {
        assertThrows(OcrFailure.class, () -> OcrProtocol.parse(
                "{\"id\":\"x\",\"width\":10,\"height\":10,\"lines\":[{\"text\":\"x\",\"confidence\":2,\"box\":[0,0,1,1]}]}", "x"));
        var result = OcrProtocol.parse("{\"id\":\"x\",\"width\":10,\"height\":10,\"lines\":[{\"text\":\"出院记录\",\"confidence\":0.99,\"box\":[0,0,9,2]}]}", "x");
        assertEquals("出院记录", result.text());
        assertEquals(0.99, result.confidence(), 0.00001);
    }
}
