package com.zjcxph.imgapi.storage;

import com.zjcxph.imgapi.config.ArchiveImageSourceProperties;
import com.zjcxph.imgapi.config.ImageProperties;
import com.zjcxph.imgapi.entity.PathDO;
import com.zjcxph.imgapi.service.ImageUrlService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NginxArchiveImageSourceTest {

    private static final String IMAGE_URL =
            "http://127.0.0.1:8005/ba-img-01/24.04/24.04.07/666666-00789124/0013.jpg";

    @Mock
    private ImageUrlService imageUrlService;

    @Mock
    private HttpClient client;

    @Test
    @SuppressWarnings("unchecked")
    void readsLegacyImageFromConfiguredNginxServerWithBasicAuth() throws Exception {
        ImageProperties imageProperties = imageProperties();
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        PathDO image = image();
        when(imageUrlService.buildImageUrl(image)).thenReturn(IMAGE_URL);

        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn(stream("nginx-image"));
        when(client.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        NginxArchiveImageSource source = new NginxArchiveImageSource(
                imageUrlService, imageProperties, sourceProperties, client);

        assertThat(source.supports(image)).isTrue();
        try (var input = source.open(image)) {
            assertThat(input.readAllBytes()).isEqualTo("nginx-image".getBytes(StandardCharsets.UTF_8));
        }

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));
        HttpRequest request = requestCaptor.getValue();
        assertThat(request.uri().toString()).isEqualTo(IMAGE_URL);
        assertThat(request.method()).isEqualTo("GET");
        assertThat(request.headers().firstValue("Authorization")).contains(
                "Basic " + Base64.getEncoder().encodeToString(
                        "br_admin:secret".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @SuppressWarnings("unchecked")
    void fallsBackToBackendLocalFileOnlyAfterNginxReadFails() throws Exception {
        ImageProperties imageProperties = imageProperties();
        ArchiveImageSourceProperties sourceProperties = new ArchiveImageSourceProperties();
        PathDO image = image();
        when(imageUrlService.buildImageUrl(image)).thenReturn(IMAGE_URL);

        HttpResponse<java.io.InputStream> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(404);
        when(response.body()).thenReturn(stream("not-found"));
        when(client.send(
                any(HttpRequest.class),
                any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        NginxArchiveImageSource nginxSource = new NginxArchiveImageSource(
                imageUrlService, imageProperties, sourceProperties, client);
        LocalImageStorage localStorage = mock(LocalImageStorage.class);
        when(localStorage.open(image)).thenReturn(stream("local-image"));
        LocalArchiveImageSource localSource = new LocalArchiveImageSource(localStorage, sourceProperties);
        ArchiveImageSourceResolver resolver = new ArchiveImageSourceResolver(List.of(nginxSource, localSource));
        ResolvedImageStorage storage = new ResolvedImageStorage(resolver, new SimpleMeterRegistry());

        try (var input = storage.open(image)) {
            assertThat(input.readAllBytes()).isEqualTo("local-image".getBytes(StandardCharsets.UTF_8));
        }

        verify(localStorage).open(image);
    }

    private ImageProperties imageProperties() {
        ImageProperties properties = new ImageProperties();
        properties.setUrl("http://127.0.0.1:8005/ba-img");
        properties.setServerUrlDefault("http://127.0.0.1:8005/ba-img-00");
        properties.setServerUrlBa01("http://127.0.0.1:8005/ba-img-01");
        properties.setServerUrlBa02("http://127.0.0.1:8005/ba-img-02");
        properties.setServerUrlBa03("http://127.0.0.1:8005/ba-img-03");
        properties.setUsername("br_admin");
        properties.setPassword("secret");
        return properties;
    }

    private PathDO image() {
        return new PathDO(
                13,
                "24.04.07",
                "0013.jpg",
                "666666",
                "00789124",
                null,
                "AUTO",
                null,
                null,
                null,
                null);
    }

    private ByteArrayInputStream stream(String value) {
        return new ByteArrayInputStream(value.getBytes(StandardCharsets.UTF_8));
    }
}
