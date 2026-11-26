package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.audit.MedicalAuditAdmissionInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;

@Configuration
public class MedicalAuditWebConfig implements WebMvcConfigurer {
    private final MedicalAuditAdmissionInterceptor gate;
    public MedicalAuditWebConfig(MedicalAuditAdmissionInterceptor gate){this.gate=gate;}
    @Override public void addInterceptors(InterceptorRegistry registry){
        registry.addInterceptor(gate).addPathPatterns("/api/v1/**").order(100);
    }
}
