package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.interceptors.AuthorizationInterceptor;
import com.zjcxph.imgapi.interceptors.DocumentationSessionCleanupInterceptor;
import com.zjcxph.imgapi.interceptors.LogInterceptor;
import com.zjcxph.imgapi.interceptors.LoginInterceptor;
import com.zjcxph.imgapi.interceptors.PasswordChangeRequiredInterceptor;
import com.zjcxph.imgapi.interceptors.RateLimitInterceptor;
import com.zjcxph.imgapi.interceptors.ReadOnlyDegradationInterceptor;
import com.zjcxph.imgapi.security.ApiAccessPolicy;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final LoginInterceptor loginInterceptor;
    private final PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final DocumentationSessionCleanupInterceptor documentationSessionCleanupInterceptor;
    private final LogInterceptor logInterceptor;
    private final RateLimitInterceptor rateLimitInterceptor;
    private final ReadOnlyDegradationInterceptor readOnlyDegradationInterceptor;

    public WebConfig(LoginInterceptor loginInterceptor,
                     PasswordChangeRequiredInterceptor passwordChangeRequiredInterceptor,
                     AuthorizationInterceptor authorizationInterceptor,
                     DocumentationSessionCleanupInterceptor documentationSessionCleanupInterceptor,
                     LogInterceptor logInterceptor,
                     RateLimitInterceptor rateLimitInterceptor,
                     ReadOnlyDegradationInterceptor readOnlyDegradationInterceptor) {
        this.loginInterceptor = loginInterceptor;
        this.passwordChangeRequiredInterceptor = passwordChangeRequiredInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
        this.documentationSessionCleanupInterceptor = documentationSessionCleanupInterceptor;
        this.logInterceptor = logInterceptor;
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.readOnlyDegradationInterceptor = readOnlyDegradationInterceptor;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .resourceChain(true)
                .addResolver(new SpaPathResourceResolver());
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        String[] staticExcludes = {
                "/assets/**", "/browser_upgrade/**", "/favicon.*", "/*.html", "/*.br", "/*.gz"
        };
        String[] baseExcludes = ApiAccessPolicy.generalExcludes();
        String[] authenticationExcludes = ApiAccessPolicy.authenticationExcludes();

        registry.addInterceptor(documentationSessionCleanupInterceptor)
                .addPathPatterns("/api/v1/auth/logout");

        registry.addInterceptor(logInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes);

        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(staticExcludes);

        registry.addInterceptor(loginInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(authenticationExcludes);

        registry.addInterceptor(passwordChangeRequiredInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(authenticationExcludes);

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes)
                .excludePathPatterns(authenticationExcludes);

        registry.addInterceptor(readOnlyDegradationInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns(baseExcludes);
    }
}
