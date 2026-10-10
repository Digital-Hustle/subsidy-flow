package ru.subsidy.package_.stream;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class StreamTicketsTest {

    @Test
    void ticketIsSingleUse() {
        StreamTickets tickets = new StreamTickets(Clock.systemUTC());
        String token = tickets.issue("user-1");
        assertThat(tickets.redeem(token)).contains("user-1");
        assertThat(tickets.redeem(token)).isEmpty();
        assertThat(tickets.redeem(null)).isEmpty();
    }

    @Test
    void expiredTicketRejected() {
        Instant start = Instant.parse("2026-10-09T10:00:00Z");
        MutableClock clock = new MutableClock(start);
        StreamTickets tickets = new StreamTickets(clock);
        String token = tickets.issue("user-1");
        clock.now = start.plus(StreamTickets.TTL).plus(Duration.ofSeconds(1));
        assertThat(tickets.redeem(token)).isEmpty();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
