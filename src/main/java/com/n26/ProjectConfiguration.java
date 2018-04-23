package com.n26;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

@PropertySource("classpath:config.properties")
@Configuration("ProjectConfiguration")
public class ProjectConfiguration {

    @Value("${refresh-interval}")
    private int refreshInterval;

    @Bean("rollingStatistics")
    public N26RollingStatistics rollingStatistics() {
        return new N26RollingStatistics(60 * 1000,60);
    }

    @Bean
    @Qualifier("refreshInterval")
    public int getRefreshInterval() {
        return refreshInterval;
    }
}
