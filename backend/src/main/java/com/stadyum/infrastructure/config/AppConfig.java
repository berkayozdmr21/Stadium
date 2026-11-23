package com.stadyum.infrastructure.config;

import com.stadyum.domain.model.HoldPolicy;
import com.stadyum.domain.stadium.StadiumLayout;
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

    /** Stadyumun koltuk düzeni. Testler bunu küçük bir düzenle değiştirir. */
    @Bean
    StadiumLayout stadiumLayout() {
        return StadiumLayout.standard();
    }

    @Bean
    HoldPolicy holdPolicy(AppProperties props) {
        return new HoldPolicy(Duration.ofSeconds(props.holdSeconds()), props.maxSeats());
    }
}
