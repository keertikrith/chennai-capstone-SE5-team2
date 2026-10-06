package com.capstone.etp.portfolio;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
class PortfolioConfig {

    /** One clock for the module so staleness and cache windows can be tested with a fixed time. */
    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock portfolioClock() {
        return Clock.systemUTC();
    }
}
