package com.zjcxph.imgapi.ocr;

import com.zjcxph.imgapi.config.OcrRuntimeProperties;
import com.zjcxph.imgapi.service.ImageContentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.io.*;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class OcrInputTest {
    @TempDir Path root;
    @Test void blockingSourceCannotOccupyOcrWorkerPastReadDeadline() {
        var properties=new OcrRuntimeProperties();properties.setTempDirectory(root.toString());properties.setReadTimeoutMillis(100);
        var images=mock(ImageContentService.class);
        InputStream blocked=new InputStream() {
            @Override public int read() {return -1;}
            @Override public int read(byte[] b,int off,int len) throws IOException {
                try{Thread.sleep(30000);}catch(InterruptedException e){throw new IOException("synthetic interrupted read");}return -1;
            }
        };
        when(images.open(1)).thenReturn(new ImageContentService.ImageContent(blocked,"synthetic.jpg",org.springframework.http.MediaType.IMAGE_JPEG,null));
        var input=new OcrInput(images,properties);
        try {org.junit.jupiter.api.Assertions.assertTimeoutPreemptively(Duration.ofSeconds(2),()->
            assertThatThrownBy(()->input.prepare(1,()->false)).isInstanceOfSatisfying(OcrFailure.class,e->assertThat(e.code()).isEqualTo("TIMEOUT")));}
        finally {input.close();}
    }
    @Test void boundedInputAndCancellationRemoveManagedTemporaryFiles() throws IOException {
        var properties=new OcrRuntimeProperties();properties.setTempDirectory(root.toString());properties.setMaxInputBytes(100);
        var images=mock(ImageContentService.class);
        when(images.open(1)).thenAnswer(i->new ImageContentService.ImageContent(new ByteArrayInputStream(new byte[1000]),"synthetic.jpg",org.springframework.http.MediaType.IMAGE_JPEG,null));
        var input=new OcrInput(images,properties);
        try {
            assertThatThrownBy(()->input.prepare(1,()->false)).isInstanceOfSatisfying(OcrFailure.class,e->assertThat(e.code()).isEqualTo("INPUT_LIMIT"));
            assertThatThrownBy(()->input.prepare(1,()->true)).isInstanceOfSatisfying(OcrFailure.class,e->assertThat(e.code()).isEqualTo("CANCELLED"));
            try(var files=Files.list(root)){assertThat(files.count()).isZero();}
        } finally {input.close();}
    }
}
