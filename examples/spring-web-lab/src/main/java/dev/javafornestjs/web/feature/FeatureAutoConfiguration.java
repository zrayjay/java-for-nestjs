package dev.javafornestjs.web.feature;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class FeatureAutoConfiguration {
    @Bean
    @ConditionalOnProperty(name = "lab.feature.enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(FeatureGreeting.class)
    FeatureGreeting greeting() { return new FeatureGreeting("auto"); }
}
