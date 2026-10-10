package com.safarsaathi.app.ui.home;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.time.LocalTime;

/** Unit tests for the Home tab's greeting ({@link HomeGreeting}). */
public class HomeGreetingTest {

    @Test
    public void partOfTheDay() {
        assertEquals(HomeGreeting.EVENING, HomeGreeting.at(LocalTime.of(4, 59)));
        assertEquals(HomeGreeting.MORNING, HomeGreeting.at(LocalTime.of(5, 0)));
        assertEquals(HomeGreeting.MORNING, HomeGreeting.at(LocalTime.of(11, 59)));
        assertEquals(HomeGreeting.AFTERNOON, HomeGreeting.at(LocalTime.NOON));
        assertEquals(HomeGreeting.AFTERNOON, HomeGreeting.at(LocalTime.of(16, 59)));
        assertEquals(HomeGreeting.EVENING, HomeGreeting.at(LocalTime.of(17, 0)));
        assertEquals(HomeGreeting.EVENING, HomeGreeting.at(LocalTime.of(22, 30)));
    }

    @Test
    public void initialForTheAvatar() {
        assertEquals("A", HomeGreeting.initial("Aditi"));
        assertEquals("A", HomeGreeting.initial("  aditi "));
        assertEquals("", HomeGreeting.initial(""));
        assertEquals("", HomeGreeting.initial(null));
    }
}
