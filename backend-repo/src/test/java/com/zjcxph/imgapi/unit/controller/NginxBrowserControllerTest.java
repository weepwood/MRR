package com.zjcxph.imgapi.unit.controller;

import com.zjcxph.imgapi.controller.NginxBrowserController;
import com.zjcxph.imgapi.service.NginxBrowserService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.context.request.async.WebAsyncUtils;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class NginxBrowserControllerTest {
    private final NginxBrowserService service = mock(NginxBrowserService.class);
    private final NginxBrowserController controller = new NginxBrowserController(service);
    private final AtomicInteger closes = new AtomicInteger();

    private void remote(String name) {
        var stream = new ByteArrayInputStream(new byte[]{1,2,3}) {
            @Override public void close() { closes.incrementAndGet(); }
        };
        when(service.openFile("default","synthetic.jpg")).thenReturn(
                new NginxBrowserService.RemoteFile(stream,"image/jpeg",3,name,null,null));
    }

    @Test void rejectedAsyncTaskClosesUnclaimedUpstreamExactlyOnce() throws Exception {
        remote("synthetic.jpg");
        var request = new MockHttpServletRequest("GET","/file");
        request.setAsyncSupported(true);
        var response = controller.openFile("default","synthetic.jpg",request);
        var manager = WebAsyncUtils.getAsyncManager(request);
        manager.setAsyncWebRequest(WebAsyncUtils.createAsyncWebRequest(request,new MockHttpServletResponse()));
        manager.setTaskExecutor(new org.springframework.core.task.AsyncTaskExecutor() {
            @Override public void execute(Runnable task) { throw new org.springframework.core.task.TaskRejectedException("synthetic rejection"); }
        });
        manager.startCallableProcessing(() -> { response.getBody().writeTo(new ByteArrayOutputStream()); return null; });
        assertThat(manager.getConcurrentResult()).isInstanceOf(org.springframework.core.task.TaskRejectedException.class);
        request.getAsyncContext().complete();
        assertThat(closes.get()).isEqualTo(1);
    }

    @Test void failedResponsePreparationClosesAlreadyOpenedUpstream() throws Exception {
        var remote = mock(NginxBrowserService.RemoteFile.class);
        when(remote.inputStream()).thenReturn(new ByteArrayInputStream(new byte[]{1}) {
            @Override public void close() { closes.incrementAndGet(); }
        });
        when(remote.fileName()).thenThrow(new IllegalStateException("synthetic response preparation failure"));
        when(service.openFile("default","synthetic.jpg")).thenReturn(remote);
        var response=controller.openFile("default","synthetic.jpg",new MockHttpServletRequest());
        assertThat(response.getStatusCode().value()).isEqualTo(503);
        assertThat(closes.get()).isEqualTo(1);
    }

    @Test void successfulBodyAndClientWriteFailureCloseUpstream() throws Exception {
        remote("synthetic.jpg");
        var response=controller.openFile("default","synthetic.jpg",new MockHttpServletRequest());
        var output=new ByteArrayOutputStream();response.getBody().writeTo(output);
        assertThat(output.toByteArray()).containsExactly((byte)1,(byte)2,(byte)3);
        assertThat(closes.get()).isEqualTo(1);
        remote("synthetic.jpg");
        var failed=controller.openFile("default","synthetic.jpg",new MockHttpServletRequest());
        assertThatThrownBy(()->failed.getBody().writeTo(new java.io.OutputStream(){
            @Override public void write(int b) throws IOException { throw new IOException("synthetic disconnect"); }
        })).isInstanceOf(IOException.class);
        assertThat(closes.get()).isEqualTo(2);
    }
}
