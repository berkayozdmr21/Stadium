package com.perde.infrastructure.config;

import com.perde.domain.model.HoldPolicy;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(AppProperties.class)
class AppConfig {

    /** Zaman tek bir yerden gelir; testler bunu değiştirerek sürenin dolmasını beklemeden sınar. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    HoldPolicy holdPolicy(AppProperties props) {
        return new HoldPolicy(Duration.ofSeconds(props.holdSeconds()), props.maxSeats());
    }
}
