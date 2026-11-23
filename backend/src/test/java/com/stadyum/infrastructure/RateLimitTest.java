package com.stadyum.infrastructure;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import com.stadyum.domain.model.Event;
import com.stadyum.support.IntegrationTest;
import jakarta.servlet.Filter;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest(properties = "stadyum.rate-limit.requests-per-minute=5")
class RateLimitTest extends IntegrationTest {

    @Autowired
    WebApplicationContext context;
    @Autowired
    @Qualifier("rateLimitFilter")
    Filter rateLimitFilter;

    @Test
    void writesAreLimitedPerClientAndReadsAreNot() throws Exception {
        Event event = newEvent("sinir", 1, 3);
        long seat = seatIds(event).get(0);
        MockMvc mvc = MockMvcBuilders.webAppContextSetup(context).addFilters(rateLimitFilter).build();

        for (int i = 0; i < 5; i++) {
            assertThat(status(mvc, "10.0.0.1", seat)).as("istek " + (i + 1)).isNotEqualTo(429);
        }
        assertThat(status(mvc, "10.0.0.1", seat)).as("sınır aşıldı").isEqualTo(429);
        assertThat(status(mvc, "10.0.0.2", seat)).as("başka istemci etkilenmez").isNotEqualTo(429);
        assertThat(mvc.perform(get("/api/events").with(r -> {
            r.setRemoteAddr("10.0.0.1");
            return r;
        })).andReturn().getResponse().getStatus()).as("okuma sınırlanmaz").isEqualTo(200);

        clock.advance(Duration.ofMinutes(1));
        assertThat(status(mvc, "10.0.0.1", seat)).as("yeni dakikada sayaç sıfırlanır").isNotEqualTo(429);
    }

    private static int status(MockMvc mvc, String ip, long seat) throws Exception {
        return mvc.perform(put("/api/events/sinir/hold/seats/{id}", seat).with(r -> {
            r.setRemoteAddr(ip);
            return r;
        })).andReturn().getResponse().getStatus();
    }
}
