package com.safarsaathi.recovery.policy;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.safarsaathi.recovery.plan.RecoverySettings;

/**
 * The real airline-policies.json and recovery-settings.json (copied onto the classpath by the backend's pom.xml)
 * follow every rule, and the checks catch a dishonest entry. Runs in the backend's build, which lends it Jackson.
 */
class PolicyAndSettingsFilesTest {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

    @Test
    void theRealPolicyFileHasNoProblems() throws IOException {
        AirlinePolicies policies = load("airline-policies.json", AirlinePolicies.class);

        assertThat(policies.problems()).isEmpty();
        assertThat(policies.airlines).extracting(airline -> airline.airlineCode)
                .containsExactly("IJ", "MJ", "DA", "DN");
        assertThat(policies.airlines).allSatisfy(airline -> {
            assertThat(airline.fictional).isTrue();
            assertThat(airline.kind).isEqualTo("SAMPLE");
        });
        assertThat(policies.find("IJ").delayAssistanceText).isNull(); // unknown on purpose
        assertThat(policies.find("MJ").delayAssistanceText).startsWith("SAMPLE POLICY");
        assertThat(policies.passengerRightsLinks).allSatisfy(link -> assertThat(link.url).startsWith("https://"));
    }

    @Test
    void theChecksCatchInventedContactsAndUnlabelledSampleText() throws IOException {
        AirlinePolicies policies = load("airline-policies.json", AirlinePolicies.class);
        AirlinePolicy indiJet = policies.find("IJ");
        indiJet.delayAssistanceText = "Free hotel for everyone"; // a fictional airline's text without "SAMPLE"
        ContactChannel phone = new ContactChannel();
        phone.type = "PHONE";
        phone.label = "Call us";
        phone.value = "+91 98765 43210"; // could be a real person's number
        indiJet.contactChannels.add(phone);

        assertThat(policies.problems()).containsExactlyInAnyOrder(
                "airline IJ: a SAMPLE policy text must start with \"SAMPLE\"",
                "airline IJ: a fictional airline gets no phone number (it could be someone's real number)",
                "airline IJ: a fictional airline's PHONE must use the reserved .example domain");
    }

    @Test
    void theRealSettingsFileHasNoProblems() throws IOException {
        RecoverySettings settings = load("recovery-settings.json", RecoverySettings.class);

        assertThat(settings.problems()).isEmpty();
        assertThat(settings.normal.stopPenaltyMinutes).isEqualTo(180);
        assertThat(settings.urgent.stopPenaltyMinutes).isEqualTo(30);
        assertThat(settings.overnightThresholdHours).isEqualTo(6);
        assertThat(settings.otherOptionsShown).isEqualTo(2);
    }

    private static <T> T load(String file, Class<T> type) throws IOException {
        try (InputStream in = PolicyAndSettingsFilesTest.class.getClassLoader()
                .getResourceAsStream("mock-data/recovery/" + file)) {
            assertThat(in).as(file + " on the classpath").isNotNull();
            return JSON.readValue(in, type);
        }
    }
}
