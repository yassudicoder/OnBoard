package com.safarsaathi.backend;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * Smoke test: the whole application starts (every bean can be created) with no API keys and no Docker.
 * no-keys.properties blanks every key, so a teammate's own .env cannot change the result, and
 * test-database.properties swaps PostgreSQL for H2, a database in memory (Flyway still creates the real tables).
 */
@SpringBootTest
@TestPropertySource(locations = {"classpath:no-keys.properties", "classpath:test-database.properties"})
class SafarSaathiApplicationTests {

    @Test
    void contextLoads() {
        // Nothing to check here: @SpringBootTest makes this test fail by itself if Spring cannot start the app.
    }
}
