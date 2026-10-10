package com.safarsaathi.routing.route;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.safarsaathi.routing.map.AirportMap;

/** Step-by-step instructions for three sample routes on the BOM-T2 demo map, in English, Hindi and Marathi. */
class InstructionTextTest {

    static AirportMap map;
    static Router router;

    @BeforeAll
    static void loadMap() throws IOException {
        map = RouterTest.bomT2();
        router = new Router(map);
    }

    static List<String> texts(Route route, String language) {
        InstructionText words = InstructionText.forLanguage(language);
        List<String> texts = new ArrayList<>();
        for (RouteStep step : route.steps) {
            texts.add(words.text(step, map));
        }
        return texts;
    }

    @Test
    void entry4ToGate32_departures() {
        Route route = router.route("entry_4", "gate_32", WalkProfile.normal());

        assertThat(texts(route, "en")).containsExactly(
                "Head towards Check-in row D",
                "Turn left at Check-in row D",
                "Turn right at Departure board",
                "Go through Security check",
                "Turn left at Central hall",
                "Turn right at Pier C sign",
                "Arrive at Gate 32");
        // How far each step walks before the next one (metres).
        assertThat(route.steps.get(0).distanceM).isCloseTo(30.4, within(0.05));
        assertThat(route.steps.get(1).distanceM).isCloseTo(35 + 26.9, within(0.05));
        assertThat(route.steps.get(4).turnDegrees).isNegative(); // a left turn
        assertThat(route.steps.get(6).distanceM).isZero();
    }

    @Test
    void entry4ToGate32_inHindiAndMarathi() {
        Route route = router.route("entry_4", "gate_32", WalkProfile.normal());

        assertThat(texts(route, "hi")).containsExactly(
                "चेक-इन पंक्ति D की ओर चलें",
                "चेक-इन पंक्ति D पर बाएँ मुड़ें",
                "प्रस्थान सूचना बोर्ड पर दाएँ मुड़ें",
                "सुरक्षा जांच से होकर जाएँ",
                "मुख्य हॉल पर बाएँ मुड़ें",
                "पियर C का संकेत पर दाएँ मुड़ें",
                "गेट 32 पर पहुँचें");
        assertThat(texts(route, "mr")).containsExactly(
                "चेक-इन रांग D च्या दिशेने चला",
                "चेक-इन रांग D जवळ डावीकडे वळा",
                "प्रस्थान फलक जवळ उजवीकडे वळा",
                "सुरक्षा तपासणी मधून पुढे जा",
                "मुख्य दालन जवळ डावीकडे वळा",
                "पियर C फलक जवळ उजवीकडे वळा",
                "गेट 32 येथे पोहोचा");
    }

    @Test
    void gate32ToBelt2_stepFree_takesTheLiftDown() {
        Route route = router.route("gate_32", "belt_2", WalkProfile.normal().withStepFree(true));

        assertThat(texts(route, "en")).containsExactly(
                "Head towards Pier C sign",
                "Turn left at Pier C sign",
                "Take Lift A down to Arrivals",
                "Head towards Belt 2",
                "Arrive at Belt 2");
        assertThat(texts(route, "mr")).contains("लिफ्ट A ने खाली आगमन मजल्यावर जा");
    }

    @Test
    void belt4ToLostAndFound_arrivals() {
        Route route = router.route("belt_4", "lost_found", WalkProfile.normal());

        assertThat(texts(route, "en")).containsExactly(
                "Head towards Belt 3",
                "Keep left at Belt 3",
                "Turn left at Arrivals exit",
                "Turn left at Ground transportation lobby",
                "Arrive at Lost & Found");
    }

    @Test
    void arrival_andUnknownLanguage() {
        assertThat(InstructionText.forLanguage("en").arrived(map.gate("41"))).isEqualTo("You have arrived at Gate 41");
        assertThat(InstructionText.forLanguage("hi").arrived(map.gate("41"))).isEqualTo("आप गेट 41 पहुँच गए हैं");
        assertThat(InstructionText.forLanguage("ta").language()).isEqualTo("en"); // Tamil comes later: English
    }

    @Test
    void everyLanguage_hasEveryInstruction() {
        for (String language : new String[] {"en", "hi", "mr"}) {
            InstructionText words = InstructionText.forLanguage(language);
            assertThat(words.language()).isEqualTo(language);
            for (Maneuver maneuver : Maneuver.values()) {
                RouteStep step = new RouteStep(maneuver, map.node("lift_a_dep"), map.node("lift_a_dep"), "ARR", 0, 0, 0);
                assertThat(words.text(step, map)).as(language + " " + maneuver).isNotBlank().doesNotContain("{");
            }
        }
    }
}
