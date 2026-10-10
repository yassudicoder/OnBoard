package com.safarsaathi.backend.watcher;

import static com.safarsaathi.backend.watcher.MockFlightStatusProviderTest.at;
import static com.safarsaathi.backend.watcher.MockFlightStatusProviderTest.flight;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.backend.config.AppProperties;
import com.safarsaathi.backend.config.ProviderStatus;
import com.safarsaathi.backend.trip.Block;

/**
 * Unit tests for the real flight-status provider, without calling the real service: we feed it sample answers in
 * AeroDataBox's format, and point it at a closed local port to check that a failure means "no change".
 */
class AeroDataBoxProviderTest {

    private static final ZoneId INDIA = ZoneId.of("Asia/Kolkata");

    /** One hour before the flight, so the provider is willing to ask. */
    private static final Clock BEFORE_THE_FLIGHT = Clock.fixed(at("2026-10-11T07:00:00+05:30"), INDIA);

    private final AeroDataBoxProvider provider = new AeroDataBoxProvider("test-key", AeroDataBoxProvider.BASE_URL,
            new ObjectMapper(), BEFORE_THE_FLIGHT, INDIA);

    /** Our flight: 08:00-09:45 in India = 02:30-04:15 UTC. */
    private final Block flight = flight(1, "2026-10-11T08:00:00+05:30", "2026-10-11T09:45:00+05:30");

    @Test
    void findsTheFlightNumberInTheTitle() {
        assertThat(AeroDataBoxProvider.flightNumber("Flight IJ 512 Mumbai to Bengaluru")).isEqualTo("IJ512");
        assertThat(AeroDataBoxProvider.flightNumber("Flight 6E2134 Pune to Delhi")).isEqualTo("6E2134");
        assertThat(AeroDataBoxProvider.flightNumber("Hotel check-in: Whitefield Residency")).isNull();
        assertThat(AeroDataBoxProvider.flightNumber(null)).isNull();
    }

    @Test
    void laterRevisedTimesBecomeADelay() throws Exception {
        String answer = """
                [{"number": "IJ 512", "status": "Delayed",
                  "departure": {"scheduledTime": {"utc": "2026-10-11 02:30Z", "local": "2026-10-11 08:00+05:30"},
                                "revisedTime":   {"utc": "2026-10-11 04:00Z", "local": "2026-10-11 09:30+05:30"}},
                  "arrival":   {"scheduledTime": {"utc": "2026-10-11 04:15Z", "local": "2026-10-11 09:45+05:30"},
                                "revisedTime":   {"utc": "2026-10-11 05:50Z", "local": "2026-10-11 11:20+05:30"}}}]
                """;

        FlightStatusChange change = provider.changeFrom(answer, flight, "IJ512").orElseThrow();

        assertThat(change.cancelled()).isFalse();
        assertThat(change.newStart()).isEqualTo(at("2026-10-11T09:30:00+05:30"));
        assertThat(change.newEnd()).isEqualTo(at("2026-10-11T11:20:00+05:30"));
        assertThat(change.detail()).isEqualTo("Delayed by 90 minutes (AeroDataBox status of IJ512: Delayed)");
    }

    @Test
    void withoutAnArrivalEstimateTheFlightKeepsItsLength() throws Exception {
        String answer = """
                [{"status": "Delayed", "departure": {"revisedTime": {"utc": "2026-10-11 03:00Z"}}, "arrival": {}}]
                """;

        FlightStatusChange change = provider.changeFrom(answer, flight, "IJ512").orElseThrow();

        assertThat(change.newStart()).isEqualTo(at("2026-10-11T08:30:00+05:30"));
        assertThat(change.newEnd()).isEqualTo(at("2026-10-11T10:15:00+05:30"));
    }

    @Test
    void canceledStatusBecomesACancellation() throws Exception {
        String answer = "[{\"status\": \"Canceled\", \"departure\": {}, \"arrival\": {}}]";

        assertThat(provider.changeFrom(answer, flight, "IJ512").orElseThrow().cancelled()).isTrue();
    }

    @Test
    void onTimeUnknownOrOddAnswersMeanNoChange() throws Exception {
        String onTime = "[{\"status\": \"Expected\", \"departure\": {\"revisedTime\": {\"utc\": \"2026-10-11 02:32Z\"}}}]";
        assertThat(provider.changeFrom(onTime, flight, "IJ512")).isEmpty(); // 2 minutes is noise, not a delay
        assertThat(provider.changeFrom("[{\"status\": \"Expected\"}]", flight, "IJ512")).isEmpty();
        assertThat(provider.changeFrom(null, flight, "IJ512")).isEmpty(); // 204 No Content
        assertThat(provider.changeFrom("[]", flight, "IJ512")).isEmpty();
        assertThat(provider.changeFrom(
                "[{\"departure\": {\"revisedTime\": {\"utc\": \"not a time\"}}}]", flight, "IJ512")).isEmpty();
    }

    @Test
    void anyNetworkProblemMeansNoChange() {
        // Port 1 on this machine: nothing listens there, so the connection is refused at once.
        AeroDataBoxProvider unreachable = new AeroDataBoxProvider("test-key", "http://127.0.0.1:1",
                new ObjectMapper(), BEFORE_THE_FLIGHT, INDIA);

        assertThat(unreachable.check(flight)).isEmpty();
    }

    @Test
    void isChosenOnlyWhenTheKeyIsSet() {
        MockFlightStatusProvider mock = new MockFlightStatusProvider();
        AppProperties withKey = settings("a-key");
        AppProperties withoutKey = settings("");

        assertThat(FlightWatcher.chooseProvider(new ProviderStatus(withKey), withKey, mock, new ObjectMapper(),
                BEFORE_THE_FLIGHT)).isInstanceOf(AeroDataBoxProvider.class);
        assertThat(FlightWatcher.chooseProvider(new ProviderStatus(withoutKey), withoutKey, mock, new ObjectMapper(),
                BEFORE_THE_FLIGHT)).isSameAs(mock);
    }

    /** Settings with only AERODATABOX_API_KEY filled in (every other key blank). */
    private static AppProperties settings(String aeroDataBoxKey) {
        return new AppProperties("demo", null, new AppProperties.Providers(
                new AppProperties.Gemini(null, null),
                new AppProperties.ApiKey(aeroDataBoxKey),
                new AppProperties.ApiKey(null),
                new AppProperties.CredentialsFile(null),
                new AppProperties.CredentialsFile(null),
                new AppProperties.ApiKey(null)),
                new AppProperties.Seed(true, 1));
    }
}
