package com.zjcxph.imgapi.config;

import com.zjcxph.imgapi.utils.AuditPayloadSanitizer;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods=false)
public class AuditPrivacyConfiguration {
    public AuditPrivacyConfiguration(Environment environment) {
        AuditPayloadSanitizer.configureKey(environment.getProperty("mrr.audit.hmac-key", environment.getProperty("MRR_AUDIT_HMAC_KEY")));
    }
}
