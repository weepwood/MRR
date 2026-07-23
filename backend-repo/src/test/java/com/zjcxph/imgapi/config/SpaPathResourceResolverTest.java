package com.zjcxph.imgapi.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SpaPathResourceResolverTest {

    @TempDir
    Path tempDir;

    @Test
    void resolvesRealAssetBeforeSpaFallback() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<html>MRR</html>");
        Files.createDirectories(tempDir.resolve("assets"));
        Files.writeString(tempDir.resolve("assets/app.js"), "console.log('mrr')");

        SpaPathResourceResolver resolver = new SpaPathResourceResolver();
        Resource location = new FileSystemResource(tempDir + File.separator);

        Resource asset = resolver.getResource("assets/app.js", location);
        Resource route = resolver.getResource("users/42", location);
        Resource root = resolver.getResource("", location);

        assertThat(asset).isNotNull();
        assertThat(asset.getFilename()).isEqualTo("app.js");
        assertThat(route).isNotNull();
        assertThat(route.getFilename()).isEqualTo("index.html");
        assertThat(root).isNotNull();
        assertThat(root.getFilename()).isEqualTo("index.html");
    }

    @Test
    void doesNotTurnBackendOrMissingFilePathsIntoHtml() throws Exception {
        Files.writeString(tempDir.resolve("index.html"), "<html>MRR</html>");
        SpaPathResourceResolver resolver = new SpaPathResourceResolver();
        Resource location = new FileSystemResource(tempDir + File.separator);

        assertThat(resolver.getResource("api", location)).isNull();
        assertThat(resolver.getResource("actuator", location)).isNull();
        assertThat(resolver.getResource("swagger-ui", location)).isNull();
        assertThat(resolver.getResource("v3/api-docs", location)).isNull();
        assertThat(resolver.getResource("api-docs", location)).isNull();
        assertThat(resolver.getResource("docs", location)).isNull();
        assertThat(resolver.getResource("webjars", location)).isNull();
        assertThat(resolver.getResource("error", location)).isNull();
        assertThat(resolver.getResource("api/v1/missing", location)).isNull();
        assertThat(resolver.getResource("actuator/missing", location)).isNull();
        assertThat(resolver.getResource("swagger-ui/missing", location)).isNull();
        assertThat(resolver.getResource("v3/api-docs/missing", location)).isNull();
        assertThat(resolver.getResource("docs/internal/missing", location)).isNull();
        assertThat(resolver.getResource("assets/missing.js", location)).isNull();
        assertThat(resolver.getResource("..\\secret", location)).isNull();
    }

    @Test
    void requiresBundledIndexForSpaFallback() throws Exception {
        SpaPathResourceResolver resolver = new SpaPathResourceResolver();
        Resource location = new FileSystemResource(tempDir + File.separator);

        assertThat(resolver.getResource("settings", location)).isNull();
    }
}
