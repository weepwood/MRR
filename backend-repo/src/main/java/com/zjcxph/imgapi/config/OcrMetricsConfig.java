package com.zjcxph.imgapi.config;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import java.time.Duration;

@Configuration
public class OcrMetricsConfig {
    @Bean MeterFilter ocrImageLatencyPercentiles() {
        return new MeterFilter() {
            @Override public DistributionStatisticConfig configure(Meter.Id id,DistributionStatisticConfig config) {
                String uri=id.getTag("uri");
                if("http.server.requests".equals(id.getName())&&uri!=null&&uri.startsWith("/api/v1/img/"))
                    return DistributionStatisticConfig.builder().percentiles(.95).expiry(Duration.ofMinutes(5)).build().merge(config);
                return config;
            }
        };
    }
}
