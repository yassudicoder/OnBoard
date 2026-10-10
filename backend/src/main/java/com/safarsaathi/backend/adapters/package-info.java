/**
 * Booking adapters: one {@code BookingAdapter} (book, change, cancel, rules) per partner. MockAir, MockStay, MockCab
 * and MockTable are called over HTTP, so a real partner can later replace a mock by changing only its adapter;
 * {@code GoogleCalendarAdapter} uses the real calendar when GOOGLE_CALENDAR_CREDENTIALS_PATH is set, else a mock one.
 * Built in Phase 4.
 */
package com.safarsaathi.backend.adapters;
