package com.perde.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.perde.domain.model.Event;
import com.perde.support.IntegrationTest;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
class ApiTest extends IntegrationTest {

    private static final String TOKEN = "X-Hold-Token";
    private static final String SLUG = "api";

    @Autowired
    WebApplicationContext context;

    MockMvc mvc;
    Event event;
    List<Long> seats;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.webAppContextSetup(context).build();
        event = newEvent(SLUG, 2, 4);
        seats = seatIds(event);
    }

    @Test
    void listsEventsWithAvailability() throws Exception {
        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].slug").value(SLUG))
                .andExpect(jsonPath("$[0].totalSeats").value(8))
                .andExpect(jsonPath("$[0].availableSeats").value(8))
                .andExpect(jsonPath("$[0].startsAt").isString());
    }

    @Test
    void metaTellsWhetherTheDemoCrowdIsOn() throws Exception {
        mvc.perform(get("/api/meta"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.demoCrowd").value(false));
    }

    @Test
    void unknownPathsAreProblemDetailsToo() throws Exception {
        mvc.perform(get("/api/yok"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void seatMapNeverExposesWhoHoldsASeat() throws Exception {
        String token = hold(seats.get(0), null);

        String body = mvc.perform(get("/api/events/{slug}", SLUG))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seats", hasSize(8)))
                .andExpect(jsonPath("$.seats[0].status").value("HELD"))
                .andExpect(jsonPath("$.seats[0].mine").value(false))
                .andExpect(jsonPath("$.hold.active").value(false))
                .andReturn().getResponse().getContentAsString();

        assertThat(body).doesNotContain(token).doesNotContain("holdId").doesNotContain("tokenHash");

        mvc.perform(get("/api/events/{slug}", SLUG).header(TOKEN, token))
                .andExpect(jsonPath("$.seats[0].mine").value(true))
                .andExpect(jsonPath("$.hold.active").value(true))
                .andExpect(jsonPath("$.hold.token").value(nullValue()));
    }

    @Test
    void fullPurchaseJourney() throws Exception {
        String token = hold(seats.get(0), null);
        mvc.perform(put("/api/events/{slug}/hold/seats/{id}", SLUG, seats.get(1)).header(TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value(nullValue()))
                .andExpect(jsonPath("$.seatIds", hasSize(2)))
                .andExpect(jsonPath("$.totalKurus").value(2 * PRICE));

        mvc.perform(delete("/api/events/{slug}/hold/seats/{id}", SLUG, seats.get(1)).header(TOKEN, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.seatIds", hasSize(1)));

        String ticket = mvc.perform(post("/api/events/{slug}/hold/confirm", SLUG).header(TOKEN, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ada Yılmaz\",\"email\":\"ada@example.com\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code", matchesPattern("PRD-[A-Z2-9]{8}")))
                .andExpect(jsonPath("$.seats", hasSize(1)))
                .andExpect(jsonPath("$.totalKurus").value(PRICE))
                .andReturn().getResponse().getContentAsString();
        String code = JsonPath.read(ticket, "$.code");

        mvc.perform(get("/api/bookings/{code}", code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.eventTitle").value("Test " + SLUG))
                .andExpect(jsonPath("$.customerName").value("Ada Yılmaz"))
                .andExpect(jsonPath("$.customerEmail").doesNotExist());
        assertThat(seatStatus(seats.get(0))).isEqualTo("SOLD");
        assertThat(seatStatus(seats.get(1))).isEqualTo("AVAILABLE");
    }

    @Test
    void errorsAreProblemDetailsWithAMachineReadableCode() throws Exception {
        hold(seats.get(0), null);

        mvc.perform(put("/api/events/{slug}/hold/seats/{id}", SLUG, seats.get(0)))
                .andExpect(status().isConflict())
                .andExpect(header().string("Content-Type", "application/problem+json"))
                .andExpect(jsonPath("$.code").value("SEAT_UNAVAILABLE"))
                .andExpect(jsonPath("$.detail").isString());

        mvc.perform(get("/api/events/{slug}", "olmayan"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
        mvc.perform(put("/api/events/{slug}/hold/seats/{id}", SLUG, 999_999))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("SEAT_NOT_FOUND"));
        mvc.perform(get("/api/bookings/{code}", "PRD-YOKYOKYO"))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("BOOKING_NOT_FOUND"));
        mvc.perform(put("/api/events/{slug}/hold/seats/{id}", SLUG, "abc"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        mvc.perform(delete("/api/events/{slug}/hold/seats/{id}", SLUG, seats.get(0)))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    void checkoutValidatesCustomerDetails() throws Exception {
        String token = hold(seats.get(0), null);

        mvc.perform(post("/api/events/{slug}/hold/confirm", SLUG).header(TOKEN, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"A\",\"email\":\"bu-eposta-degil\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION"))
                .andExpect(jsonPath("$.errors.name").isString())
                .andExpect(jsonPath("$.errors.email").isString());

        mvc.perform(post("/api/events/{slug}/hold/confirm", SLUG).header(TOKEN, token)
                        .contentType(MediaType.APPLICATION_JSON).content("{bozuk json"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("BAD_REQUEST"));

        assertThat(seatStatus(seats.get(0))).as("geçersiz istek satış yapmaz").isEqualTo("HELD");
    }

    @Test
    void checkoutWithoutOrAfterExpiredHoldIsGone() throws Exception {
        String json = "{\"name\":\"Ada Yılmaz\",\"email\":\"ada@example.com\"}";
        mvc.perform(post("/api/events/{slug}/hold/confirm", SLUG).contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));

        String token = hold(seats.get(0), null);
        clock.advance(Duration.ofSeconds(300));

        mvc.perform(post("/api/events/{slug}/hold/confirm", SLUG).header(TOKEN, token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isGone()).andExpect(jsonPath("$.code").value("HOLD_EXPIRED"));
    }

    @Test
    void seatLimitIsUnprocessable() throws Exception {
        Event wide = newEvent("genis", 1, 8);
        List<Long> ids = seatIds(wide);
        String token = null;
        for (int i = 0; i < 6; i++) {
            String body = mvc.perform(withToken(put("/api/events/genis/hold/seats/{id}", ids.get(i)), token))
                    .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
            if (token == null) {
                token = JsonPath.read(body, "$.token");
            }
        }
        mvc.perform(put("/api/events/genis/hold/seats/{id}", ids.get(6)).header(TOKEN, token))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MAX_SEATS_EXCEEDED"));
    }

    @Test
    void corsAllowsOnlyTheConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/events").header("Origin", "http://localhost:5173")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:5173"));

        mvc.perform(options("/api/events").header("Origin", "https://kotu.example")
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isForbidden());
    }

    private String hold(long seatId, String token) throws Exception {
        String body = mvc.perform(withToken(put("/api/events/{slug}/hold/seats/{id}", SLUG, seatId), token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.token");
    }

    private static org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder withToken(
            org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request, String token) {
        return token == null ? request : request.header(TOKEN, token);
    }
}
